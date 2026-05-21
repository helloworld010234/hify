package com.hify.modules.knowledge.domain.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;
import com.hify.common.web.PageResult;
import com.hify.modules.knowledge.api.KnowledgeBaseService;
import com.hify.modules.knowledge.api.dto.request.KnowledgeBaseCreateRequest;
import com.hify.modules.knowledge.api.dto.request.KnowledgeBaseListRequest;
import com.hify.modules.knowledge.api.dto.request.KnowledgeBaseUpdateRequest;
import com.hify.modules.knowledge.api.dto.response.KnowledgeBaseResponse;
import com.hify.modules.knowledge.infra.entity.Document;
import com.hify.modules.knowledge.infra.entity.KnowledgeBase;
import com.hify.modules.knowledge.infra.mapper.DocumentMapper;
import com.hify.modules.knowledge.infra.mapper.KnowledgeBaseMapper;
import com.hify.modules.knowledge.infra.pg.DocumentChunkRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 知识库管理 Service 实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeBaseServiceImpl implements KnowledgeBaseService {

    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final DocumentMapper documentMapper;
    private final DocumentChunkRepository documentChunkRepository;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public KnowledgeBaseResponse create(KnowledgeBaseCreateRequest request) {
        // 校验名称唯一性
        KnowledgeBase exist = knowledgeBaseMapper.selectByName(request.getName());
        if (exist != null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "知识库名称已存在：" + request.getName());
        }

        KnowledgeBase kb = new KnowledgeBase();
        kb.setName(request.getName());
        kb.setDescription(request.getDescription());
        kb.setEnabled(1);

        knowledgeBaseMapper.insert(kb);
        log.info("KnowledgeBase created: id={}, name={}", kb.getId(), kb.getName());

        return toResponse(kb);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, KnowledgeBaseUpdateRequest request) {
        KnowledgeBase kb = knowledgeBaseMapper.selectById(id);
        if (kb == null || kb.getDeleted() != null && kb.getDeleted() == 1) {
            throw new BizException(ErrorCode.NOT_FOUND, "知识库不存在：" + id);
        }

        // 校验名称唯一性（排除自己）
        KnowledgeBase exist = knowledgeBaseMapper.selectByName(request.getName());
        if (exist != null && !exist.getId().equals(id)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "知识库名称已存在：" + request.getName());
        }

        kb.setName(request.getName());
        kb.setDescription(request.getDescription());
        kb.setEnabled(request.getEnabled());

        knowledgeBaseMapper.updateById(kb);
        log.info("KnowledgeBase updated: id={}", id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        KnowledgeBase kb = knowledgeBaseMapper.selectById(id);
        if (kb == null || kb.getDeleted() != null && kb.getDeleted() == 1) {
            throw new BizException(ErrorCode.NOT_FOUND, "知识库不存在：" + id);
        }

        // 1. 逻辑删除知识库
        knowledgeBaseMapper.deleteById(id);

        // 2. 级联逻辑删除关联的 document
        documentMapper.deleteByKnowledgeBaseId(id);

        // 3. 级联逻辑删除 PostgreSQL 中的 document_chunk
        documentChunkRepository.deleteByKnowledgeBaseId(id);

        log.info("KnowledgeBase deleted: id={}", id);
    }

    @Override
    public KnowledgeBaseResponse getById(Long id) {
        KnowledgeBase kb = knowledgeBaseMapper.selectById(id);
        if (kb == null || kb.getDeleted() != null && kb.getDeleted() == 1) {
            throw new BizException(ErrorCode.NOT_FOUND, "知识库不存在：" + id);
        }
        return toResponse(kb);
    }

    @Override
    public PageResult<KnowledgeBaseResponse> list(KnowledgeBaseListRequest request) {
        Page<KnowledgeBase> page = new Page<>(request.getPage(), request.getSize());
        LambdaQueryWrapper<KnowledgeBase> wrapper = new LambdaQueryWrapper<KnowledgeBase>()
                .eq(KnowledgeBase::getDeleted, 0)
                .like(StringUtils.hasText(request.getName()), KnowledgeBase::getName, request.getName())
                .orderByDesc(KnowledgeBase::getCreatedAt);

        Page<KnowledgeBase> resultPage = knowledgeBaseMapper.selectPage(page, wrapper);

        List<KnowledgeBaseResponse> list = resultPage.getRecords().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());

        return PageResult.of(resultPage.getTotal(), request.getPage(), request.getSize(), list);
    }

    private KnowledgeBaseResponse toResponse(KnowledgeBase kb) {
        KnowledgeBaseResponse resp = new KnowledgeBaseResponse();
        resp.setId(kb.getId());
        resp.setName(kb.getName());
        resp.setDescription(kb.getDescription());
        resp.setEnabled(kb.getEnabled());

        long docCount = documentMapper.selectCount(
                new LambdaQueryWrapper<Document>()
                        .eq(Document::getKnowledgeBaseId, kb.getId())
                        .eq(Document::getDeleted, 0)
        );
        resp.setDocumentCount((int) docCount);

        resp.setCreatedAt(kb.getCreatedAt());
        resp.setUpdatedAt(kb.getUpdatedAt());
        return resp;
    }
}
