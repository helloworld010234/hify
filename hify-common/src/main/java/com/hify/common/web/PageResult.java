package com.hify.common.web;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
public class PageResult<T> extends Result<List<T>> {
    private long total;
    private long page;
    private long size;

    public PageResult() {
    }

    public PageResult(int code, String message, List<T> data, long total, long page, long size) {
        super(code, message, data);
        this.total = total;
        this.page = page;
        this.size = size;
    }

    public static <T> PageResult<T> of(long total, long page, long size, List<T> data) {
        return new PageResult<>(200, "success", data, total, page, size);
    }
}
