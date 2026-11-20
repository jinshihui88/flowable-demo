package com.example.flowabledemo.dto;

/** 流程定义视图对象（自动部署后可查询）。 */
public record ProcessDefinitionVO(
        String processDefinitionId,
        String key,
        String name,
        int version,
        String deploymentId,
        String resourceName,
        boolean suspended
) {
}
