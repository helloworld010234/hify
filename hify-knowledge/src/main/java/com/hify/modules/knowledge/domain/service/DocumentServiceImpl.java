package com.hify.modules.knowledge.domain.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;
import com.hify.common.web.PageResult;
import com.hify.modules.knowledge.api.DocumentService;
import com.hify.modules.knowledge.api.dto.request.DocumentListRequest;
import com.hify.modules.knowledge.api.dto.response.DocumentChunkResponse;
import com.hify.modules.knowledge.api.dto.response.DocumentResponse;
import com.hify.modules.knowledge.domain.dto.ChunkDTO;
import com.hify.modules.knowledge.infra.client.EmbeddingClient;
import com.hify.modules.knowledge.infra.entity.Document;
import com.hify.modules.knowledge.infra.entity.KnowledgeBase;
import com.hify.modules.knowledge.infra.mapper.DocumentMapper;
import com.hify.modules.knowledge.infra.mapper.KnowledgeBaseMapper;
import com.hify.modules.knowledge.infra.pg.DocumentChunk;
import com.hify.modules.knowledge.infra.pg.DocumentChunkRepository;
import com.hify.modules.provider.api.ProviderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.beans.factory.annotation.Value;
import java.util.concurrent.ExecutorService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 文档管理 Service 实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentServiceImpl implements DocumentService {

    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB
    private static final List<String> ALLOWED_TYPES = List.of("txt", "md", "pdf");

    // 分块策略：512 token，overlap 64 token
    private static final int CHUNK_SIZE_TOKENS = 512;
    private static final int CHUNK_OVERLAP_TOKENS = 64;

    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final DocumentMapper documentMapper;
    private final DocumentChunkRepository documentChunkRepository;
    private final EmbeddingClient embeddingClient;
    private final ProviderService providerService;
    private final ExecutorService documentParseExecutor;

    @Value("${hify.rag.upload-dir:./uploads}")
    private String uploadDir;

    @Value("${hify.rag.embedding.provider-id:}")
    private Long embeddingProviderId;

    @Value("${hify.rag.embedding.model:text-embedding-v4}")
    private String embeddingModel;

    @Value("${hify.rag.embedding.dimension:1024}")
    private int embeddingDimension;

    // ==================== 上传接口 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long upload(Long kbId, MultipartFile file) {
        // 校验知识库存在
        KnowledgeBase kb = knowledgeBaseMapper.selectById(kbId);
        if (kb == null || kb.getDeleted() != null && kb.getDeleted() == 1) {
            throw new BizException(ErrorCode.NOT_FOUND, "知识库不存在：" + kbId);
        }

        // 校验文件类型和大小
        String originalName = file.getOriginalFilename();
        String ext = getExtension(originalName).toLowerCase();
        validateFile(file, ext);

        // 保存文件到磁盘
        String storedName = UUID.randomUUID() + "." + ext;
        Path targetPath = Paths.get(uploadDir, storedName);
        try {
            Files.createDirectories(targetPath.getParent());
            Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            log.error("文件保存失败: {}", originalName, e);
            throw new BizException(ErrorCode.INTERNAL_ERROR, "文件保存失败");
        }

        // 创建数据库记录
        Document doc = new Document();
        doc.setKnowledgeBaseId(kbId);
        doc.setName(originalName);
        doc.setFileType(ext);
        doc.setFileSize(file.getSize());
        doc.setStatus("PENDING");
        doc.setChunkCount(0);
        doc.setErrorMessage("");
        documentMapper.insert(doc);

        Long documentId = doc.getId();
        String filePath = targetPath.toAbsolutePath().toString();

        // 提交异步解析任务
        documentParseExecutor.execute(() -> processDocument(documentId, kbId, filePath, ext));

        log.info("Document uploaded: id={}, name={}, kbId={}", documentId, originalName, kbId);
        return documentId;
    }

    // ==================== 管线主方法（五步串联 + 异常捕获） ====================

    /**
     * 文档处理管线：解析 → 切分 → 向量化 → 存储
     * <p>
     * 只负责串联五个环节和异常状态管理，每个环节的具体逻辑在独立方法中。
     */
    private void processDocument(Long documentId, Long kbId, String filePath, String fileType) {
        try {
            // Step 1: 状态更新为处理中
            updateStatus(documentId, "PROCESSING", null);

            // Step 2: 解析
            String text = extractText(filePath, fileType);
            if (!StringUtils.hasText(text)) {
                throw new RuntimeException("文件内容为空，可能为扫描版 PDF（暂不支持）");
            }

            // Step 3: 分块
            List<ChunkDTO> chunks = splitChunks(text);
            if (chunks.isEmpty()) {
                throw new RuntimeException("文本切分后为空");
            }

            // Step 4: 向量化
            embedChunks(chunks);

            // Step 5: 存储
            saveChunks(documentId, kbId, chunks);

            // 完成
            Document done = new Document();
            done.setId(documentId);
            done.setStatus("DONE");
            done.setChunkCount(chunks.size());
            documentMapper.updateById(done);

            log.info("Document processed: id={}, chunks={}", documentId, chunks.size());

        } catch (Throwable e) {
            log.error("Document process failed: id={}", documentId, e);
            updateStatus(documentId, "FAILED", e.getMessage());
        }
    }

    // ==================== Step 1: 状态更新 ====================

    private void updateStatus(Long documentId, String status, String errorMessage) {
        Document doc = new Document();
        doc.setId(documentId);
        doc.setStatus(status);
        if (StringUtils.hasText(errorMessage)) {
            doc.setErrorMessage(errorMessage.length() > 500 ? errorMessage.substring(0, 500) : errorMessage);
        }
        documentMapper.updateById(doc);
    }

    // ==================== Step 2: 解析 ====================

    /**
     * 从文件中提取纯文本
     *
     * @param filePath 文件磁盘路径
     * @param fileType 文件类型：txt / md / pdf
     * @return 纯文本内容
     */
    private String extractText(String filePath, String fileType) throws IOException {
        return switch (fileType) {
            case "txt", "md" -> Files.readString(Path.of(filePath));
            case "pdf" -> extractPdfText(filePath);
            default -> throw new RuntimeException("不支持的文件类型: " + fileType);
        };
    }

    private String extractPdfText(String filePath) throws IOException {
        try (PDDocument document = Loader.loadPDF(Path.of(filePath).toFile())) {
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(document);
            // 扫描版 PDF 提取文字为空，一期不支持
            if (!StringUtils.hasText(text.trim())) {
                throw new RuntimeException("PDF 无文字层，可能为扫描版（暂不支持）");
            }
            return text;
        }
    }

    // ==================== Step 3: 分块 ====================

    /**
     * 将长文本递归切分为固定 token 大小的块
     * <p>
     * 切割优先级：段落边界（

）> 句子边界（。！？）> 字符数截断
     *
     * @param text 原始文本
     * @return 分块列表
     */
    private List<ChunkDTO> splitChunks(String text) {
        List<ChunkDTO> result = new ArrayList<>();
        int chunkIndex = 0;
        int pos = 0;
        int textLen = text.length();

        while (pos < textLen) {
            // 从 pos 开始，找最佳切分点（优先保证 token 数不超过 512）
            int endPos = findChunkEnd(text, pos, CHUNK_SIZE_TOKENS);

            String content = text.substring(pos, endPos).trim();
            if (StringUtils.hasText(content)) {
                ChunkDTO chunk = new ChunkDTO();
                chunk.setChunkIndex(chunkIndex++);
                chunk.setContent(content);
                chunk.setTokenCount(estimateTokens(content));
                result.add(chunk);
            }

            // 下一块起始位置：回退 overlap token 对应的字符数
            int overlapChars = estimateCharsForTokens(text, pos, endPos, CHUNK_OVERLAP_TOKENS);
            int nextPos = endPos - overlapChars;
            // 强制推进，避免死循环
            if (nextPos <= pos) {
                nextPos = endPos;
            }
            pos = nextPos;
            if (pos >= textLen) {
                break;
            }
        }

        return result;
    }

    /**
     * 从 start 开始，找到最佳的 chunk 结束位置
     * <p>
     * 优先级：段落边界 > 句子边界 > 字符数截断
     */
    private int findChunkEnd(String text, int start, int maxTokens) {
        int textLen = text.length();
        // 先按最大 token 估算对应的字符上限（中文按 1:1，混合文本保守估计）
        int maxChars = Math.min(start + maxTokens * 2, textLen);

        // 在 [start, maxChars] 范围内找最佳边界
        int bestEnd = maxChars;

        // 1. 优先找段落边界 \n\n（从后往前找）
        for (int i = maxChars - 1; i > start; i--) {
            if (i + 1 < textLen && text.charAt(i) == '\n' && text.charAt(i + 1) == '\n') {
                int candidate = i + 2;
                if (estimateTokens(text.substring(start, candidate)) <= maxTokens) {
                    bestEnd = candidate;
                    return bestEnd;
                }
            }
        }

        // 2. 其次找句子边界（。！？；）
        for (int i = maxChars - 1; i > start; i--) {
            char c = text.charAt(i);
            if (c == '。' || c == '！' || c == '？' || c == '；' || c == '.') {
                int candidate = i + 1;
                if (estimateTokens(text.substring(start, candidate)) <= maxTokens) {
                    bestEnd = candidate;
                    return bestEnd;
                }
            }
        }

        // 3. 兜底：字符数截断
        // 如果 maxChars 处 token 超限，逐步回退
        while (maxChars > start && estimateTokens(text.substring(start, maxChars)) > maxTokens) {
            maxChars--;
        }
        bestEnd = maxChars;

        return bestEnd;
    }

    /**
     * 估算 overlap token 对应的字符数
     * <p>
     * 使用二分查找替代线性扫描，避免 OOM（减少 substring 创建次数）。
     */
    private int estimateCharsForTokens(String text, int start, int end, int targetTokens) {
        if (start >= end) return 0;

        // 找到满足 token >= targetTokens 的最大 pos（最接近 end）
        int left = start;
        int right = end;

        while (left < right) {
            int mid = left + (right - left + 1) / 2; // 上取整
            if (mid >= end) {
                right = mid - 1;
                continue;
            }
            if (estimateTokens(text.substring(mid, end)) >= targetTokens) {
                left = mid;
            } else {
                right = mid - 1;
            }
        }

        return end - left;
    }

    // ==================== Step 4: 向量化 ====================

    /**
     * 为每个 ChunkDTO 填充 embedding 字段
     * <p>
     * 复用 Provider 模块的配置获取 baseUrl 和 apiKey。
     *
     * @param chunks 分块列表（输入输出同一对象，补全 embedding）
     */
    private void embedChunks(List<ChunkDTO> chunks) {
        if (chunks.isEmpty()) {
            return;
        }

        // 从 Provider 模块获取配置
        if (embeddingProviderId == null) {
            throw new RuntimeException("未配置 Embedding Provider ID");
        }
        var provider = providerService.getById(embeddingProviderId);
        if (provider == null) {
            throw new RuntimeException("Embedding Provider 不存在: id=" + embeddingProviderId);
        }
        String baseUrl = provider.getBaseUrl();
        // 优先从环境变量读取阿里云百炼 API Key，fallback 到 Provider 表
        String apiKey = System.getenv("ALI_BAI_LIAN_API_KEY");
        if (!StringUtils.hasText(apiKey)) {
            apiKey = providerService.getApiKey(embeddingProviderId);
        }
        if (!StringUtils.hasText(apiKey)) {
            throw new RuntimeException("Embedding API Key 为空，请配置环境变量 ALI_BAI_LIAN_API_KEY 或 Provider 表");
        }

        // 提取文本列表
        List<String> texts = chunks.stream()
                .map(ChunkDTO::getContent)
                .collect(Collectors.toList());

        // 批量调用 Embedding API
        List<float[]> embeddings = embeddingClient.embedBatch(texts, baseUrl, apiKey, embeddingModel);

        // 回填到 ChunkDTO（embedBatch 已保证返回顺序与输入一致）
        for (int i = 0; i < chunks.size(); i++) {
            chunks.get(i).setEmbedding(embeddings.get(i));
        }
    }

    // ==================== Step 5: 存储 ====================

    /**
     * 将分块批量写入 pgvector，同时更新 document 记录
     */
    private void saveChunks(Long documentId, Long kbId, List<ChunkDTO> chunks) {
        List<DocumentChunk> chunkList = new ArrayList<>();
        for (ChunkDTO dto : chunks) {
            DocumentChunk chunk = new DocumentChunk();
            chunk.setKnowledgeBaseId(kbId);
            chunk.setDocumentId(documentId);
            chunk.setChunkIndex(dto.getChunkIndex());
            chunk.setContent(dto.getContent());
            chunk.setEmbedding(dto.getEmbedding());
            chunk.setTokenCount(dto.getTokenCount());
            chunkList.add(chunk);
        }
        documentChunkRepository.batchInsert(chunkList);
    }

    // ==================== Token 估算 ====================

    /**
     * 简单 Token 估算：中文字符 ≈ 1 token，英文单词 ≈ 1 token
     */
    private int estimateTokens(String text) {
        if (!StringUtils.hasText(text)) {
            return 0;
        }
        int chineseCount = 0;
        int englishWordCount = 0;
        StringBuilder word = new StringBuilder();

        for (char c : text.toCharArray()) {
            if (isChinese(c)) {
                chineseCount++;
                if (word.length() > 0) {
                    englishWordCount++;
                    word.setLength(0);
                }
            } else if (Character.isLetterOrDigit(c)) {
                word.append(c);
            } else {
                if (word.length() > 0) {
                    englishWordCount++;
                    word.setLength(0);
                }
            }
        }
        if (word.length() > 0) {
            englishWordCount++;
        }
        return chineseCount + englishWordCount;
    }

    private boolean isChinese(char c) {
        Character.UnicodeBlock ub = Character.UnicodeBlock.of(c);
        return ub == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
                || ub == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS
                || ub == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A;
    }

    // ==================== 查询接口 ====================

    @Override
    public PageResult<DocumentResponse> list(Long kbId, DocumentListRequest request) {
        Page<Document> page = new Page<>(request.getPage(), request.getSize());
        LambdaQueryWrapper<Document> wrapper = new LambdaQueryWrapper<Document>()
                .eq(Document::getKnowledgeBaseId, kbId)
                .eq(Document::getDeleted, 0)
                .orderByDesc(Document::getCreatedAt);

        Page<Document> resultPage = documentMapper.selectPage(page, wrapper);

        List<DocumentResponse> list = resultPage.getRecords().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());

        return PageResult.of(resultPage.getTotal(), request.getPage(), request.getSize(), list);
    }

    @Override
    public DocumentResponse getById(Long id) {
        Document doc = documentMapper.selectById(id);
        if (doc == null || doc.getDeleted() != null && doc.getDeleted() == 1) {
            throw new BizException(ErrorCode.NOT_FOUND, "文档不存在：" + id);
        }
        return toResponse(doc);
    }

    @Override
    public List<DocumentChunkResponse> listChunks(Long id) {
        Document doc = documentMapper.selectById(id);
        if (doc == null || doc.getDeleted() != null && doc.getDeleted() == 1) {
            throw new BizException(ErrorCode.NOT_FOUND, "文档不存在：" + id);
        }

        List<DocumentChunk> chunks = documentChunkRepository.selectByDocumentId(id);
        return chunks.stream().map(chunk -> {
            DocumentChunkResponse resp = new DocumentChunkResponse();
            resp.setId(chunk.getId());
            resp.setChunkIndex(chunk.getChunkIndex());
            resp.setContent(chunk.getContent());
            resp.setTokenCount(chunk.getTokenCount());
            return resp;
        }).collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        Document doc = documentMapper.selectById(id);
        if (doc == null || doc.getDeleted() != null && doc.getDeleted() == 1) {
            throw new BizException(ErrorCode.NOT_FOUND, "文档不存在：" + id);
        }

        documentMapper.deleteById(id);
        documentChunkRepository.deleteByDocumentId(id);

        log.info("Document deleted: id={}", id);
    }

    // ==================== 工具方法 ====================

    private void validateFile(MultipartFile file, String ext) {
        if (!ALLOWED_TYPES.contains(ext)) {
            throw new BizException(ErrorCode.PARAM_ERROR,
                    "不支持的文件类型：" + ext + "，仅支持：txt、md、pdf");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BizException(ErrorCode.PARAM_ERROR, "文件大小超过限制：最大 10MB");
        }
    }

    private DocumentResponse toResponse(Document doc) {
        DocumentResponse resp = new DocumentResponse();
        resp.setId(doc.getId());
        resp.setKnowledgeBaseId(doc.getKnowledgeBaseId());
        resp.setName(doc.getName());
        resp.setFileType(doc.getFileType());
        resp.setFileSize(doc.getFileSize());
        resp.setStatus(doc.getStatus());
        resp.setChunkCount(doc.getChunkCount());
        resp.setErrorMessage(doc.getErrorMessage());
        resp.setCreatedAt(doc.getCreatedAt());
        resp.setUpdatedAt(doc.getUpdatedAt());
        return resp;
    }

    private String getExtension(String filename) {
        if (filename == null || filename.lastIndexOf('.') == -1) {
            return "";
        }
        return filename.substring(filename.lastIndexOf('.') + 1);
    }
}
