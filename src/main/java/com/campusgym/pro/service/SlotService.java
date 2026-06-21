package com.campusgym.pro.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.campusgym.pro.entity.Slot;
import com.campusgym.pro.mapper.SlotMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class SlotService extends ServiceImpl<SlotMapper, Slot> {

    private static final String SLOT_STOCK_PREFIX = "slot:stock:";

    private final RedisTemplate<String, Object> redisTemplate;
    private final SlotMapper slotMapper;

    @Scheduled(cron = "0 0 0 * * ?")
    public void cacheTomorrowStock() {
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        cacheStockByDate(tomorrow);
        log.info("库存预热完成，日期: {}", tomorrow);
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