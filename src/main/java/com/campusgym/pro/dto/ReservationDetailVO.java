package com.campusgym.pro.dto;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Data
public class ReservationDetailVO {
    private Long id;
    private Long userId;
    private Long slotId;
    private String status;
    private LocalDateTime createdAt;
    private String stadiumName;
    private String stadiumType;
    private LocalDate date;
    private LocalTime startTime;
    private LocalTime endTime;
}