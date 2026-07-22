package com.badminton.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
public class CourtAvailabilityVO {
    private Long courtId;
    private String name;
    private Integer status;
    private String statusDisplay;
    private String remark;
    private List<PlayerSlotVO> players = new ArrayList<>();

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PlayerSlotVO {
        private String avatar;
        private Integer gender;
        private String nickname;
        private Integer age;
    }
}
