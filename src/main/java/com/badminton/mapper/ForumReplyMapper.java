package com.badminton.mapper;

import com.badminton.entity.ForumReply;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ForumReplyMapper {
    int insert(ForumReply reply);
    ForumReply selectById(@Param("id") Long id);
    List<ForumReply> findByPost(@Param("postId") Long postId,
                                @Param("status") String status);
    int softDeleteByAuthor(@Param("id") Long id, @Param("userId") Long userId);
    int updateStatus(@Param("id") Long id, @Param("status") String status,
                     @Param("operatorId") Long operatorId);
}
