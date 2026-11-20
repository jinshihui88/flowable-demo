-- 业务表：与 Flowable 的 ACT_* 引擎表分库分表管理，通过 business_key / process_instance_id 关联。
-- uk_business_key 是幂等兜底：并发重复提交同一业务单号时由数据库唯一约束拦截。
CREATE TABLE IF NOT EXISTS leave_request (
    id                  BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    business_key        VARCHAR(64)  NOT NULL COMMENT '业务单号（唯一）',
    applicant_id        VARCHAR(64)  NOT NULL COMMENT '申请人',
    approver_id         VARCHAR(64)  NOT NULL COMMENT '审批人',
    days                INT          NOT NULL COMMENT '请假天数',
    reason              VARCHAR(512) NOT NULL COMMENT '请假事由',
    process_instance_id VARCHAR(64)  NULL COMMENT 'Flowable 流程实例 ID',
    status              VARCHAR(20)  NOT NULL DEFAULT 'RUNNING' COMMENT 'RUNNING/APPROVED/REJECTED',
    created_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_business_key (business_key),
    KEY idx_process_instance_id (process_instance_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='请假申请业务表';
