package com.hify.modules.provider.web;

import com.hify.common.web.PageResult;
import com.hify.common.web.Result;
import com.hify.modules.provider.api.ProviderService;
import com.hify.modules.provider.api.dto.request.ProviderCreateRequest;
import com.hify.modules.provider.api.dto.request.ProviderListRequest;
import com.hify.modules.provider.api.dto.request.ProviderUpdateRequest;
import com.hify.modules.provider.api.dto.response.ConnectionTestResult;
import com.hify.modules.provider.api.dto.response.ProviderDetailResponse;
import com.hify.modules.provider.api.dto.response.ProviderListResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Provider 管理 Controller
 */
@RestController
@RequestMapping("/api/v1/providers")
@RequiredArgsConstructor
public class ProviderController {

    private final ProviderService providerService;

    /**
     * 创建供应商
     */
    @PostMapping
    public Result<Long> create(@Valid @RequestBody ProviderCreateRequest request) {
        Long id = providerService.create(request);
        return Result.ok(id);
    }

    /**
     * 分页查询供应商列表
     */
    @GetMapping
    public Result<PageResult<ProviderListResponse>> list(ProviderListRequest request) {
        PageResult<ProviderListResponse> pageResult = providerService.list(request);
        return Result.ok(pageResult);
    }

    /**
     * 获取供应商详情（含 modelConfig 和 health）
     */
    @GetMapping("/{id}")
    public Result<ProviderDetailResponse> getById(@PathVariable Long id) {
        ProviderDetailResponse detail = providerService.getById(id);
        return Result.ok(detail);
    }

    /**
     * 更新供应商
     */
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id,
                               @Valid @RequestBody ProviderUpdateRequest request) {
        providerService.update(id, request);
        return Result.ok();
    }

    /**
     * 删除供应商（逻辑删除）
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        providerService.delete(id);
        return Result.ok();
    }

    /**
     * 连通性测试
     */
    @PostMapping("/{id}/test-connection")
    public Result<ConnectionTestResult> testConnection(@PathVariable Long id) {
        ConnectionTestResult result = providerService.testConnection(id);
        return Result.ok(result);
    }
}
