package com.community.medbox.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * 冷藏药品冷链记录：封袋入柜、存放期间温度登记、取出交接均按袋号记录，
 * 要求全程 2~8℃。温度越限系统给出文字告警并在通知中体现。
 */
@Entity
@Table(name = "cold_chain_log")
public class ColdChainLog extends BaseEntity {

    @Column(name = "bag_no", nullable = false)
    public String bagNo;

    /** IN=入柜存放, CHECK=温度巡检, OUT=取出交接 */
    @Column(nullable = false)
    public String action;

    @Column(nullable = false)
    public double temperature;

    @Column(name = "fridge_no")
    public String fridgeNo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "operator_id")
    public Staff operator;

    @Column
    public Boolean alarm;

    @Column(length = 300)
    public String note;

    @Column(name = "created_at", nullable = false)
    public LocalDateTime createdAt = LocalDateTime.now();
}
