package com.campusgym.pro.dto;

import lombok.Data;

@Data
public class ReservationRequest {
    private Long userId;
    private Long slotId;
}