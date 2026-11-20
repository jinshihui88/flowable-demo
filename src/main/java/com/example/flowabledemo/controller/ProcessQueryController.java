package com.example.flowabledemo.controller;

import com.example.flowabledemo.common.ApiResult;
import com.example.flowabledemo.dto.HistoricTaskVO;
import com.example.flowabledemo.dto.HistoricVariableVO;
import com.example.flowabledemo.dto.ProcessDefinitionVO;
import com.example.flowabledemo.dto.ProcessStatusVO;
import com.example.flowabledemo.service.ProcessDefinitionService;
import com.example.flowabledemo.service.ProcessQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 流程定义、运行状态与历史轨迹的查询接口。 */
@RestController
@RequestMapping("/api/process")
public class ProcessQueryController {

    private final ProcessDefinitionService processDefinitionService;
    private final ProcessQueryService processQueryService;

    public ProcessQueryController(ProcessDefinitionService processDefinitionService,
                                  ProcessQueryService processQueryService) {
        this.processDefinitionService = processDefinitionService;
        this.processQueryService = processQueryService;
    }

    /** 查看自动部署的最新版流程定义，用于确认 BPMN 已被扫描到。 */
    @GetMapping("/definitions")
    public ApiResult<List<ProcessDefinitionVO>> definitions() {
        return ApiResult.success(processDefinitionService.listLatestDefinitions());
    }

    /** 查看流程是否仍在运行，以及开始/结束时间、发起人。 */
    @GetMapping("/instances/{processInstanceId}/status")
    public ApiResult<ProcessStatusVO> status(@PathVariable String processInstanceId) {
        return ApiResult.success(processQueryService.status(processInstanceId));
    }

    /** 查看历史任务轨迹（审批轨迹）。 */
    @GetMapping("/instances/{processInstanceId}/history/tasks")
    public ApiResult<List<HistoricTaskVO>> historicTasks(@PathVariable String processInstanceId) {
        return ApiResult.success(processQueryService.listHistoricTasks(processInstanceId));
    }

    /** 查看历史流程变量（approved、审批意见等）。 */
    @GetMapping("/instances/{processInstanceId}/history/variables")
    public ApiResult<List<HistoricVariableVO>> historicVariables(@PathVariable String processInstanceId) {
        return ApiResult.success(processQueryService.listHistoricVariables(processInstanceId));
    }
}
