package com.hify.common.test;

import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 单元测试基类。
 * 所有纯单元测试继承此类，统一加载 MockitoExtension。
 */
@ExtendWith(MockitoExtension.class)
public abstract class AbstractUnitTest {
    // 公共辅助方法可在此扩展，如 Clock 固定时间辅助
}
