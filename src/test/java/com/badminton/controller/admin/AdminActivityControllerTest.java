package com.badminton.controller.admin;

import com.badminton.common.ApiResponse;
import com.badminton.common.BusinessException;
import com.badminton.dto.response.SignupVO;
import com.badminton.entity.Activity;
import com.badminton.mapper.ActivityMapper;
import com.badminton.mapper.ActivitySignupMapper;
import com.badminton.service.ActivitySignupExcelService;
import com.badminton.service.OssService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminActivityControllerTest {
    @Mock
    private ActivityMapper activityMapper;

    @Mock
    private ActivitySignupMapper signupMapper;

    @Mock
    private OssService ossService;

    @Mock
    private ActivitySignupExcelService excelService;

    @InjectMocks
    private AdminActivityController activityController;

    @Test
    void signupDetailsContainContactInformationWithoutParticipantCount() throws Exception {
        Activity activity = new Activity();
        activity.setId(10L);
        SignupVO signup = new SignupVO();
        signup.setId(3L);
        signup.setActivityId(10L);
        signup.setUserId(7L);
        signup.setName("张三");
        signup.setPhone("13800138000");
        signup.setNickname("羽球爱好者");
        signup.setAvatar("https://example/avatar.png");

        when(activityMapper.selectByIdIncludingDeleted(10L)).thenReturn(activity);
        when(signupMapper.findDetailsByActivityId(10L)).thenReturn(Collections.singletonList(signup));

        ApiResponse<List<SignupVO>> response = activityController.signups(10L);

        SignupVO detail = response.getData().get(0);
        assertEquals("张三", detail.getName());
        assertEquals("13800138000", detail.getPhone());
        assertEquals("羽球爱好者", detail.getNickname());
        assertFalse(new ObjectMapper().writeValueAsString(response).contains("participantCount"));
    }

    @Test
    void exportSignupsReturnsXlsxAttachment() throws Exception {
        Activity activity = new Activity();
        activity.setId(10L);
        activity.setTitle("周末羽毛球赛");
        when(activityMapper.selectByIdIncludingDeleted(10L)).thenReturn(activity);
        when(signupMapper.findDetailsByActivityId(10L)).thenReturn(Collections.emptyList());
        when(excelService.export(eq("周末羽毛球赛"), anyList())).thenReturn(new byte[]{1, 2, 3});

        ResponseEntity<byte[]> response = activityController.exportSignups(10L);

        assertEquals(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
                response.getHeaders().getContentType());
        assertEquals("attachment; filename*=UTF-8''%E5%91%A8%E6%9C%AB%E7%BE%BD%E6%AF%9B%E7%90%83%E8%B5%9B-%E6%8A%A5%E5%90%8D%E5%90%8D%E5%8D%95.xlsx",
                response.getHeaders().getFirst("Content-Disposition"));
        assertArrayEquals(new byte[]{1, 2, 3}, response.getBody());
    }

    @Test
    void exportSignupsRejectsMissingActivity() {
        when(activityMapper.selectByIdIncludingDeleted(99L)).thenReturn(null);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> activityController.exportSignups(99L));

        assertEquals("活动不存在", exception.getMessage());
        verifyNoInteractions(excelService);
    }
}
