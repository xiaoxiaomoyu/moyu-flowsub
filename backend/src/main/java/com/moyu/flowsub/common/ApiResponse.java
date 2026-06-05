package com.moyu.flowsub.common;

/**
 * 统一接口响应结构，保证前端可以用同一套逻辑处理 REST API 返回值。
 */
public record ApiResponse<T>(int code, String message, T data) {

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(0, "success", data);
    }

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(0, "ok", data);
    }
}
