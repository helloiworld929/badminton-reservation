package com.badminton.mapper;

import com.badminton.entity.ActivitySignup;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ActivitySignupMapper {

    int insert(ActivitySignup signup);

    List<ActivitySignup> selectAll();

    List<ActivitySignup> findByActivityId(@Param("activityId") Long activityId);

    List<ActivitySignup> findByActivityIdAndUserId(@Param("activityId") Long activityId,
                                                    @Param("userId") Long userId);

    int deleteById(@Param("id") Long id);
}
