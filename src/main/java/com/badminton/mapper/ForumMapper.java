package com.badminton.mapper;

import com.badminton.entity.ForumPost;
import com.badminton.entity.ForumPostImage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ForumMapper {
    int insertPost(ForumPost post);
    int insertImage(ForumPostImage image);
    ForumPost selectPostById(@Param("id") Long id);
    List<ForumPost> findPosts(@Param("category") String category,
                              @Param("keyword") String keyword,
                              @Param("status") String status);
    List<ForumPostImage> findImages(@Param("postId") Long postId,
                                    @Param("status") String status);
    int incrementViewCount(@Param("id") Long id);
    int softDeleteByAuthor(@Param("id") Long id, @Param("userId") Long userId);
    int updatePostStatus(@Param("id") Long id, @Param("status") String status,
                         @Param("operatorId") Long operatorId);
    int updatePinned(@Param("id") Long id, @Param("pinned") boolean pinned,
                     @Param("operatorId") Long operatorId);
    int updateImageStatusByPost(@Param("postId") Long postId, @Param("status") String status);
}
