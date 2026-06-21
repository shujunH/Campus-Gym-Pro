package com.campusgym.pro.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campusgym.pro.dto.ReservationDetailVO;
import com.campusgym.pro.dto.ReservationRequest;
import com.campusgym.pro.dto.ReservationResult;
import com.campusgym.pro.entity.Reservation;
import com.campusgym.pro.entity.Slot;
import com.campusgym.pro.entity.Stadium;
import com.campusgym.pro.mapper.ReservationMapper;
import com.campusgym.pro.mapper.SlotMapper;
import com.campusgym.pro.mapper.StadiumMapper;
import com.campusgym.pro.service.ReservationService;
import com.campusgym.pro.service.SlotService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ReservationController {

    private final ReservationService reservationService;
    private final SlotService slotService;
    private final StadiumMapper stadiumMapper;
    private final SlotMapper slotMapper;
    private final ReservationMapper reservationMapper;

    @PostMapping("/reservation")
    public ReservationResult reserve(@RequestBody ReservationRequest request) {
        return reservationService.reserve(request.getUserId(), request.getSlotId());
    }

    @PostMapping("/reservation/cancel/{reservationId}")
    public ReservationResult cancel(@PathVariable Long reservationId, @RequestParam Long userId) {
        return reservationService.cancelReservation(reservationId, userId);
    }

    @GetMapping("/stadiums")
    public List<Stadium> listStadiums() {
        return stadiumMapper.selectList(
                new LambdaQueryWrapper<Stadium>()
                        .eq(Stadium::getStatus, 1)
        );
    }

    @GetMapping("/slots")
    public List<Slot> listSlots(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) String type) {
        LambdaQueryWrapper<Slot> wrapper = new LambdaQueryWrapper<Slot>()
                .eq(Slot::getStatus, 1);
        if (date != null) {
            wrapper.eq(Slot::getDate, date);
        }
        if (type != null && !type.isEmpty()) {
            List<Stadium> stadiums = stadiumMapper.selectList(
                    new LambdaQueryWrapper<Stadium>().eq(Stadium::getType, type));
            List<Long> ids = stadiums.stream().map(Stadium::getId).toList();
            if (ids.isEmpty()) {
                return List.of();
            }
            wrapper.in(Slot::getStadiumId, ids);
        }
        return slotMapper.selectList(wrapper);
    }

    @GetMapping("/slots/{slotId}")
    public Slot getSlot(@PathVariable Long slotId) {
        return slotMapper.selectById(slotId);
    }

    @GetMapping("/reservations")
    public List<ReservationDetailVO> listReservations(@RequestParam Long userId) {
        return reservationMapper.findDetailByUserId(userId);
    }

    @GetMapping("/reservation-users")
    public List<Long> listReservationUsers() {
        return reservationMapper.findDistinctUserIds();
    }

    @PostMapping("/slots/warmup")
    public String warmUpStock(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        slotService.cacheStockByDate(date);
        return "库存预热完成，日期：" + date;
    }
}