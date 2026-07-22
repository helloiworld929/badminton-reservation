package com.badminton.controller.admin;

import com.badminton.common.ApiResponse;
import com.badminton.dto.response.SignupVO;
import com.badminton.entity.Activity;
import com.badminton.entity.ActivitySignup;
import com.badminton.entity.User;
import com.badminton.mapper.ActivityMapper;
import com.badminton.mapper.ActivitySignupMapper;
import com.badminton.mapper.UserMapper;
import com.badminton.service.OssService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminActivityControllerTest {
    @Mock
    private ActivityMapper activityMapper;

    @Mock
    private ActivitySignupMapper signupMapper;

    @Mock
    private UserMapper userMapper;

    @Mock
    private OssService ossService;

    @InjectMocks
    private AdminActivityController activityController;

    @Test
    void signupDetailsContainContactInformationWithoutParticipantCount() throws Exception {
        Activity activity = new Activity();
        activity.setId(10L);
        ActivitySignup signup = new ActivitySignup();
        signup.setId(3L);
        signup.setActivityId(10L);
        signup.setUserId(7L);
        signup.setName("张三");
        signup.setPhone("13800138000");
        User user = new User();
        user.setId(7L);
        user.setNickname("羽球爱好者");
        user.setAvatar("https://example/avatar.png");

        when(activityMapper.selectByIdIncludingDeleted(10L)).thenReturn(activity);
        when(signupMapper.findByActivityId(10L)).thenReturn(Collections.singletonList(signup));
        when(userMapper.selectById(7L)).thenReturn(user);

        ApiResponse<List<SignupVO>> response = activityController.signups(10L);

        SignupVO detail = response.getData().get(0);
        assertEquals("张三", detail.getName());
        assertEquals("13800138000", detail.getPhone());
        assertEquals("羽球爱好者", detail.getNickname());
        assertFalse(new ObjectMapper().writeValueAsString(response).contains("participantCount"));
    }
}
