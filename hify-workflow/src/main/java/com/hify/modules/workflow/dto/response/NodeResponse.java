package com.hify.modules.workflow.dto.response;

import com.hify.modules.workflow.service.impl.nodeconfig.NodeConfig;
import lombok.Data;

/**
 * 工作流节点响应
 */
@Data
public class NodeResponse {

    private String nodeKey;

    private String type;

    private String name;

    private NodeConfig config;
}
