package com.badminton.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class User {
    private Long id;
    private String nickname;
    private String gender;
    private Integer age;
    private String username;
    private String phone;
    private String avatar;
    private String status;
    private String role;
    private Integer noshowCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // 密码字段不自动生成 getter，防止序列化泄露
    @JsonIgnore
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    private String password;
}
