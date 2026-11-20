package com.example.flowabledemo.service;

import com.example.flowabledemo.dto.ProcessDefinitionVO;
import org.flowable.engine.RepositoryService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** 流程定义查询：验证 resources/processes 下的 BPMN 是否已被自动部署。 */
@Service
public class ProcessDefinitionService {

    private final RepositoryService repositoryService;

    public ProcessDefinitionService(RepositoryService repositoryService) {
        this.repositoryService = repositoryService;
    }

    @Transactional(readOnly = true)
    public List<ProcessDefinitionVO> listLatestDefinitions() {
        return repositoryService.createProcessDefinitionQuery()
                .latestVersion()
                .active()
                .orderByProcessDefinitionKey().asc()
                .list().stream()
                .map(d -> new ProcessDefinitionVO(
                        d.getId(), d.getKey(), d.getName(), d.getVersion(),
                        d.getDeploymentId(), d.getResourceName(), d.isSuspended()))
                .toList();
    }
}
