package com.example.flowabledemo.dto;

import java.util.Date;

/** 待办任务视图对象。 */
public record TaskVO(
        String taskId,
        String taskName,
        String processInstanceId,
        String assignee,
        Date createTime
) {
}
