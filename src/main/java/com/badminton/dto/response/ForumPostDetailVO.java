package com.badminton.dto.response;

import com.badminton.entity.ForumPost;
import com.badminton.entity.ForumReply;
import lombok.Data;

@Data
public class ForumPostDetailVO {
    private ForumPost post;
    private PageResult<ForumReply> replies;
}
