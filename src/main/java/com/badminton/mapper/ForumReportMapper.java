package com.badminton.mapper;

import com.badminton.entity.ForumReport;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ForumReportMapper {
    int insert(ForumReport report);
    ForumReport selectById(@Param("id") Long id);
    int countPending(@Param("reporterId") Long reporterId,
                     @Param("targetType") String targetType,
                     @Param("targetId") Long targetId);
    List<ForumReport> findAll(@Param("status") String status);
    int handle(@Param("id") Long id, @Param("status") String status,
               @Param("result") String result, @Param("operatorId") Long operatorId);
}
