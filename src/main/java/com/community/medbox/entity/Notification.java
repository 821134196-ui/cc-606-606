package com.community.medbox.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import java.time.LocalDateTime;

/** 本地模拟通知：现场扫码/封袋/交接/签收/冷链告警产生的消息，不真正发送短信。 */
@Entity
@Table(name = "notification")
public class Notification extends BaseEntity {

    /** RESIDENT / STAFF */
    @Column(nullable = false)
    public String targetType;

    /** 目标标识：居民手机号 或 工作人员用户名 */
    @Column(name = "target_id", nullable = false)
    public String targetId;

    @Column(nullable = false, length = 500)
    public String content;

    @Column(name = "is_read", nullable = false)
    public boolean read = false;

    @Column(name = "created_at", nullable = false)
    public LocalDateTime createdAt = LocalDateTime.now();
}
