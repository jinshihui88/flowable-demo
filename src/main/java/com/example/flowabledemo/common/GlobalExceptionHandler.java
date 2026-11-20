package com.example.flowabledemo.common;

import jakarta.validation.ConstraintViolationException;
import org.flowable.common.engine.api.FlowableException;
import org.flowable.common.engine.api.FlowableObjectNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

/**
 * 统一异常处理：业务参数错误返回 4xx，而不是让 IllegalArgumentException
 * 冒泡成 HTTP 500。同时避免把引擎内部堆栈直接暴露给调用方。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResult<Void>> handleValidation(MethodArgumentNotValidException ex) {
        String msg = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return build(HttpStatus.BAD_REQUEST, msg.isEmpty() ? "请求参数不合法" : msg);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResult<Void>> handleConstraint(ConstraintViolationException ex) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResult<Void>> handleUnreadable(HttpMessageNotReadableException ex) {
        return build(HttpStatus.BAD_REQUEST, "请求体不是合法的 JSON");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResult<Void>> handleIllegalArgument(IllegalArgumentException ex) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(DuplicateKeyException.class)
    public ResponseEntity<ApiResult<Void>> handleDuplicate(DuplicateKeyException ex) {
        return build(HttpStatus.CONFLICT, "该业务单号已存在，请勿重复提交");
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiResult<Void>> handleIllegalState(IllegalStateException ex) {
        return build(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(FlowableObjectNotFoundException.class)
    public ResponseEntity<ApiResult<Void>> handleNotFound(FlowableObjectNotFoundException ex) {
        return build(HttpStatus.NOT_FOUND, "流程对象不存在：" + ex.getMessage());
    }

    @ExceptionHandler(FlowableException.class)
    public ResponseEntity<ApiResult<Void>> handleFlowable(FlowableException ex) {
        log.warn("Flowable 引擎异常: {}", ex.getMessage());
        return build(HttpStatus.BAD_REQUEST, "流程引擎处理失败：" + ex.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResult<Void>> handleUnexpected(Exception ex) {
        log.error("未预期的服务端异常", ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "服务端内部错误，请查看日志");
    }

    private ResponseEntity<ApiResult<Void>> build(HttpStatus status, String message) {
        return ResponseEntity.status(status)
                .body(ApiResult.failure(status.value(), message));
    }
}
