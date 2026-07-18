package com.badminton.mapper;

import com.badminton.entity.OperationLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface OperationLogMapper {

    int insert(OperationLog log);

    List<OperationLog> findByReservationId(@Param("reservationId") long reservationId);
}
