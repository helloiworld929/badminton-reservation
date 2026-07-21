package com.badminton.controller.admin;

import com.badminton.common.ApiResponse;
import com.badminton.dto.request.ForumPinRequest;
import com.badminton.dto.request.ForumReportHandleRequest;
import com.badminton.dto.request.ForumStatusRequest;
import com.badminton.dto.response.ForumPostDetailVO;
import com.badminton.dto.response.PageResult;
import com.badminton.entity.ForumPost;
import com.badminton.entity.ForumReport;
import com.badminton.service.ForumService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpSession;
import javax.validation.Valid;

@RestController
@RequestMapping("/admin/forum")
public class AdminForumController {
    private final ForumService forumService;

    public AdminForumController(ForumService forumService) {
        this.forumService = forumService;
    }

    @GetMapping("/posts")
    public ApiResponse<PageResult<ForumPost>> posts(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize) {
        return ApiResponse.success(forumService.adminListPosts(category, keyword, status, pageNum, pageSize));
    }

    @GetMapping("/posts/{id}")
    public ApiResponse<ForumPostDetailVO> detail(@PathVariable long id,
                                                  @RequestParam(defaultValue = "1") int replyPage,
                                                  @RequestParam(defaultValue = "20") int replyPageSize) {
        return ApiResponse.success(forumService.getPostDetail(id, replyPage, replyPageSize, true, false));
    }

    @PutMapping("/posts/{id}/pin")
    public ApiResponse<Boolean> pin(@PathVariable long id, @RequestBody ForumPinRequest request,
                                    HttpSession session) {
        forumService.setPinned(userId(session), id, request.isPinned());
        return ApiResponse.success(true);
    }

    @PutMapping("/posts/{id}/status")
    public ApiResponse<Boolean> status(@PathVariable long id, @Valid @RequestBody ForumStatusRequest request,
                                       HttpSession session) {
        forumService.updatePostStatus(userId(session), id, request.getStatus());
        return ApiResponse.success(true);
    }

    @PutMapping("/replies/{id}/status")
    public ApiResponse<Boolean> replyStatus(@PathVariable long id,
                                            @Valid @RequestBody ForumStatusRequest request,
                                            HttpSession session) {
        forumService.updateReplyStatus(userId(session), id, request.getStatus());
        return ApiResponse.success(true);
    }

    @GetMapping("/reports")
    public ApiResponse<PageResult<ForumReport>> reports(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize) {
        return ApiResponse.success(forumService.adminListReports(status, pageNum, pageSize));
    }

    @PutMapping("/reports/{id}")
    public ApiResponse<Boolean> handleReport(@PathVariable long id,
                                             @Valid @RequestBody ForumReportHandleRequest request,
                                             HttpSession session) {
        forumService.handleReport(userId(session), id, request);
        return ApiResponse.success(true);
    }

    private long userId(HttpSession session) {
        Object value = session.getAttribute("userId");
        if (value instanceof Number) return ((Number) value).longValue();
        throw new IllegalArgumentException("请先登录");
    }
}
