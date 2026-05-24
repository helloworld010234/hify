package com.hify.modules.knowledge.controller;

import com.hify.common.controller.PageResult;
import com.hify.common.controller.Result;
import com.hify.modules.knowledge.service.DocumentService;
import com.hify.modules.knowledge.dto.request.DocumentListRequest;
import com.hify.modules.knowledge.dto.response.DocumentChunkResponse;
import com.hify.modules.knowledge.dto.response.DocumentResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 文档管理 Controller
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService documentService;

    /**
     * 上传文档
     */
    @PostMapping("/knowledge-bases/{kbId:[0-9]+}/documents")
    public Result<Long> upload(@PathVariable Long kbId,
                                @RequestParam("file") MultipartFile file) {
        Long documentId = documentService.upload(kbId, file);
        return Result.ok(documentId);
    }

    /**
     * 分页查询知识库下的文档列表
     */
    @GetMapping("/knowledge-bases/{kbId:[0-9]+}/documents")
    public Result<PageResult<DocumentResponse>> list(@PathVariable Long kbId,
                                                      DocumentListRequest request) {
        return documentService.list(kbId, request);
    }

    /**
     * 查询文档详情
     */
    @GetMapping("/documents/{id:[0-9]+}")
    public Result<DocumentResponse> getById(@PathVariable Long id) {
        DocumentResponse response = documentService.getById(id);
        return Result.ok(response);
    }

    /**
     * 查询文档的分块列表（来自 pgvector）
     */
    @GetMapping("/documents/{id:[0-9]+}/chunks")
    public Result<List<DocumentChunkResponse>> listChunks(@PathVariable Long id) {
        List<DocumentChunkResponse> chunks = documentService.listChunks(id);
        return Result.ok(chunks);
    }

    /**
     * 删除文档（逻辑删除，级联删除 pgvector chunk）
     */
    @DeleteMapping("/documents/{id:[0-9]+}")
    public Result<Void> delete(@PathVariable Long id) {
        documentService.delete(id);
        return Result.ok();
    }
}
