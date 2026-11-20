package com.example.flowabledemo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Spring Boot 3.5.4 + Flowable 7.2.0 请假审批流程示例。
 *
 * <p>Flowable 的 ProcessEngine、RuntimeService、TaskService、HistoryService、
 * RepositoryService 由 flowable-spring-boot-starter-process 自动装配，
 * 与业务代码共用同一个 DataSource 与事务管理器。
 */
@SpringBootApplication
public class FlowableDemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(FlowableDemoApplication.class, args);
    }
}
