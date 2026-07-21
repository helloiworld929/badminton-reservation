package com.badminton.service;

import com.badminton.common.BusinessException;
import com.badminton.dto.request.ForumPostCreateRequest;
import com.badminton.dto.request.ForumReportHandleRequest;
import com.badminton.dto.request.ForumReportRequest;
import com.badminton.dto.response.ForumPostDetailVO;
import com.badminton.dto.response.PageResult;
import com.badminton.entity.ForumPost;
import com.badminton.entity.ForumPostImage;
import com.badminton.entity.ForumReply;
import com.badminton.entity.ForumReport;
import com.badminton.mapper.ForumMapper;
import com.badminton.mapper.ForumReplyMapper;
import com.badminton.mapper.ForumReportMapper;
import com.github.pagehelper.PageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** 校内论坛的发帖、回复、举报和管理规则集中入口。 */
@Service
public class ForumService {
    public static final List<String> CATEGORIES = Arrays.asList(
            "约球组队", "技术交流", "赛事活动", "场地反馈", "失物招领", "其他");
    public static final List<String> REPORT_REASONS = Arrays.asList(
            "广告", "辱骂", "人身攻击", "违规图片", "其他");

    private static final Set<String> CONTENT_STATUSES = new HashSet<>(
            Arrays.asList("normal", "hidden", "deleted"));
    private static final Set<String> REPORT_STATUSES = new HashSet<>(
            Arrays.asList("resolved", "rejected"));

    private final ForumMapper forumMapper;
    private final ForumReplyMapper replyMapper;
    private final ForumReportMapper reportMapper;
    private final OssService ossService;
    private final Clock clock;

    public ForumService(ForumMapper forumMapper, ForumReplyMapper replyMapper,
                        ForumReportMapper reportMapper, OssService ossService, Clock clock) {
        this.forumMapper = forumMapper;
        this.replyMapper = replyMapper;
        this.reportMapper = reportMapper;
        this.ossService = ossService;
        this.clock = clock;
    }

    public PageResult<ForumPost> listPosts(String category, String keyword, int pageNum, int pageSize) {
        validateCategoryFilter(category);
        PageHelper.startPage(pageNum, pageSize);
        List<ForumPost> posts = forumMapper.findPosts(emptyToNull(category), emptyToNull(keyword), "normal");
        fillImages(posts, "normal");
        return PageResult.of(posts);
    }

    public PageResult<ForumPost> adminListPosts(String category, String keyword, String status,
                                                int pageNum, int pageSize) {
        validateCategoryFilter(category);
        if (status != null && !status.isBlank() && !CONTENT_STATUSES.contains(status)) {
            throw new BusinessException("帖子状态不合法");
        }
        PageHelper.startPage(pageNum, pageSize);
        List<ForumPost> posts = forumMapper.findPosts(emptyToNull(category), emptyToNull(keyword), emptyToNull(status));
        // 管理端需要查看被隐藏或删除帖子的原图片，以便审核和恢复。
        fillImages(posts, null);
        return PageResult.of(posts);
    }

    @Transactional
    public ForumPost createPost(long userId, ForumPostCreateRequest request) {
        validateCategory(request.getCategory());
        List<MultipartFile> images = request.getImages() == null ? new ArrayList<>()
                : request.getImages().stream().filter(file -> file != null && !file.isEmpty())
                .collect(Collectors.toList());
        if (images.size() > 3) throw new BusinessException("每个帖子最多上传3张图片");

        List<String> uploadedUrls = new ArrayList<>();
        try {
            for (MultipartFile image : images) {
                uploadedUrls.add(ossService.upload(image, "forum"));
            }

            LocalDateTime now = LocalDateTime.now(clock);
            ForumPost post = new ForumPost();
            post.setUserId(userId);
            post.setTitle(request.getTitle().trim());
            post.setContent(request.getContent().trim());
            post.setCategory(request.getCategory());
            post.setStatus("normal");
            post.setPinned(false);
            post.setViewCount(0);
            post.setCreatedAt(now);
            post.setUpdatedAt(now);
            forumMapper.insertPost(post);

            for (int i = 0; i < uploadedUrls.size(); i++) {
                ForumPostImage image = new ForumPostImage();
                image.setPostId(post.getId());
                image.setImageUrl(uploadedUrls.get(i));
                image.setSortOrder(i);
                image.setStatus("normal");
                image.setCreatedAt(now);
                forumMapper.insertImage(image);
            }
            post.setImageUrls(uploadedUrls);
            return post;
        } catch (IOException e) {
            cleanupUploads(uploadedUrls);
            throw new BusinessException("论坛图片上传失败");
        } catch (RuntimeException e) {
            cleanupUploads(uploadedUrls);
            throw e;
        }
    }

    public ForumPostDetailVO getPostDetail(long postId, int replyPage, int replyPageSize,
                                            boolean admin, boolean incrementView) {
        ForumPost post = forumMapper.selectPostById(postId);
        if (post == null || (!admin && !"normal".equals(post.getStatus()))) {
            throw new BusinessException("帖子不存在或已不可见");
        }
        if (!admin && incrementView) {
            forumMapper.incrementViewCount(postId);
            post.setViewCount((post.getViewCount() == null ? 0 : post.getViewCount()) + 1);
        }
        post.setImageUrls(forumMapper.findImages(postId, admin ? null : "normal").stream()
                .map(ForumPostImage::getImageUrl).collect(Collectors.toList()));

        PageHelper.startPage(replyPage, replyPageSize);
        // 用户端保留隐藏、删除回复的楼层位置，正文在下方替换为状态提示。
        List<ForumReply> replies = replyMapper.findByPost(postId, null);
        if (!admin) {
            for (ForumReply reply : replies) {
                if ("deleted".equals(reply.getStatus())) reply.setContent("内容已删除");
                if ("hidden".equals(reply.getStatus())) reply.setContent("内容已隐藏");
            }
        }

        ForumPostDetailVO detail = new ForumPostDetailVO();
        detail.setPost(post);
        detail.setReplies(PageResult.of(replies));
        return detail;
    }

    @Transactional
    public void deleteOwnPost(long userId, long postId) {
        ForumPost post = requirePost(postId);
        if (!post.getUserId().equals(userId)) throw new BusinessException("只能删除自己的帖子");
        if (!"normal".equals(post.getStatus())) throw new BusinessException("当前帖子不可删除");
        forumMapper.softDeleteByAuthor(postId, userId);
        forumMapper.updateImageStatusByPost(postId, "deleted");
    }

    @Transactional
    public ForumReply addReply(long userId, long postId, String content) {
        ForumPost post = requirePost(postId);
        if (!"normal".equals(post.getStatus())) throw new BusinessException("当前帖子不可回复");
        ForumReply reply = new ForumReply();
        reply.setPostId(postId);
        reply.setUserId(userId);
        reply.setContent(content.trim());
        reply.setStatus("normal");
        reply.setCreatedAt(LocalDateTime.now(clock));
        replyMapper.insert(reply);
        return reply;
    }

    public void deleteOwnReply(long userId, long replyId) {
        ForumReply reply = requireReply(replyId);
        if (!reply.getUserId().equals(userId)) throw new BusinessException("只能删除自己的回复");
        if (!"normal".equals(reply.getStatus())) throw new BusinessException("当前回复不可删除");
        replyMapper.softDeleteByAuthor(replyId, userId);
    }

    public ForumReport report(long userId, ForumReportRequest request) {
        validateReport(request);
        if (reportMapper.countPending(userId, request.getTargetType(), request.getTargetId()) > 0) {
            throw new BusinessException("你已经举报过该内容，请等待管理员处理");
        }
        if ("post".equals(request.getTargetType())) {
            ForumPost post = requirePost(request.getTargetId());
            if (!"normal".equals(post.getStatus())) throw new BusinessException("该帖子当前不可举报");
            if (post.getUserId().equals(userId)) throw new BusinessException("不能举报自己的帖子");
        } else {
            ForumReply reply = requireReply(request.getTargetId());
            if (!"normal".equals(reply.getStatus())) throw new BusinessException("该回复当前不可举报");
            if (reply.getUserId().equals(userId)) throw new BusinessException("不能举报自己的回复");
        }
        ForumReport report = new ForumReport();
        report.setReporterId(userId);
        report.setTargetType(request.getTargetType());
        report.setTargetId(request.getTargetId());
        report.setReason(request.getReason());
        report.setStatus("pending");
        report.setCreatedAt(LocalDateTime.now(clock));
        reportMapper.insert(report);
        return report;
    }

    public void setPinned(long operatorId, long postId, boolean pinned) {
        ForumPost post = requirePost(postId);
        if (!"normal".equals(post.getStatus())) throw new BusinessException("只有正常帖子可以置顶");
        forumMapper.updatePinned(postId, pinned, operatorId);
    }

    @Transactional
    public void updatePostStatus(long operatorId, long postId, String status) {
        validateContentStatus(status);
        requirePost(postId);
        forumMapper.updatePostStatus(postId, status, operatorId);
        forumMapper.updateImageStatusByPost(postId, status);
    }

    public void updateReplyStatus(long operatorId, long replyId, String status) {
        validateContentStatus(status);
        requireReply(replyId);
        replyMapper.updateStatus(replyId, status, operatorId);
    }

    public PageResult<ForumReport> adminListReports(String status, int pageNum, int pageSize) {
        if (status != null && !status.isBlank()
                && !"pending".equals(status) && !REPORT_STATUSES.contains(status)) {
            throw new BusinessException("举报状态不合法");
        }
        PageHelper.startPage(pageNum, pageSize);
        List<ForumReport> reports = reportMapper.findAll(emptyToNull(status));
        return PageResult.of(reports);
    }

    public void handleReport(long operatorId, long reportId, ForumReportHandleRequest request) {
        if (!REPORT_STATUSES.contains(request.getStatus())) throw new BusinessException("举报处理状态不合法");
        ForumReport report = reportMapper.selectById(reportId);
        if (report == null) throw new BusinessException("举报记录不存在");
        if (!"pending".equals(report.getStatus())) throw new BusinessException("该举报已经处理");
        reportMapper.handle(reportId, request.getStatus(), emptyToNull(request.getResult()), operatorId);
    }

    private ForumPost requirePost(long postId) {
        ForumPost post = forumMapper.selectPostById(postId);
        if (post == null) throw new BusinessException("帖子不存在");
        return post;
    }

    private ForumReply requireReply(long replyId) {
        ForumReply reply = replyMapper.selectById(replyId);
        if (reply == null) throw new BusinessException("回复不存在");
        return reply;
    }

    private void fillImages(List<ForumPost> posts, String status) {
        for (ForumPost post : posts) {
            post.setImageUrls(forumMapper.findImages(post.getId(), status).stream()
                    .map(ForumPostImage::getImageUrl).collect(Collectors.toList()));
        }
    }

    private void validateCategory(String category) {
        if (!CATEGORIES.contains(category)) throw new BusinessException("帖子分类不合法");
    }

    private void validateCategoryFilter(String category) {
        if (category != null && !category.isBlank()) validateCategory(category);
    }

    private void validateContentStatus(String status) {
        if (!CONTENT_STATUSES.contains(status)) throw new BusinessException("内容状态不合法");
    }

    private void validateReport(ForumReportRequest request) {
        if (!"post".equals(request.getTargetType()) && !"reply".equals(request.getTargetType())) {
            throw new BusinessException("举报对象类型不合法");
        }
        if (!REPORT_REASONS.contains(request.getReason())) throw new BusinessException("举报原因不合法");
    }

    private void cleanupUploads(List<String> urls) {
        for (String url : urls) {
            try {
                ossService.deleteByUrl(url);
            } catch (RuntimeException ignored) {
                // 补偿删除失败不覆盖原始业务异常，保留日志由 OSS SDK 处理。
            }
        }
    }

    private String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
