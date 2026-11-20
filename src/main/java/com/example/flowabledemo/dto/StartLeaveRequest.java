package com.example.flowabledemo.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 发起请假申请的请求体。businessKey 同时作为幂等键与流程业务主键。 */
public record StartLeaveRequest(
        @NotBlank(message = "businessKey 不能为空")
        @Size(max = 64, message = "businessKey 最长 64 字符")
        String businessKey,

        @NotBlank(message = "applicantId 不能为空")
        @Size(max = 64, message = "applicantId 最长 64 字符")
        String applicantId,

        @NotBlank(message = "approverId 不能为空")
        @Size(max = 64, message = "approverId 最长 64 字符")
        String approverId,

        @Min(value = 1, message = "请假天数至少为 1")
        @Max(value = 365, message = "请假天数最多为 365")
        int days,

        @NotBlank(message = "reason 不能为空")
        @Size(max = 512, message = "reason 最长 512 字符")
        String reason
) {
}
