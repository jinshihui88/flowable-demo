package com.example.flowabledemo.dto;

import java.util.Date;

/** 历史任务轨迹（审批轨迹）。 */
public record HistoricTaskVO(
        String taskId,
        String taskName,
        String assignee,
        Date startTime,
        Date endTime,
        Long durationInMillis
) {
}
