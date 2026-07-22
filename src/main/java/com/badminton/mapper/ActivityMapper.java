package com.badminton.mapper;

import com.badminton.entity.Activity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ActivityMapper {

    Activity selectById(@Param("id") Long id);

    Activity selectByIdIncludingDeleted(@Param("id") Long id);

    List<Activity> selectAll();

    int insert(Activity activity);

    int updateById(Activity activity);

    int softDeleteById(@Param("id") Long id);
}
