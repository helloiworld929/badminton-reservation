package com.badminton.mapper;

import com.badminton.entity.Court;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface CourtMapper {

    Court selectById(@Param("id") Long id);

    List<Court> selectAll();

    int insert(Court court);

    int updateById(Court court);

    int deleteById(@Param("id") Long id);
}
