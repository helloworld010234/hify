package com.hify.common.cache;

import java.io.Serializable;

/**
 * 缓存空值标记，用于区分"缓存未命中"和"DB 中不存在该记录"。
 * 当 {@link CachePenetrationGuard} 的 loader 返回 null 时，
 * 将该标记写入 Redis（短 TTL），防止同一不存在的 key 反复穿透到 DB。
 */
public enum NullValue implements Serializable {
    INSTANCE
}
