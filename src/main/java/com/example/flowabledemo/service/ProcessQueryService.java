package com.example.flowabledemo.service;

import com.example.flowabledemo.dto.HistoricTaskVO;
import com.example.flowabledemo.dto.HistoricVariableVO;
import com.example.flowabledemo.dto.ProcessStatusVO;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.engine.runtime.ProcessInstance;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/** 流程运行状态与历史轨迹查询（对应教程第 10 章）。 */
@Service
public class ProcessQueryService {

    private final RuntimeService runtimeService;
    private final HistoryService historyService;

    public ProcessQueryService(RuntimeService runtimeService, HistoryService historyService) {
        this.runtimeService = runtimeService;
        this.historyService = historyService;
    }

    /** running=true 表示仍在 RuntimeService 中；流程结束后运行实例被移除，只能查历史。 */
    @Transactional(readOnly = true)
    public ProcessStatusVO status(String processInstanceId) {
        requireId(processInstanceId);
        ProcessInstance running = runtimeService.createProcessInstanceQuery()
                .processInstanceId(processInstanceId).singleResult();
        HistoricProcessInstance historic = historyService.createHistoricProcessInstanceQuery()
                .processInstanceId(processInstanceId).singleResult();
        if (running == null && historic == null) {
            throw new IllegalArgumentException("流程实例不存在：" + processInstanceId);
        }
        return new ProcessStatusVO(
                processInstanceId,
                historic != null ? historic.getBusinessKey() : running.getBusinessKey(),
                running != null,
                historic != null ? historic.getProcessDefinitionKey() : running.getProcessDefinitionKey(),
                historic != null ? historic.getStartUserId() : null,
                historic != null ? historic.getStartTime() : null,
                historic != null ? historic.getEndTime() : null);
    }

    /** 审批轨迹：按任务开始时间升序。 */
    @Transactional(readOnly = true)
    public List<HistoricTaskVO> listHistoricTasks(String processInstanceId) {
        requireId(processInstanceId);
        return historyService.createHistoricTaskInstanceQuery()
                .processInstanceId(processInstanceId)
                .orderByHistoricTaskInstanceStartTime().asc()
                .list().stream()
                .map(t -> new HistoricTaskVO(t.getId(), t.getName(), t.getAssignee(),
                        t.getStartTime(), t.getEndTime(), t.getDurationInMillis()))
                .toList();
    }

    /** 历史变量：approved / approvalComment / applicantId 等。 */
    @Transactional(readOnly = true)
    public List<HistoricVariableVO> listHistoricVariables(String processInstanceId) {
        requireId(processInstanceId);
        return historyService.createHistoricVariableInstanceQuery()
                .processInstanceId(processInstanceId)
                .list().stream()
                .map(v -> new HistoricVariableVO(
                        v.getVariableName(),
                        v.getValue() == null ? null : String.valueOf(v.getValue()),
                        v.getVariableTypeName()))
                .toList();
    }

    private void requireId(String processInstanceId) {
        if (!StringUtils.hasText(processInstanceId)) {
            throw new IllegalArgumentException("processInstanceId 不能为空");
        }
    }
}
