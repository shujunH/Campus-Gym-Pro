package com.campusgym.pro.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campusgym.pro.dto.ReservationDetailVO;
import com.campusgym.pro.entity.Reservation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ReservationMapper extends BaseMapper<Reservation> {

    @Select("SELECT COUNT(*) FROM reservation WHERE slot_id = #{slotId} AND user_id = #{userId} AND status != 'CANCELLED' AND deleted = 0")
    int countBySlotIdAndUserId(@Param("slotId") Long slotId, @Param("userId") Long userId);

    @Select("SELECT DISTINCT user_id FROM reservation WHERE deleted = 0 AND status != 'CANCELLED' ORDER BY user_id")
    List<Long> findDistinctUserIds();

    @Select("SELECT r.id, r.user_id, r.slot_id, r.status, r.created_at, " +
            "st.name AS stadium_name, st.type AS stadium_type, " +
            "sl.date, sl.start_time, sl.end_time " +
            "FROM reservation r " +
            "LEFT JOIN slot sl ON r.slot_id = sl.id " +
            "LEFT JOIN stadium st ON sl.stadium_id = st.id " +
            "WHERE r.user_id = #{userId} AND r.deleted = 0 " +
            "ORDER BY r.created_at DESC")
    List<ReservationDetailVO> findDetailByUserId(@Param("userId") Long userId);
}