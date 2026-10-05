package com.medreturn.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** 交回过期药的居民。tokenCode 是居民查询本人凭证的查询码。 */
@Entity
@Table(name = "resident")
public class Resident {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String name;

    @Column(nullable = false, length = 32)
    private String phone;

    /** 凭证查询码（6 位），仅居民本人持有；短信中模拟发送。 */
    @Column(name = "token_code", nullable = false, unique = true, length = 16)
    private String tokenCode;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public Resident() {
    }

    public Resident(String name, String phone, String tokenCode) {
        this.name = name;
        this.phone = phone;
        this.tokenCode = tokenCode;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getTokenCode() {
        return tokenCode;
    }

    public void setTokenCode(String tokenCode) {
        this.tokenCode = tokenCode;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
