package com.campusgym.pro.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campusgym.pro.entity.Slot;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface SlotMapper extends BaseMapper<Slot> {

    @Update("UPDATE slot SET remaining_stock = remaining_stock - 1 WHERE id = #{slotId} AND remaining_stock > 0")
    int deductStock(@Param("slotId") Long slotId);
}