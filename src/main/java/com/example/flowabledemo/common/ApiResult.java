package com.example.flowabledemo.common;

/**
 * 统一响应体。成功时 code=0，data 为业务数据；失败时 data=null，code 为 HTTP 状态码。
 */
public record ApiResult<T>(int code, String message, T data, long timestamp) {

    public static <T> ApiResult<T> success(T data) {
        return new ApiResult<>(0, "ok", data, System.currentTimeMillis());
    }

    public static <T> ApiResult<T> success(String message, T data) {
        return new ApiResult<>(0, message, data, System.currentTimeMillis());
    }

    public static <T> ApiResult<T> failure(int code, String message) {
        return new ApiResult<>(code, message, null, System.currentTimeMillis());
    }
}
