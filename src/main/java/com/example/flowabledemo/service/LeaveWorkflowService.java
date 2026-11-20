package com.example.flowabledemo.service;

import com.example.flowabledemo.dto.ApprovalRequest;
import com.example.flowabledemo.dto.StartLeaveRequest;
import com.example.flowabledemo.dto.TaskVO;
import com.example.flowabledemo.repository.LeaveRequestRepository;
import org.flowable.engine.IdentityService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.Task;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 请假流程的业务服务：发起申请、查询待办、办理审批。 */
@Service
public class LeaveWorkflowService {

    /** 与 BPMN 中 process id="leaveApproval" 保持一致 */
    public static final String PROCESS_KEY = "leaveApproval";
    private static final String STATUS_APPROVED = "APPROVED";
    private static final String STATUS_REJECTED = "REJECTED";

    private final RuntimeService runtimeService;
    private final TaskService taskService;
    private final IdentityService identityService;
    private final LeaveRequestRepository leaveRequestRepository;

    public LeaveWorkflowService(RuntimeService runtimeService,
                                TaskService taskService,
                                IdentityService identityService,
                                LeaveRequestRepository leaveRequestRepository) {
        this.runtimeService = runtimeService;
        this.taskService = taskService;
        this.identityService = identityService;
        this.leaveRequestRepository = leaveRequestRepository;
    }

    /**
     * 发起请假流程。
     * 业务表写入 + 流程启动在同一个 Spring 事务内，异常时一起回滚；
     * 唯一索引负责并发幂等，比「先查询再启动」的预检查更可靠。
     */
    @Transactional
    public String start(StartLeaveRequest request) {
        leaveRequestRepository.insert(request);

        Map<String, Object> variables = new HashMap<>();
        variables.put("applicantId", request.applicantId());
        variables.put("approverId", request.approverId());
        variables.put("days", request.days());
        variables.put("reason", request.reason());

        ProcessInstance instance;
        try {
            // 让 ACT_HI_PROCINST.START_USER_ID_ 记录发起人，ThreadLocal 用完必须清理
            identityService.setAuthenticatedUserId(request.applicantId());
            instance = runtimeService.startProcessInstanceByKey(
                    PROCESS_KEY, request.businessKey(), variables);
        } finally {
            identityService.setAuthenticatedUserId(null);
        }

        leaveRequestRepository.updateProcessInstanceId(request.businessKey(), instance.getId());
        return instance.getId();
    }

    /** 查询指定用户的待办（BPMN 使用 assignee 直接分配，故用 taskAssignee）。 */
    @Transactional(readOnly = true)
    public List<TaskVO> listMyTasks(String userId) {
        if (!StringUtils.hasText(userId)) {
            throw new IllegalArgumentException("userId 不能为空");
        }
        return taskService.createTaskQuery()
                .taskAssignee(userId)
                .active()
                .orderByTaskCreateTime().desc()
                .list().stream()
                .map(t -> new TaskVO(t.getId(), t.getName(), t.getProcessInstanceId(),
                        t.getAssignee(), t.getCreateTime()))
                .toList();
    }

    /**
     * 办理审批任务。完成任务时写入 approved 变量，引擎据此计算排他网关走通过/驳回分支。
     * 通过 taskAssignee 限定只能办理分配给自己的任务，避免越权。
     */
    @Transactional
    public void completeTask(String taskId, ApprovalRequest request) {
        Task task = taskService.createTaskQuery()
                .taskId(taskId)
                .taskAssignee(request.operatorId())
                .active()
                .singleResult();
        if (task == null) {
            throw new IllegalArgumentException("任务不存在、已办理或当前用户无权办理");
        }

        Map<String, Object> variables = new HashMap<>();
        variables.put("approved", request.approved());
        variables.put("approvalComment", request.comment() == null ? "" : request.comment());
        variables.put("approvedBy", request.operatorId());

        taskService.complete(taskId, variables);

        leaveRequestRepository.updateStatusByProcessInstanceId(
                task.getProcessInstanceId(),
                request.approved() ? STATUS_APPROVED : STATUS_REJECTED);
    }
}
