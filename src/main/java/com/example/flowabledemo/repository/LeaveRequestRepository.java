package com.example.flowabledemo.repository;

import com.example.flowabledemo.dto.StartLeaveRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 业务表访问层。业务数据与 Flowable 的 ACT_* 表分开管理，
 * 通过 business_key / process_instance_id 建立关联。
 */
@Repository
public class LeaveRequestRepository {

    private final JdbcTemplate jdbcTemplate;

    public LeaveRequestRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 插入业务单。uk_business_key 唯一约束会在并发重复提交时抛 DuplicateKeyException，
     * 由 GlobalExceptionHandler 统一映射为 HTTP 409。
     */
    public void insert(StartLeaveRequest request) {
        jdbcTemplate.update(
                "INSERT INTO leave_request (business_key, applicant_id, approver_id, days, reason, status) "
                        + "VALUES (?, ?, ?, ?, ?, 'RUNNING')",
                request.businessKey(), request.applicantId(), request.approverId(),
                request.days(), request.reason());
    }

    public void updateProcessInstanceId(String businessKey, String processInstanceId) {
        jdbcTemplate.update(
                "UPDATE leave_request SET process_instance_id = ? WHERE business_key = ?",
                processInstanceId, businessKey);
    }

    public void updateStatusByProcessInstanceId(String processInstanceId, String status) {
        jdbcTemplate.update(
                "UPDATE leave_request SET status = ? WHERE process_instance_id = ?",
                status, processInstanceId);
    }
}
