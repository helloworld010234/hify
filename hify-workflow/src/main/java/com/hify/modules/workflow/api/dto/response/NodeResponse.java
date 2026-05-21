package com.hify.modules.workflow.api.dto.response;

import com.hify.modules.workflow.domain.nodeconfig.NodeConfig;
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
