package com.hify.modules.workflow.service.impl;

import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;
import com.hify.modules.workflow.dto.response.WorkflowRunResponse;
import com.hify.modules.workflow.api.WorkflowRunService;
import com.hify.modules.workflow.service.impl.engine.WorkflowEngine;
import com.hify.modules.workflow.service.impl.engine.WorkflowRunResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowRunServiceImpl implements com.hify.modules.workflow.api.WorkflowRunService {

    private final WorkflowEngine workflowEngine;

    @Override
    public String run(Long workflowId, String userMessage) {
        WorkflowRunResult result = workflowEngine.run(workflowId, userMessage);
        if (!"SUCCESS".equals(result.status())) {
            String err = result.error() != null ? result.error() : "工作流执行失败";
            throw new BizException(ErrorCode.INTERNAL_ERROR, err);
        }
        return result.output();
    }

    @Override
    public WorkflowRunResponse runWorkflow(Long workflowId, String userMessage) {
        WorkflowRunResult result = workflowEngine.run(workflowId, userMessage);
        WorkflowRunResponse resp = new WorkflowRunResponse();
        resp.setRunId(result.runId());
        resp.setStatus(result.status());
        resp.setOutput(result.output());
        resp.setError(result.error());
        return resp;
    }
}
