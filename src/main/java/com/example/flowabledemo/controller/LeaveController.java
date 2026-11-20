package com.example.flowabledemo.controller;

import com.example.flowabledemo.common.ApiResult;
import com.example.flowabledemo.dto.ApprovalRequest;
import com.example.flowabledemo.dto.StartLeaveRequest;
import com.example.flowabledemo.dto.TaskVO;
import com.example.flowabledemo.service.LeaveWorkflowService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** 请假审批的业务接口。 */
@RestController
@RequestMapping("/api/leave")
public class LeaveController {

    private final LeaveWorkflowService workflowService;

    public LeaveController(LeaveWorkflowService workflowService) {
        this.workflowService = workflowService;
    }

    /** 发起请假申请，返回流程实例 ID。 */
    @PostMapping("/start")
    public ApiResult<Map<String, String>> start(@Valid @RequestBody StartLeaveRequest request) {
        String processInstanceId = workflowService.start(request);
        return ApiResult.success(Map.of("processInstanceId", processInstanceId));
    }

    /**
     * 查询指定用户的待办任务。
     * 注意：userId 由调用方传入仅为教程演示；生产项目应从登录态读取，不接受前端任意指定。
     */
    @GetMapping("/tasks")
    public ApiResult<List<TaskVO>> myTasks(@RequestParam String userId) {
        return ApiResult.success(workflowService.listMyTasks(userId));
    }

    /** 办理审批任务：approved=true 走通过分支，false 走驳回分支。 */
    @PostMapping("/tasks/{taskId}/complete")
    public ApiResult<Map<String, String>> complete(
            @PathVariable String taskId,
            @Valid @RequestBody ApprovalRequest request) {
        workflowService.completeTask(taskId, request);
        return ApiResult.success("审批已提交", Map.of("taskId", taskId));
    }
}
