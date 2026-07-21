package com.badminton.controller;

import com.badminton.common.ApiResponse;
import com.badminton.dto.request.ForumPostCreateRequest;
import com.badminton.dto.request.ForumReplyRequest;
import com.badminton.dto.request.ForumReportRequest;
import com.badminton.dto.response.ForumPostDetailVO;
import com.badminton.dto.response.PageResult;
import com.badminton.entity.ForumPost;
import com.badminton.entity.ForumReply;
import com.badminton.entity.ForumReport;
import com.badminton.service.ForumService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpSession;
import javax.validation.Valid;

@RestController
@RequestMapping("/forum")
public class ForumController {
    private final ForumService forumService;

    public ForumController(ForumService forumService) {
        this.forumService = forumService;
    }

    @GetMapping("/posts")
    public ApiResponse<PageResult<ForumPost>> list(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize) {
        return ApiResponse.success(forumService.listPosts(category, keyword, pageNum, pageSize));
    }

    @GetMapping("/posts/{id}")
    public ApiResponse<ForumPostDetailVO> detail(@PathVariable long id,
                                                  @RequestParam(defaultValue = "1") int replyPage,
                                                  @RequestParam(defaultValue = "20") int replyPageSize) {
        return ApiResponse.success(forumService.getPostDetail(id, replyPage, replyPageSize, false, true));
    }

    @PostMapping(value = "/posts", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<ForumPost> create(@Valid @ModelAttribute ForumPostCreateRequest request,
                                         HttpSession session) {
        return ApiResponse.success(forumService.createPost(userId(session), request));
    }

    @DeleteMapping("/posts/{id}")
    public ApiResponse<Boolean> delete(@PathVariable long id, HttpSession session) {
        forumService.deleteOwnPost(userId(session), id);
        return ApiResponse.success(true);
    }

    @PostMapping("/posts/{id}/replies")
    public ApiResponse<ForumReply> reply(@PathVariable long id,
                                         @Valid @RequestBody ForumReplyRequest request,
                                         HttpSession session) {
        return ApiResponse.success(forumService.addReply(userId(session), id, request.getContent()));
    }

    @DeleteMapping("/replies/{id}")
    public ApiResponse<Boolean> deleteReply(@PathVariable long id, HttpSession session) {
        forumService.deleteOwnReply(userId(session), id);
        return ApiResponse.success(true);
    }

    @PostMapping("/reports")
    public ApiResponse<ForumReport> report(@Valid @RequestBody ForumReportRequest request,
                                           HttpSession session) {
        return ApiResponse.success(forumService.report(userId(session), request));
    }

    private long userId(HttpSession session) {
        Object value = session.getAttribute("userId");
        if (value instanceof Number) return ((Number) value).longValue();
        throw new IllegalArgumentException("请先登录");
    }
}
