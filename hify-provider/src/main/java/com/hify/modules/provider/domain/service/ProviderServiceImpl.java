package com.hify.modules.provider.domain.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;
import com.hify.common.web.PageResult;
import com.hify.modules.provider.api.ProviderService;
import com.hify.modules.provider.api.dto.ModelDto;
import com.hify.modules.provider.api.dto.request.ProviderCreateRequest;
import com.hify.modules.provider.api.dto.request.ProviderListRequest;
import com.hify.modules.provider.api.dto.request.ProviderUpdateRequest;
import com.hify.modules.provider.api.dto.response.ProviderDetailResponse;
import com.hify.modules.provider.api.dto.response.ProviderListResponse;
import com.hify.modules.provider.infra.entity.ModelConfig;
import com.hify.modules.provider.infra.entity.Provider;
import com.hify.modules.provider.infra.entity.ProviderHealth;
import com.hify.modules.provider.infra.mapper.ModelConfigMapper;
import com.hify.modules.provider.infra.mapper.ProviderHealthMapper;
import com.hify.modules.provider.infra.mapper.ProviderMapper;
import com.hify.modules.provider.api.dto.response.ConnectionTestResult;
import com.hify.modules.provider.domain.adapter.ProviderAdapterFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Provider 管理 Service 实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProviderServiceImpl implements ProviderService {

    private final ProviderMapper providerMapper;
    private final ModelConfigMapper modelConfigMapper;
    private final ProviderHealthMapper providerHealthMapper;
    private final ProviderAdapterFactory adapterFactory;

    // ==================== 创建 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(cacheNames = "provider-cache", allEntries = true)
    public Long create(ProviderCreateRequest request) {
        // 0. 自动推断缺失字段
        String code = request.getCode();
        if (StringUtils.isBlank(code)) {
            code = autoGenerateCode(request.getName());
            request.setCode(code);
        }
        if (StringUtils.isBlank(request.getAuthType())) {
            request.setAuthType(inferAuthType(request.getProviderType()));
        }

        // 1. 校验编码唯一性（先统一转小写再查，与入库值保持一致）
        String checkCode = request.getCode() == null ? null : request.getCode().trim().toLowerCase();
        if (providerMapper.selectByCode(checkCode) != null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "供应商编码已存在: " + checkCode);
        }

        // 2. 校验名称唯一性（排除已删除）
        Long nameCount = providerMapper.selectCount(
                new LambdaQueryWrapper<Provider>()
                        .eq(Provider::getName, request.getName())
        );
        if (nameCount > 0) {
            throw new BizException(ErrorCode.PARAM_ERROR, "供应商名称已存在: " + request.getName());
        }

        // 3. 构建实体
        Provider provider = new Provider();
        provider.setCode(request.getCode().trim().toLowerCase());
        provider.setName(request.getName());
        provider.setProviderType(request.getProviderType());
        provider.setBaseUrl(request.getBaseUrl());
        provider.setAuthType(request.getAuthType());
        // TODO: apiKey 需要通过 EncryptionService 加密后存储
        provider.setApiKey(maskApiKey(request.getApiKey()));
        provider.setAuthConfig(request.getAuthConfig());
        provider.setTimeoutMs(request.getTimeoutMs());
        provider.setMaxRetries(request.getMaxRetries());
        provider.setStatus(request.getStatus());
        provider.setFallbackProviderId(request.getFallbackProviderId());
        provider.setSortOrder(request.getSortOrder());
        provider.setRemark(request.getRemark());

        providerMapper.insert(provider);
        log.info("Provider created: id={}, code={}", provider.getId(), provider.getCode());
        return provider.getId();
    }

    // ==================== 更新 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(cacheNames = "provider-cache", allEntries = true)
    public void update(Long id, ProviderUpdateRequest request) {
        Provider provider = providerMapper.selectById(id);
        if (provider == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "供应商不存在: " + id);
        }

        // 校验名称唯一性（排除自身）
        Long nameCount = providerMapper.selectCount(
                new LambdaQueryWrapper<Provider>()
                        .eq(Provider::getName, request.getName())
                        .ne(Provider::getId, id)
        );
        if (nameCount > 0) {
            throw new BizException(ErrorCode.PARAM_ERROR, "供应商名称已存在: " + request.getName());
        }

        provider.setName(request.getName());
        provider.setBaseUrl(request.getBaseUrl());
        provider.setAuthType(request.getAuthType());
        // apiKey 为空表示不修改
        if (StringUtils.isNotBlank(request.getApiKey())) {
            // TODO: 加密后存储
            provider.setApiKey(request.getApiKey());
        }
        provider.setAuthConfig(request.getAuthConfig());
        provider.setTimeoutMs(request.getTimeoutMs());
        provider.setMaxRetries(request.getMaxRetries());
        provider.setStatus(request.getStatus());
        provider.setFallbackProviderId(request.getFallbackProviderId());
        provider.setSortOrder(request.getSortOrder());
        provider.setRemark(request.getRemark());

        providerMapper.updateById(provider);
        log.info("Provider updated: id={}", id);
    }

    // ==================== 删除 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(cacheNames = "provider-cache", allEntries = true)
    public void delete(Long id) {
        Provider provider = providerMapper.selectById(id);
        if (provider == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "供应商不存在: " + id);
        }
        providerMapper.deleteById(id);
        log.info("Provider deleted: id={}, code={}", id, provider.getCode());
    }

    // ==================== 详情（带缓存）====================

    @Override
    @Cacheable(cacheNames = "provider-cache", key = "'detail:' + #id")
    public ProviderDetailResponse getById(Long id) {
        Provider provider = providerMapper.selectById(id);
        if (provider == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "供应商不存在: " + id);
        }

        ProviderDetailResponse response = new ProviderDetailResponse();
        response.setId(provider.getId());
        response.setCode(provider.getCode());
        response.setName(provider.getName());
        response.setProviderType(provider.getProviderType());
        response.setBaseUrl(provider.getBaseUrl());
        response.setAuthType(provider.getAuthType());
        response.setApiKeyMask(maskApiKey(provider.getApiKey()));
        response.setAuthConfig(provider.getAuthConfig());
        response.setTimeoutMs(provider.getTimeoutMs());
        response.setMaxRetries(provider.getMaxRetries());
        response.setStatus(provider.getStatus());
        response.setHealthStatus(provider.getHealthStatus());
        response.setConsecutiveFailures(provider.getConsecutiveFailures());
        response.setLastCheckTime(provider.getLastCheckTime());
        response.setLastErrorMsg(provider.getLastErrorMsg());
        response.setSortOrder(provider.getSortOrder());
        response.setRemark(provider.getRemark());
        response.setCreatedAt(provider.getCreatedAt());

        // 查询关联模型列表
        List<ModelConfig> modelConfigs = modelConfigMapper.selectActiveByProviderId(id);
        response.setModelConfigs(modelConfigs);

        // 查询健康状态快照
        ProviderHealth health = providerHealthMapper.selectByProviderId(id);
        response.setProviderHealth(health);

        // 查询 fallback 供应商编码
        if (provider.getFallbackProviderId() != null) {
            Provider fallback = providerMapper.selectById(provider.getFallbackProviderId());
            if (fallback != null) {
                response.setFallbackProviderCode(fallback.getCode());
            }
        }

        return response;
    }

    // ==================== 列表（带缓存）====================

    @Override
    @Cacheable(
            cacheNames = "provider-cache",
            key = "'list:' + #request.current + ':' + #request.size + ':' + #request.providerType + ':' + #request.status + ':' + #request.keyword"
    )
    public PageResult<ProviderListResponse> list(ProviderListRequest request) {
        LambdaQueryWrapper<Provider> wrapper = new LambdaQueryWrapper<>();

        // 按协议类型筛选
        if (StringUtils.isNotBlank(request.getProviderType())) {
            wrapper.eq(Provider::getProviderType, request.getProviderType());
        }

        // 按人工状态筛选
        if (StringUtils.isNotBlank(request.getStatus())) {
            wrapper.eq(Provider::getStatus, request.getStatus());
        }

        // 按名称或编码模糊搜索
        if (StringUtils.isNotBlank(request.getKeyword())) {
            wrapper.and(w -> w.like(Provider::getName, request.getKeyword())
                    .or()
                    .like(Provider::getCode, request.getKeyword()));
        }

        wrapper.orderByAsc(Provider::getSortOrder).orderByDesc(Provider::getCreatedAt);

        Page<Provider> page = providerMapper.selectPage(
                new Page<>(request.getCurrent(), request.getSize()),
                wrapper
        );

        // 批量查询模型数量和健康状态（避免 N+1）
        List<Long> providerIds = page.getRecords().stream()
                .map(Provider::getId)
                .toList();

        // 查询各供应商下活跃模型数量
        Map<Long, Long> modelCountMap = new java.util.HashMap<>();
        if (!providerIds.isEmpty()) {
            List<ModelConfig> models = modelConfigMapper.selectList(
                    new LambdaQueryWrapper<ModelConfig>()
                            .in(ModelConfig::getProviderId, providerIds)
                            .eq(ModelConfig::getStatus, "active")
                            .eq(ModelConfig::getDeleted, 0)
            );
            modelCountMap.putAll(models.stream()
                    .collect(java.util.stream.Collectors.groupingBy(
                            ModelConfig::getProviderId,
                            java.util.stream.Collectors.counting()
                    )));
        }

        // 查询健康状态响应时间
        Map<Long, Long> responseTimeMap = new java.util.HashMap<>();
        if (!providerIds.isEmpty()) {
            List<ProviderHealth> healths = providerHealthMapper.selectList(
                    new LambdaQueryWrapper<ProviderHealth>()
                            .in(ProviderHealth::getProviderId, providerIds)
            );
            responseTimeMap.putAll(healths.stream()
                    .collect(java.util.stream.Collectors.toMap(
                            ProviderHealth::getProviderId,
                            h -> h.getResponseTimeMs() == null ? 0L : h.getResponseTimeMs(),
                            (a, b) -> a
                    )));
        }

        List<ProviderListResponse> records = page.getRecords().stream()
                .map(p -> convertToListResponse(p, modelCountMap, responseTimeMap))
                .toList();

        return PageResult.of(page.getTotal(), page.getCurrent(), page.getSize(), records);
    }

    // ==================== 连通性测试 ====================

    @Override
    @CacheEvict(cacheNames = "provider-cache", allEntries = true)
    public ConnectionTestResult testConnection(Long id) {
        Provider provider = providerMapper.selectById(id);
        if (provider == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "供应商不存在: " + id);
        }

        // 执行连通性测试（Adapter 内部读取 provider.apiKey）
        ConnectionTestResult result = adapterFactory.getAdapter(provider.getProviderType())
                .testConnection(provider);

        // 更新供应商健康状态（t_provider）
        provider.setHealthStatus(result.isSuccess() ? "healthy" : "unhealthy");
        provider.setConsecutiveFailures(
                result.isSuccess()
                        ? 0
                        : (provider.getConsecutiveFailures() == null ? 0 : provider.getConsecutiveFailures()) + 1
        );
        provider.setLastCheckTime(java.time.LocalDateTime.now());
        provider.setLastErrorMsg(result.isSuccess() ? null : result.getErrorMessage());
        providerMapper.updateById(provider);

        // 更新或插入健康快照（t_provider_health）
        ProviderHealth health = providerHealthMapper.selectByProviderId(id);
        if (health == null) {
            health = new ProviderHealth();
            health.setProviderId(id);
        }
        health.setHealthStatus(result.isSuccess() ? "healthy" : "unhealthy");
        health.setConsecutiveFailures(provider.getConsecutiveFailures());
        health.setLastCheckTime(java.time.LocalDateTime.now());
        health.setLastErrorMsg(result.getErrorMessage());
        health.setResponseTimeMs(result.getLatencyMs());

        if (health.getId() == null) {
            providerHealthMapper.insert(health);
        } else {
            providerHealthMapper.updateById(health);
        }

        // 同步远程模型列表到 t_model
        if (result.isSuccess() && result.getModels() != null && !result.getModels().isEmpty()) {
            // 软删除该供应商下旧模型
            modelConfigMapper.delete(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.hify.modules.provider.infra.entity.ModelConfig>()
                            .eq(com.hify.modules.provider.infra.entity.ModelConfig::getProviderId, id)
            );
            // 插入新模型
            int sort = 0;
            for (com.hify.modules.provider.api.dto.response.ConnectionTestResult.ModelInfo mi : result.getModels()) {
                com.hify.modules.provider.infra.entity.ModelConfig model = new com.hify.modules.provider.infra.entity.ModelConfig();
                model.setProviderId(id);
                model.setModelCode(mi.getModelCode());
                model.setModelName(mi.getModelName());
                model.setModelType("chat");
                model.setStatus("active");
                model.setSortOrder(sort++);
                modelConfigMapper.insert(model);
            }
            log.info("Provider models synced: id={}, code={}, models={}", id, provider.getCode(), result.getModels().size());
        }

        log.info("Provider health checked: id={}, code={}, success={}, latency={}ms, models={}",
                id, provider.getCode(), result.isSuccess(), result.getLatencyMs(), result.getModelCount());

        return result;
    }

    // ==================== 私有方法 ====================

    private ProviderListResponse convertToListResponse(Provider provider,
                                                        Map<Long, Long> modelCountMap,
                                                        Map<Long, Long> responseTimeMap) {
        ProviderListResponse resp = new ProviderListResponse();
        resp.setId(provider.getId());
        resp.setCode(provider.getCode());
        resp.setName(provider.getName());
        resp.setProviderType(provider.getProviderType());
        resp.setBaseUrl(provider.getBaseUrl());
        resp.setAuthType(provider.getAuthType());
        resp.setStatus(provider.getStatus());
        resp.setHealthStatus(provider.getHealthStatus());
        resp.setConsecutiveFailures(provider.getConsecutiveFailures());
        resp.setLastCheckTime(provider.getLastCheckTime());
        resp.setSortOrder(provider.getSortOrder());
        resp.setCreatedAt(provider.getCreatedAt());
        resp.setModelCount(modelCountMap.getOrDefault(provider.getId(), 0L).intValue());
        resp.setResponseTimeMs(responseTimeMap.getOrDefault(provider.getId(), 0L));
        return resp;
    }

    /**
     * API 密钥掩码：保留前 6 位和后 4 位，中间用 **** 替换
     */
    private String maskApiKey(String apiKey) {
        if (StringUtils.isBlank(apiKey) || apiKey.length() < 12) {
            return apiKey;
        }
        return apiKey.substring(0, 6) + "****" + apiKey.substring(apiKey.length() - 4);
    }

    /**
     * 根据名称自动生成供应商编码
     */
    private String autoGenerateCode(String name) {
        if (StringUtils.isBlank(name)) {
            return "provider_" + System.currentTimeMillis();
        }
        String code = name.toLowerCase()
                .replaceAll("[^a-z0-9]", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_+|_+$", "");
        if (StringUtils.isBlank(code)) {
            code = "provider";
        }
        // 若已存在则追加时间戳后缀
        if (providerMapper.selectByCode(code) != null) {
            code = code + "_" + System.currentTimeMillis();
        }
        return code;
    }

    /**
     * 根据供应商协议类型推断默认鉴权类型
     */
    private String inferAuthType(String providerType) {
        if (StringUtils.isBlank(providerType)) {
            return "bearer";
        }
        String pt = providerType.toLowerCase();
        if (pt.contains("anthropic")) {
            return "api_key";
        }
        if (pt.contains("azure")) {
            return "azure_api_key";
        }
        if (pt.contains("ollama")) {
            return "none";
        }
        return "bearer";
    }

    @Override
    public List<ProviderListResponse> listAllActiveProviders() {
        List<Provider> providers = providerMapper.selectActiveList();
        return providers.stream().map(this::convertToBasicListResponse).toList();
    }

    private ProviderListResponse convertToBasicListResponse(Provider provider) {
        ProviderListResponse resp = new ProviderListResponse();
        resp.setId(provider.getId());
        resp.setCode(provider.getCode());
        resp.setName(provider.getName());
        resp.setProviderType(provider.getProviderType());
        resp.setBaseUrl(provider.getBaseUrl());
        resp.setAuthType(provider.getAuthType());
        resp.setStatus(provider.getStatus());
        resp.setHealthStatus(provider.getHealthStatus());
        resp.setConsecutiveFailures(provider.getConsecutiveFailures());
        resp.setLastCheckTime(provider.getLastCheckTime());
        resp.setSortOrder(provider.getSortOrder());
        resp.setCreatedAt(provider.getCreatedAt());
        return resp;
    }

    @Override
    public List<ModelDto> listAllActiveModels() {
        List<ModelConfig> models = modelConfigMapper.selectAllActive();
        return models.stream().map(this::convertToModelDto).toList();
    }

    private ModelDto convertToModelDto(ModelConfig model) {
        ModelDto dto = new ModelDto();
        dto.setId(model.getId());
        dto.setProviderId(model.getProviderId());
        dto.setModelCode(model.getModelCode());
        dto.setModelName(model.getModelName());
        dto.setModelType(model.getModelType());
        dto.setMaxContextTokens(model.getMaxContextTokens());
        dto.setMaxOutputTokens(model.getMaxOutputTokens());
        dto.setSupportsStreaming(model.getSupportsStreaming() != null && model.getSupportsStreaming() == 1);
        dto.setSupportsToolCalls(model.getSupportsToolCalls() != null && model.getSupportsToolCalls() == 1);
        dto.setSupportsVision(model.getSupportsVision() != null && model.getSupportsVision() == 1);
        dto.setSupportsJsonMode(model.getSupportsJsonMode() != null && model.getSupportsJsonMode() == 1);
        dto.setStatus(model.getStatus());
        dto.setIsDefault(model.getIsDefault() != null && model.getIsDefault() == 1);
        dto.setSortOrder(model.getSortOrder());
        dto.setCreatedAt(model.getCreatedAt());
        return dto;
    }

    @Override
    public String getModelNameById(Long modelConfigId) {
        if (modelConfigId == null) {
            return null;
        }
        ModelConfig model = modelConfigMapper.selectById(modelConfigId);
        if (model == null || model.getDeleted() != null && model.getDeleted() == 1) {
            return null;
        }
        // 检查所属 Provider 是否被删除
        Provider provider = providerMapper.selectById(model.getProviderId());
        if (provider == null || provider.getDeleted() != null && provider.getDeleted() == 1) {
            return null;
        }
        return model.getModelName();
    }
}
