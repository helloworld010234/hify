package com.hify.modules.knowledge.infra.pg;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 文档分块向量数据访问（PostgreSQL / pgvector）
 * <p>
 * 使用 {@code pgvectorJdbcTemplate} 操作第二数据源，所有 SQL 均使用 pgvector 扩展语法。
 */
@Repository
@RequiredArgsConstructor
public class DocumentChunkRepository {

    @Qualifier("pgvectorJdbcTemplate")
    private final JdbcTemplate jdbcTemplate;

    /**
     * 相似度检索：按知识库查询与用户问题最相关的 Top-K 个分块
     *
     * @param kbId           知识库 ID
     * @param embeddingStr   用户问题向量的字符串表示，如 "[0.1,-0.05,...]"
     * @param topK           返回条数
     * @return 分块列表（含相似度）
     */
    public List<DocumentChunk> searchByKnowledgeBase(Long kbId, String embeddingStr, int topK) {
        String sql = """
                SELECT
                    id,
                    document_id,
                    content,
                    1 - (embedding <=> ?::vector(1024)) AS similarity
                FROM document_chunk
                WHERE knowledge_base_id = ?
                  AND deleted = 0
                ORDER BY embedding <=> ?::vector(1024)
                LIMIT ?
                """;

        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            DocumentChunk chunk = new DocumentChunk();
            chunk.setId(rs.getLong("id"));
            chunk.setDocumentId(rs.getLong("document_id"));
            chunk.setContent(rs.getString("content"));
            chunk.setSimilarity(rs.getDouble("similarity"));
            return chunk;
        }, embeddingStr, kbId, embeddingStr, topK);
    }

    /**
     * 批量插入分块（文档解析完成后一次性写入）
     *
     * @param chunks 分块列表
     */
    public void batchInsert(List<DocumentChunk> chunks) {
        String sql = """
                INSERT INTO document_chunk
                    (knowledge_base_id, document_id, chunk_index, content, embedding, token_count)
                VALUES (?, ?, ?, ?, ?::vector(1024), ?)
                """;

        jdbcTemplate.batchUpdate(sql, chunks, chunks.size(), (ps, chunk) -> {
            ps.setLong(1, chunk.getKnowledgeBaseId());
            ps.setLong(2, chunk.getDocumentId());
            ps.setInt(3, chunk.getChunkIndex());
            ps.setString(4, chunk.getContent());
            ps.setString(5, floatArrayToVectorString(chunk.getEmbedding()));
            ps.setInt(6, chunk.getTokenCount());
        });
    }

    /**
     * 按文档 ID 查询分块列表
     */
    public List<DocumentChunk> selectByDocumentId(Long documentId) {
        String sql = """
                SELECT id, chunk_index, content, token_count
                FROM document_chunk
                WHERE document_id = ? AND deleted = 0
                ORDER BY chunk_index
                """;
        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            DocumentChunk chunk = new DocumentChunk();
            chunk.setId(rs.getLong("id"));
            chunk.setChunkIndex(rs.getInt("chunk_index"));
            chunk.setContent(rs.getString("content"));
            chunk.setTokenCount(rs.getInt("token_count"));
            return chunk;
        }, documentId);
    }

    /**
     * 按文档逻辑删除（重新解析时清旧数据）
     */
    public void deleteByDocumentId(Long documentId) {
        jdbcTemplate.update(
                "UPDATE document_chunk SET deleted = 1 WHERE document_id = ?",
                documentId
        );
    }

    /**
     * 按知识库逻辑删除（级联删除知识库下的所有 chunk）
     */
    public void deleteByKnowledgeBaseId(Long kbId) {
        jdbcTemplate.update(
                "UPDATE document_chunk SET deleted = 1 WHERE knowledge_base_id = ?",
                kbId
        );
    }

    /**
     * 按知识库物理删除（知识库彻底删除时）
     */
    public void purgeByKnowledgeBaseId(Long kbId) {
        jdbcTemplate.update(
                "DELETE FROM document_chunk WHERE knowledge_base_id = ?",
                kbId
        );
    }

    // ---------- 私有工具方法 ----------

    private String floatArrayToVectorString(float[] vector) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) {
                sb.append(",");
            }
            sb.append(vector[i]);
        }
        sb.append("]");
        return sb.toString();
    }
}
