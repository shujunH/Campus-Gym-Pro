package com.campusgym.pro.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReservationResult {
    private boolean success;
    private String message;
    private Long reservationId;
    private Long slotId;
    private Long userId;

    public static ReservationResult ok(Long reservationId, Long slotId, Long userId) {
        ReservationResult result = new ReservationResult();
        result.setSuccess(true);
        result.setMessage("预约成功");
        result.setReservationId(reservationId);
        result.setSlotId(slotId);
        result.setUserId(userId);
        return result;
    }

    public static ReservationResult queued(Long slotId, Long userId) {
        ReservationResult result = new ReservationResult();
        result.setSuccess(true);
        result.setMessage("预约请求已接收，排队处理中");
        result.setSlotId(slotId);
        result.setUserId(userId);
        return result;
    }

    public static ReservationResult fail(String message) {
        ReservationResult result = new ReservationResult();
        result.setSuccess(false);
        result.setMessage(message);
        return result;
    }
}