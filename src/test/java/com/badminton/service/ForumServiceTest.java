package com.badminton.service;

import com.badminton.common.BusinessException;
import com.badminton.dto.request.ForumPostCreateRequest;
import com.badminton.dto.request.ForumReportRequest;
import com.badminton.dto.response.ForumPostDetailVO;
import com.badminton.entity.ForumPost;
import com.badminton.entity.ForumPostImage;
import com.badminton.entity.ForumReply;
import com.badminton.mapper.ForumMapper;
import com.badminton.mapper.ForumReplyMapper;
import com.badminton.mapper.ForumReportMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 论坛核心行为测试，不连接真实数据库和 OSS。 */
@SpringBootTest
@ActiveProfiles("test")
@Import(ForumServiceTest.FixedClockConfiguration.class)
class ForumServiceTest {
    @Autowired
    private ForumService forumService;

    @MockBean
    private ForumMapper forumMapper;
    @MockBean
    private ForumReplyMapper replyMapper;
    @MockBean
    private ForumReportMapper reportMapper;
    @MockBean
    private OssService ossService;
    @MockBean
    private PlatformTransactionManager transactionManager;
    @MockBean
    private org.springframework.data.redis.core.StringRedisTemplate stringRedisTemplate;

    @Test
    void createPostUploadsAndStoresImages() throws Exception {
        MockMultipartFile first = image("first.jpg");
        MockMultipartFile second = image("second.jpg");
        ForumPostCreateRequest request = request(first, second);
        when(ossService.upload(first, "forum")).thenReturn("https://example/forum/first.jpg");
        when(ossService.upload(second, "forum")).thenReturn("https://example/forum/second.jpg");
        when(forumMapper.insertPost(any(ForumPost.class))).thenAnswer(invocation -> {
            invocation.getArgument(0, ForumPost.class).setId(10L);
            return 1;
        });

        ForumPost post = forumService.createPost(7L, request);

        assertEquals(10L, post.getId());
        assertEquals(2, post.getImageUrls().size());
        verify(forumMapper, times(2)).insertImage(any(ForumPostImage.class));
    }

    @Test
    void createPostCleansUploadedImageWhenDatabaseWriteFails() throws Exception {
        MockMultipartFile image = image("failed.jpg");
        ForumPostCreateRequest request = request(image);
        when(ossService.upload(image, "forum")).thenReturn("https://example/forum/failed.jpg");
        when(forumMapper.insertPost(any(ForumPost.class))).thenThrow(new RuntimeException("db failed"));

        assertThrows(RuntimeException.class, () -> forumService.createPost(7L, request));

        verify(ossService).deleteByUrl("https://example/forum/failed.jpg");
    }

    @Test
    void userDetailIncrementsLightweightViewCount() {
        ForumPost post = post(10L, 7L, "normal");
        post.setViewCount(5);
        when(forumMapper.selectPostById(10L)).thenReturn(post);
        when(forumMapper.findImages(10L, "normal")).thenReturn(Collections.emptyList());
        when(replyMapper.findByPost(10L, null)).thenReturn(Collections.emptyList());

        ForumPostDetailVO detail = forumService.getPostDetail(10L, 1, 20, false, true);

        assertEquals(6, detail.getPost().getViewCount());
        verify(forumMapper).incrementViewCount(10L);
    }

    @Test
    void userCannotDeleteAnotherUsersPost() {
        when(forumMapper.selectPostById(10L)).thenReturn(post(10L, 8L, "normal"));

        assertThrows(BusinessException.class, () -> forumService.deleteOwnPost(7L, 10L));

        verify(forumMapper, never()).softDeleteByAuthor(any(), any());
    }

    @Test
    void duplicatePendingReportIsRejected() {
        ForumReportRequest request = new ForumReportRequest();
        request.setTargetType("post");
        request.setTargetId(10L);
        request.setReason("广告");
        when(reportMapper.countPending(7L, "post", 10L)).thenReturn(1);

        assertThrows(BusinessException.class, () -> forumService.report(7L, request));

        verify(reportMapper, never()).insert(any());
    }

    @Test
    void adminHidePostAlsoHidesImages() {
        when(forumMapper.selectPostById(10L)).thenReturn(post(10L, 7L, "normal"));

        forumService.updatePostStatus(99L, 10L, "hidden");

        verify(forumMapper).updatePostStatus(10L, "hidden", 99L);
        verify(forumMapper).updateImageStatusByPost(10L, "hidden");
    }

    @Test
    void replyCanOnlyBeAddedToNormalPost() {
        when(forumMapper.selectPostById(10L)).thenReturn(post(10L, 7L, "hidden"));

        assertThrows(BusinessException.class, () -> forumService.addReply(8L, 10L, "测试回复"));

        verify(replyMapper, never()).insert(any(ForumReply.class));
    }

    private static ForumPostCreateRequest request(MockMultipartFile... images) {
        ForumPostCreateRequest request = new ForumPostCreateRequest();
        request.setTitle("寻找周末双打搭档");
        request.setContent("本周六下午想找三位同学一起打双打。欢迎回复交流。");
        request.setCategory("约球组队");
        request.setImages(Arrays.asList(images));
        return request;
    }

    private static MockMultipartFile image(String name) {
        return new MockMultipartFile("images", name, "image/jpeg", new byte[]{1, 2, 3});
    }

    private static ForumPost post(long id, long userId, String status) {
        ForumPost post = new ForumPost();
        post.setId(id);
        post.setUserId(userId);
        post.setTitle("测试帖子标题");
        post.setContent("测试帖子正文");
        post.setCategory("技术交流");
        post.setStatus(status);
        post.setPinned(false);
        post.setViewCount(0);
        return post;
    }

    @TestConfiguration
    static class FixedClockConfiguration {
        @Bean
        @Primary
        Clock forumTestClock() {
            return Clock.fixed(Instant.parse("2026-07-20T02:00:00Z"), ZoneId.of("Asia/Shanghai"));
        }
    }
}
