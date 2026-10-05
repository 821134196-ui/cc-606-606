package com.medreturn.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * 本地模拟的扫码/短信通知记录。
 * 真实环境中 channel=SMS/SCAN；此处只在本地写库，前端轮询展示，
 * 不接入任何外部服务。
 */
@Entity
@Table(name = "notification")
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 接收对象手机号（居民）或工号（工作人员）。 */
    @Column(nullable = false, length = 64)
    private String target;

    @Column(nullable = false, length = 16)
    private String channel;

    @Column(nullable = false, length = 16)
    private String audience;   // RESIDENT / STAFF

    @Column(nullable = false, length = 128)
    private String title;

    @Column(nullable = false, length = 512)
    private String content;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "is_read", nullable = false)
    private boolean read = false;

    public Notification() {
    }

    public Notification(String target, String channel, String audience, String title, String content) {
        this.target = target;
        this.channel = channel;
        this.audience = audience;
        this.title = title;
        this.content = content;
    }

    public Long getId() {
        return id;
    }

    public String getTarget() {
        return target;
    }

    public String getChannel() {
        return channel;
    }

    public String getAudience() {
        return audience;
    }

    public String getTitle() {
        return title;
    }

    public String getContent() {
        return content;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public boolean isRead() {
        return read;
    }

    public void setRead(boolean read) {
        this.read = read;
    }
}
