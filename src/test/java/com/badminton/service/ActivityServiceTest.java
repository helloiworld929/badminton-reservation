package com.badminton.service;

import com.badminton.common.BusinessException;
import com.badminton.dto.request.ActivitySignupRequest;
import com.badminton.entity.Activity;
import com.badminton.entity.ActivitySignup;
import com.badminton.mapper.ActivityMapper;
import com.badminton.mapper.ActivitySignupMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ActivityServiceTest {
    @Mock
    private ActivityMapper activityMapper;

    @Mock
    private ActivitySignupMapper signupMapper;

    @Test
    void signupStoresOnlyContactInformationForOneUser() throws Exception {
        Activity activity = new Activity();
        activity.setId(10L);
        when(activityMapper.selectById(10L)).thenReturn(activity);
        when(signupMapper.findByActivityIdAndUserId(10L, 7L)).thenReturn(Collections.emptyList());

        ActivityService service = new ActivityService(activityMapper, signupMapper);
        ActivitySignupRequest request = new ActivitySignupRequest();
        request.setActivityId(10L);
        request.setName(" 张三 ");
        request.setPhone("13800138000");

        service.signup(7L, request);

        ArgumentCaptor<ActivitySignup> signupCaptor = ArgumentCaptor.forClass(ActivitySignup.class);
        verify(signupMapper).insert(signupCaptor.capture());
        ActivitySignup signup = signupCaptor.getValue();
        assertEquals(10L, signup.getActivityId());
        assertEquals(7L, signup.getUserId());
        assertEquals("张三", signup.getName());
        assertEquals("13800138000", signup.getPhone());
        assertFalse(new ObjectMapper().writeValueAsString(signup).contains("participantCount"));
    }

    @Test
    void signupRejectsAnotherSignupFromTheSameUser() {
        Activity activity = new Activity();
        activity.setId(10L);
        when(activityMapper.selectById(10L)).thenReturn(activity);
        when(signupMapper.findByActivityIdAndUserId(10L, 7L))
                .thenReturn(Collections.singletonList(new ActivitySignup()));

        ActivityService service = new ActivityService(activityMapper, signupMapper);
        ActivitySignupRequest request = new ActivitySignupRequest();
        request.setActivityId(10L);
        request.setName("张三");
        request.setPhone("13800138000");

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.signup(7L, request));

        assertEquals("您已报名该活动，请勿重复报名", exception.getMessage());
        verify(signupMapper, never()).insert(any(ActivitySignup.class));
    }
}
