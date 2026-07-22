package com.badminton.mapper;

import com.badminton.entity.Reservation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface ReservationMapper {

    Reservation selectById(@Param("id") Long id);

    List<Reservation> selectAll();

    int insert(Reservation reservation);

    int transitionStatus(@Param("id") Long id,
                         @Param("fromStatus") String fromStatus,
                         @Param("toStatus") String toStatus,
                         @Param("verifiedAt") LocalDateTime verifiedAt,
                         @Param("verifiedBy") Long verifiedBy);

    List<Reservation> findBySlot(@Param("date") LocalDate date, @Param("startTime") int startTime);

    int countActiveByCourtSlot(@Param("courtId") long courtId,
                               @Param("date") LocalDate date,
                               @Param("startTime") int startTime);

    List<Reservation> findByUser(@Param("userId") long userId);

    Reservation findDetailById(@Param("id") long id);

    List<Reservation> findAdminList(@Param("status") String status,
                                    @Param("date") String date,
                                    @Param("courtId") Long courtId);

    Reservation findByVerificationCode(@Param("code") String code);

    /** 查询超过指定时间仍未核销的预约，用于定时任务标记爽约 */
    List<Reservation> findNoshowCandidates(@Param("cutoff") String cutoff);
}
