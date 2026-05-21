-- ============================================================
-- 迁移脚本：将向量维度改为 1024
-- 适用场景：从 768 维模型切换回 text-embedding-v4
-- ============================================================

-- 1. 删除旧的 HNSW 索引（索引依赖于向量维度）
DROP INDEX IF EXISTS idx_document_chunk_embedding_hnsw;

-- 2. 修改 embedding 列维度
--    注意：若表中已有 768 维数据，ALTER TYPE 会失败。
--    请先 TRUNCATE 清空数据，或删除表重建。
ALTER TABLE document_chunk
    ALTER COLUMN embedding TYPE vector(1024);

-- 3. 重建 HNSW 索引（余弦相似度）
CREATE INDEX idx_document_chunk_embedding_hnsw
    ON document_chunk
    USING hnsw (embedding vector_cosine_ops);
