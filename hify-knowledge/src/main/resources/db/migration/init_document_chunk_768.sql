-- ============================================================
-- 初始化脚本：document_chunk 表（pgvector，768维）
-- 配套模型：tongyi-embedding-vision-flash（阿里云 DashScope 多模态）
-- ============================================================

-- 确保 pgvector 扩展已安装
CREATE EXTENSION IF NOT EXISTS vector;

-- 创建文档分块向量表
CREATE TABLE IF NOT EXISTS document_chunk (
    id              BIGSERIAL PRIMARY KEY,
    knowledge_base_id BIGINT NOT NULL,
    document_id     BIGINT NOT NULL,
    chunk_index     INT NOT NULL,
    content         TEXT NOT NULL,
    embedding       vector(768) NOT NULL,
    token_count     INT DEFAULT 0,
    deleted         INT DEFAULT 0,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- HNSW 索引（余弦相似度）
CREATE INDEX IF NOT EXISTS idx_document_chunk_embedding_hnsw
    ON document_chunk
    USING hnsw (embedding vector_cosine_ops);

-- 普通索引
CREATE INDEX IF NOT EXISTS idx_document_chunk_kb_id
    ON document_chunk (knowledge_base_id);

CREATE INDEX IF NOT EXISTS idx_document_chunk_doc_id
    ON document_chunk (document_id);
