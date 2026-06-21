package com.campusgym.pro.service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.campusgym.pro.dto.ReservationResult;
import com.campusgym.pro.entity.Reservation;
import com.campusgym.pro.entity.Slot;
import com.campusgym.pro.mapper.ReservationMapper;
import com.campusgym.pro.mapper.SlotMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReservationService extends ServiceImpl<ReservationMapper, Reservation> {

    private final RedissonClient redissonClient;
    private final RedisTemplate<String, Object> redisTemplate;
    private final DefaultRedisScript<Long> deductStockScript;
    private final SlotMapper slotMapper;
    private final ReservationMapper reservationMapper;
    private final RabbitTemplate rabbitTemplate;
    private final SlotService slotService;

    @Value("${gym.reservation.lock.wait-time}")
    private int lockWaitTime;

    @Value("${gym.reservation.lock.lease-time}")
    private int lockLeaseTime;

    private static final String EXCHANGE = "gym.reservation.exchange";
    private static final String ROUTING_KEY = "gym.reservation.order";

    public ReservationResult reserve(Long userId, Long slotId) {
        String lockKey = "lock:reservation:" + userId + ":" + slotId;
        RLock lock = redissonClient.getLock(lockKey);

        try {
            boolean acquired = lock.tryLock(lockWaitTime, lockLeaseTime, TimeUnit.SECONDS);
            if (!acquired) {
                log.warn("获取锁失败，用户重复请求 userId={}, slotId={}", userId, slotId);
                return ReservationResult.fail("请求过于频繁，请稍后重试");
            }

            int count = reservationMapper.countBySlotIdAndUserId(slotId, userId);
            if (count > 0) {
                log.warn("幂等检查失败：用户已预约该场次 userId={}, slotId={}", userId, slotId);
                return ReservationResult.fail("您已预约过该场次，请勿重复预约");
            }

            Long result = redisTemplate.execute(
                    deductStockScript,
                    Collections.singletonList(slotService.getStockKey(slotId))
            );

            if (result == null || result == -1) {
                slotService.warmUpStock(slotId);
                result = redisTemplate.execute(
                        deductStockScript,
                        Collections.singletonList(slotService.getStockKey(slotId))
                );
            }

            if (result == null || result == -1) {
                log.warn("Redis库存Key不存在或预热失败 slotId={}", slotId);
                return ReservationResult.fail("场次信息异常，请刷新后重试");
            }

            if (result == 0) {
                log.info("库存不足，扣减失败 userId={}, slotId={}", userId, slotId);
                return ReservationResult.fail("该场次已约满，请选择其他场次");
            }

            Reservation reservation = new Reservation();
            reservation.setUserId(userId);
            reservation.setSlotId(slotId);
            reservation.setStatus("PENDING");
            reservation.setDeleted(0);
            reservation.setCreatedAt(LocalDateTime.now());
            reservation.setUpdatedAt(LocalDateTime.now());

            try {
                rabbitTemplate.convertAndSend(EXCHANGE, ROUTING_KEY, reservation);
                log.info("预约请求已发送至MQ userId={}, slotId={}", userId, slotId);
                return ReservationResult.queued(slotId, userId);
            } catch (Exception e) {
                log.warn("RabbitMQ不可用，降级为同步处理 userId={}, slotId={}", userId, slotId);
                processReservation(reservation);
                return ReservationResult.ok(reservation.getId(), slotId, userId);
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("获取分布式锁被中断", e);
            return ReservationResult.fail("系统繁忙，请稍后重试");
        } catch (Exception e) {
            log.error("预约处理异常", e);
            return ReservationResult.fail("系统繁忙，请稍后重试");
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void processReservation(Reservation reservation) {
        try {
            String lockKey = "lock:db:" + reservation.getUserId() + ":" + reservation.getSlotId();
            RLock lock = redissonClient.getLock(lockKey);

            try {
                boolean acquired = lock.tryLock(3, 5, TimeUnit.SECONDS);
                if (!acquired) {
                    log.warn("数据库入库获取锁失败，跳过 userId={}", reservation.getUserId());
                    return;
                }

                int count = reservationMapper.countBySlotIdAndUserId(
                        reservation.getSlotId(), reservation.getUserId());
                if (count > 0) {
                    log.warn("Consumer幂等检查：用户已预约 userId={}", reservation.getUserId());
                    return;
                }

                int rows = slotMapper.deductStock(reservation.getSlotId());
                if (rows <= 0) {
                    log.error("MySQL库存扣减失败，数据不一致 slotId={}", reservation.getSlotId());
                    return;
                }

                reservation.setStatus("CONFIRMED");
                reservation.setCreatedAt(LocalDateTime.now());
                reservation.setUpdatedAt(LocalDateTime.now());
                reservationMapper.insert(reservation);

                log.info("订单入库成功 reservationId={}, userId={}, slotId={}",
                        reservation.getId(), reservation.getUserId(), reservation.getSlotId());

            } finally {
                if (lock.isHeldByCurrentThread()) {
                    lock.unlock();
                }
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("处理预约订单时获取锁被中断", e);
            throw new RuntimeException("系统繁忙，请稍后重试", e);
        } catch (Exception e) {
            log.error("处理预约订单失败", e);
            throw new RuntimeException("订单处理失败", e);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public ReservationResult cancelReservation(Long reservationId, Long userId) {
        Reservation reservation = reservationMapper.selectById(reservationId);
        if (reservation == null) {
            return ReservationResult.fail("订单不存在");
        }
        if (!reservation.getUserId().equals(userId)) {
            return ReservationResult.fail("无权操作该订单");
        }
        if ("CANCELLED".equals(reservation.getStatus())) {
            return ReservationResult.fail("订单已取消");
        }

        reservation.setStatus("CANCELLED");
        reservationMapper.updateById(reservation);

        Slot slot = slotMapper.selectById(reservation.getSlotId());
        if (slot != null) {
            slot.setRemainingStock(slot.getRemainingStock() + 1);
            slotMapper.updateById(slot);

            String stockKey = slotService.getStockKey(slot.getId());
            redisTemplate.opsForValue().increment(stockKey, 1);
        }

        log.info("订单取消成功 reservationId={}, userId={}", reservationId, userId);
        return ReservationResult.ok(reservationId, reservation.getSlotId(), userId);
    }
}