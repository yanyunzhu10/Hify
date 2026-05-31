package com.hify.modules.provider.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 供应商健康状态。
 * <p>
 * 与 t_provider 读写分离：provider 表写少读多可缓存，health 表写频繁不缓存。
 * fail_count 配合 Resilience4j 熔断器使用，连续失败超过阈值触发熔断。
 * </p>
 * <p>
 * 不继承 BaseEntity：此表无 created_at 和 deleted 字段，
 * 健康状态只保留最新快照，不做逻辑删除。
 * </p>
 */
@Getter
@Setter
@TableName("t_provider_health")
public class ProviderHealth {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联 t_provider.id，唯一索引 */
    private Long providerId;

    /** 健康状态：UP / DOWN / DEGRADED / UNKNOWN */
    private String status;

    /** 最后一次探测时间 */
    private LocalDateTime lastCheckAt;

    /** 最后一次成功时间 */
    private LocalDateTime lastSuccessAt;

    /** 连续失败次数，成功后重置为 0 */
    private Integer failCount;

    /** 最近一次调用的延迟（毫秒） */
    private Integer latencyMs;

    /** 最近一次失败的原因 */
    private String errorMessage;

    /** 更新时间 */
    @TableField(fill = FieldFill.UPDATE)
    private LocalDateTime updatedAt;
}
