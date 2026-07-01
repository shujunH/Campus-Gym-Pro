package com.campusgym.pro.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.campusgym.pro.entity.Slot;
import com.campusgym.pro.entity.Stadium;
import com.campusgym.pro.mapper.SlotMapper;
import com.campusgym.pro.mapper.StadiumMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class SlotService extends ServiceImpl<SlotMapper, Slot> {

    private static final String SLOT_STOCK_PREFIX = "slot:stock:";

    /**
     * 每日场次时间段（与 init.sql 保持一致）
     */
    private static final LocalTime[][] TIME_SLOTS = {
            {LocalTime.of(8, 0), LocalTime.of(9, 0)},
            {LocalTime.of(9, 0), LocalTime.of(10, 0)},
            {LocalTime.of(10, 0), LocalTime.of(11, 0)},
            {LocalTime.of(11, 0), LocalTime.of(12, 0)},
            {LocalTime.of(14, 0), LocalTime.of(15, 0)},
            {LocalTime.of(15, 0), LocalTime.of(16, 0)},
            {LocalTime.of(16, 0), LocalTime.of(17, 0)},
            {LocalTime.of(17, 0), LocalTime.of(18, 0)},
            {LocalTime.of(18, 0), LocalTime.of(19, 0)},
            {LocalTime.of(19, 0), LocalTime.of(20, 0)},
            {LocalTime.of(20, 0), LocalTime.of(21, 0)},
    };

    /** 提前生成未来 N 天的场次 */
    private static final int LOOK_AHEAD_DAYS = 7;

    /** 每个场次默认总库存 */
    private static final int DEFAULT_STOCK = 20;

    private final RedisTemplate<String, Object> redisTemplate;
    private final SlotMapper slotMapper;
    private final StadiumMapper stadiumMapper;

    /**
     * 每天 00:01 — 自动生成未来 N 天的场次数据（补齐缺失日期）
     * 此任务保证数据库中长期存在可预约的场次，解决运行几天后无场次可读的问题
     */
    @Scheduled(cron = "0 1 0 * * ?")
    public void generateDailySlots() {
        int totalCreated = 0;
        List<Stadium> stadiums = stadiumMapper.selectList(
                new LambdaQueryWrapper<Stadium>().eq(Stadium::getStatus, 1)
        );

        for (int i = 0; i <= LOOK_AHEAD_DAYS; i++) {
            LocalDate date = LocalDate.now().plusDays(i);
            Long count = slotMapper.selectCount(
                    new LambdaQueryWrapper<Slot>().eq(Slot::getDate, date)
            );
            if (count > 0) {
                continue; // 该日期已有场次，跳过
            }

            List<Slot> newSlots = new ArrayList<>();
            for (Stadium stadium : stadiums) {
                for (LocalTime[] timeRange : TIME_SLOTS) {
                    Slot slot = new Slot();
                    slot.setStadiumId(stadium.getId());
                    slot.setDate(date);
                    slot.setStartTime(timeRange[0]);
                    slot.setEndTime(timeRange[1]);
                    slot.setTotalStock(DEFAULT_STOCK);
                    slot.setRemainingStock(DEFAULT_STOCK);
                    slot.setStatus(1);
                    newSlots.add(slot);
                }
            }
            saveBatch(newSlots);
            totalCreated += newSlots.size();
            log.info("已生成 {} 个场次，日期: {}", newSlots.size(), date);
        }

        if (totalCreated > 0) {
            log.info("场次自动生成完成，本次共生成 {} 个场次", totalCreated);
        }
    }

    /**
     * 每天 00:05 — 将所有未来场次的库存预热到 Redis
     */
    @Scheduled(cron = "0 5 0 * * ?")
    public void cacheAllFutureStock() {
        for (int i = 0; i <= LOOK_AHEAD_DAYS; i++) {
            LocalDate date = LocalDate.now().plusDays(i);
            cacheStockByDate(date);
        }
        log.info("所有未来场次库存预热完成");
    }

    /**
     * 每天 00:10 — 清理已过期场次（将昨天及之前的场次状态置为不可预约）
     */
    @Scheduled(cron = "0 10 0 * * ?")
    public void disablePastSlots() {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        int affected = slotMapper.disableByDate(yesterday);
        log.info("已关闭 {} 个过期场次（日期 <= {}）", affected, yesterday);
    }

    public void cacheStockByDate(LocalDate date) {
        List<Slot> slots = slotMapper.selectList(
                new LambdaQueryWrapper<Slot>()
                        .eq(Slot::getDate, date)
                        .eq(Slot::getStatus, 1)
        );
        for (Slot slot : slots) {
            String key = SLOT_STOCK_PREFIX + slot.getId();
            redisTemplate.opsForValue().set(key, slot.getRemainingStock(), 2, TimeUnit.DAYS);
        }
        log.info("已缓存 {} 个场次库存到 Redis，日期: {}", slots.size(), date);
    }

    public void warmUpStock(Long slotId) {
        Slot slot = slotMapper.selectById(slotId);
        if (slot != null && slot.getStatus() == 1) {
            String key = SLOT_STOCK_PREFIX + slotId;
            redisTemplate.opsForValue().set(key, slot.getRemainingStock(), 2, TimeUnit.DAYS);
        }
    }

    public String getStockKey(Long slotId) {
        return SLOT_STOCK_PREFIX + slotId;
    }
}