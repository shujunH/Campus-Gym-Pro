package com.campusgym.pro.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Data
@TableName("slot")
public class Slot {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long stadiumId;

    private LocalDate date;

    private LocalTime startTime;

    private LocalTime endTime;

    private Integer totalStock;

    private Integer remainingStock;

    private Integer status;

    @TableLogic
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}