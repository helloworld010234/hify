CREATE DATABASE IF NOT EXISTS hify_refund CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE hify_refund;
CREATE TABLE IF NOT EXISTS refund_application (
    refund_id VARCHAR(32) NOT NULL PRIMARY KEY,
    order_id VARCHAR(64) NOT NULL,
    user_id VARCHAR(64),
    amount DECIMAL(18,2) NOT NULL,
    reason VARCHAR(500),
    reject_reason VARCHAR(500),
    status VARCHAR(20) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_order_id (order_id), INDEX idx_status (status), INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
