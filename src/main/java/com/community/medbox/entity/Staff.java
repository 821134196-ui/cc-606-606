package com.community.medbox.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import java.time.LocalDateTime;

/** 回收点工作人员/处置单位接收人。 */
@Entity
@Table(name = "staff")
public class Staff extends BaseEntity {

    @Column(nullable = false, unique = true)
    public String username;

    @Column(nullable = false)
    public String displayName;

    /** STAFF=社区工作人员, DISPOSAL=处置单位接收人 */
    @Column(nullable = false)
    public String role = "STAFF";

    @Column(name = "created_at")
    public LocalDateTime createdAt = LocalDateTime.now();

    public static Staff findByUsername(String username) {
        return find("username", username).firstResult();
    }
}
