package com.badminton.controller;

import com.badminton.dto.response.PageResult;
import com.badminton.entity.ForumPost;
import com.badminton.entity.ForumReply;
import com.badminton.service.ForumService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 论坛接口的登录、管理权限和 multipart 参数绑定测试。 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ForumControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ForumService forumService;

    @Test
    void unauthenticatedUserCannotAccessForumApi() throws Exception {
        mockMvc.perform(get("/forum/posts"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(-1));

        verifyNoInteractions(forumService);
    }

    @Test
    void loggedInUserCanCreateMultipartPost() throws Exception {
        MockMultipartFile image = new MockMultipartFile(
                "images", "court.jpg", "image/jpeg", new byte[]{1, 2, 3});
        ForumPost created = new ForumPost();
        created.setId(10L);
        when(forumService.createPost(eq(7L), any())).thenReturn(created);

        mockMvc.perform(multipart("/forum/posts")
                        .file(image)
                        .param("title", "寻找周末双打搭档")
                        .param("category", "约球组队")
                        .param("content", "本周六下午想找三位同学一起打双打。")
                        .sessionAttr("userId", 7L)
                        .sessionAttr("role", "user"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1))
                .andExpect(jsonPath("$.data.id").value(10));

        verify(forumService).createPost(eq(7L), any());
    }

    @Test
    void normalUserCannotAccessAdminForumApi() throws Exception {
        mockMvc.perform(get("/admin/forum/posts")
                        .sessionAttr("userId", 7L)
                        .sessionAttr("role", "user"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(0));

        verifyNoInteractions(forumService);
    }

    @Test
    void adminCanAccessAdminForumApi() throws Exception {
        PageResult<ForumPost> page = PageResult.of(Collections.emptyList());
        when(forumService.adminListPosts(null, null, null, 1, 10)).thenReturn(page);

        mockMvc.perform(get("/admin/forum/posts")
                        .sessionAttr("userId", 99L)
                        .sessionAttr("role", "admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1));
    }

    @Test
    void adminCanViewExactReportedReply() throws Exception {
        ForumReply reply = new ForumReply();
        reply.setId(11L);
        reply.setPostId(10L);
        reply.setContent("被举报的回复内容");
        when(forumService.getReplyForAdmin(11L)).thenReturn(reply);

        mockMvc.perform(get("/admin/forum/replies/11")
                        .sessionAttr("userId", 99L)
                        .sessionAttr("role", "admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1))
                .andExpect(jsonPath("$.data.id").value(11))
                .andExpect(jsonPath("$.data.postId").value(10))
                .andExpect(jsonPath("$.data.content").value("被举报的回复内容"));

        verify(forumService).getReplyForAdmin(11L);
    }
}
