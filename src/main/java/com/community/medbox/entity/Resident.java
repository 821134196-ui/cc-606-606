package com.community.medbox.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import java.time.LocalDateTime;

/** 社区居民。居民端只能凭"查询码"查看本人的交回凭证。 */
@Entity
@Table(name = "resident")
public class Resident extends BaseEntity {

    @Column(nullable = false)
    public String name;

    @Column(nullable = false, unique = true)
    public String phone;

    /** 居民查询凭证（模拟短信验证码/身份证后四位），用于隔离查询权限 */
    @Column(name = "query_code", nullable = false, unique = true)
    public String queryCode;

    @Column(name = "created_at")
    public LocalDateTime createdAt = LocalDateTime.now();
}
