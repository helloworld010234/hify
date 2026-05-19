package com.hify.common.web;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class PageResult<T> extends Result<T> {
    private long total;
    private long page;
    private long size;

    public PageResult() {
    }

    public PageResult(int code, String message, T data, long total, long page, long size) {
        super(code, message, data);
        this.total = total;
        this.page = page;
        this.size = size;
    }

    public static <T> PageResult<T> of(long total, long page, long size, T data) {
        return new PageResult<>(200, "success", data, total, page, size);
    }
}
