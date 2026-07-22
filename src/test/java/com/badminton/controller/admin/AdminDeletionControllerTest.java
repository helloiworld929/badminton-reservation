package com.badminton.controller.admin;

import com.badminton.common.ApiResponse;
import com.badminton.entity.Activity;
import com.badminton.entity.Court;
import com.badminton.mapper.ActivityMapper;
import com.badminton.mapper.ActivitySignupMapper;
import com.badminton.mapper.CourtMapper;
import com.badminton.mapper.UserMapper;
import com.badminton.service.OssService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminDeletionControllerTest {
    @Mock
    private CourtMapper courtMapper;

    @InjectMocks
    private AdminCourtController courtController;

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
    void deletingCourtKeepsHistoricalRowBySoftDeletingIt() {
        Court court = new Court();
        court.setId(1L);
        court.setName("测试场地");
        when(courtMapper.selectById(1L)).thenReturn(court);
        when(courtMapper.softDeleteById(1L)).thenReturn(1);

        ApiResponse<Boolean> response = courtController.delete(1L);

        assertTrue(response.getData());
        verify(courtMapper).softDeleteById(1L);
    }

    @Test
    void deletingActivityKeepsSignupsBySoftDeletingIt() {
        Activity activity = new Activity();
        activity.setId(10L);
        activity.setTitle("测试活动");
        when(activityMapper.selectById(10L)).thenReturn(activity);
        when(activityMapper.softDeleteById(10L)).thenReturn(1);

        ApiResponse<Boolean> response = activityController.delete(10L);

        assertTrue(response.getData());
        verify(activityMapper).softDeleteById(10L);
    }
}
