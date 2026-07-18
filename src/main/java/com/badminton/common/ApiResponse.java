package com.badminton.common;

import lombok.Getter;

@Getter
public class ApiResponse<T> {
    private final int code;
    private final String msg;
    private final T data;

    private ApiResponse(int code, String msg, T data) {
        this.code = code;
        this.msg = msg;
        this.data = data;
    }

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(1, "success", data);
    }

    public static <T> ApiResponse<T> failure(String msg) {
        return new ApiResponse<>(0, msg, null);
    }

    public static <T> ApiResponse<T> unauthorized(String msg) {
        return new ApiResponse<>(-1, msg, null);
    }
}
