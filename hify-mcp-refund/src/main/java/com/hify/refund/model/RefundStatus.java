package com.hify.refund.model;

public enum RefundStatus {
    PENDING("待审核"), APPROVED("已批准"), REJECTED("已拒绝"),
    PROCESSING("处理中"), COMPLETED("已完成"), CANCELLED("已取消");

    private final String description;

    RefundStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
