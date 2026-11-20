package com.example.flowabledemo.dto;

import java.util.Date;

/** 流程运行状态 + 历史概要。running=false 时 endTime 等历史字段才有值。 */
public record ProcessStatusVO(
        String processInstanceId,
        String businessKey,
        boolean running,
        String processDefinitionKey,
        String startUserId,
        Date startTime,
        Date endTime
) {
}
