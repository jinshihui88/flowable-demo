package com.example.flowabledemo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 审批请求。
 * 注意：operatorId 仅为教程演示字段，生产项目必须从已认证的主体（Principal/JWT）中获取，
 * 不能信任前端传入，否则存在伪造审批人的越权风险。
 */
public record ApprovalRequest(
        @NotBlank(message = "operatorId 不能为空")
        String operatorId,

        boolean approved,

        @Size(max = 512, message = "comment 最长 512 字符")
        String comment
) {
}
