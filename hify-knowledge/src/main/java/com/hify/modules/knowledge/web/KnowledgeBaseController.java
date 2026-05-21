package com.hify.modules.knowledge.web;

import com.hify.common.web.PageResult;
import com.hify.common.web.Result;
import com.hify.modules.knowledge.api.KnowledgeBaseService;
import com.hify.modules.knowledge.api.dto.request.KnowledgeBaseCreateRequest;
import com.hify.modules.knowledge.api.dto.request.KnowledgeBaseListRequest;
import com.hify.modules.knowledge.api.dto.request.KnowledgeBaseUpdateRequest;
import com.hify.modules.knowledge.api.dto.response.KnowledgeBaseResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 知识库管理 Controller
 */
@RestController
@RequestMapping("/api/v1/knowledge-bases")
@RequiredArgsConstructor
public class KnowledgeBaseController {

    private final KnowledgeBaseService knowledgeBaseService;

    /**
     * 创建知识库
     */
    @PostMapping
    public Result<KnowledgeBaseResponse> create(@Valid @RequestBody KnowledgeBaseCreateRequest request) {
        KnowledgeBaseResponse response = knowledgeBaseService.create(request);
        return Result.ok(response);
    }

    /**
     * 分页查询知识库列表
     */
    @GetMapping
    public PageResult<KnowledgeBaseResponse> list(KnowledgeBaseListRequest request) {
        return knowledgeBaseService.list(request);
    }

    /**
     * 查询单个知识库详情
     */
    @GetMapping("/{id:[0-9]+}")
    public Result<KnowledgeBaseResponse> getById(@PathVariable Long id) {
        KnowledgeBaseResponse response = knowledgeBaseService.getById(id);
        return Result.ok(response);
    }

    /**
     * 更新知识库
     */
    @PutMapping("/{id:[0-9]+}")
    public Result<Void> update(@PathVariable Long id,
                                @Valid @RequestBody KnowledgeBaseUpdateRequest request) {
        knowledgeBaseService.update(id, request);
        return Result.ok();
    }

    /**
     * 删除知识库（逻辑删除，级联 document + document_chunk）
     */
    @DeleteMapping("/{id:[0-9]+}")
    public Result<Void> delete(@PathVariable Long id) {
        knowledgeBaseService.delete(id);
        return Result.ok();
    }
}
