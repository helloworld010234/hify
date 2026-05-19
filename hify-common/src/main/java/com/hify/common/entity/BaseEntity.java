package com.hify.common.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 基础实体类
 * <p>
 * 所有业务表实体均应继承此类，统一包含：
 * <ul>
 *   <li>id：自增主键（禁用 UUID）</li>
 *   <li>createdAt / updatedAt：自动时间戳</li>
 *   <li>deleted：逻辑删除标志（MyBatis-Plus 全局配置）</li>
 * </ul>
 *
 * @see com.baomidou.mybatisplus.annotation.TableLogic
 */
@Data
public class BaseEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
