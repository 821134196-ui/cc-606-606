package com.community.medbox.entity;

import com.community.medbox.model.TransferType;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * 每次转交记录：袋号、交出人、接收人、类型、时间、交接位置。
 * 冷藏袋交接须同时记录链上温度（coldTemp），沿袋号可追查完整流转。
 */
@Entity
@Table(name = "transfer_record")
public class TransferRecord extends BaseEntity {

    @Column(name = "bag_no", nullable = false)
    public String bagNo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public TransferType type;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "from_staff_id")
    public Staff fromStaff;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "to_staff_id")
    public Staff toStaff;

    /** 交接时所在位置/保管位置 */
    @Column(name = "location")
    public String location;

    /** 冷藏袋交接温度（℃），非冷藏为空 */
    @Column(name = "cold_temp")
    public Double coldTemp;

    /** 接收人扫码签收码（本地模拟扫码） */
    @Column(name = "scan_code")
    public String scanCode;

    @Column(name = "created_at", nullable = false)
    public LocalDateTime createdAt = LocalDateTime.now();
}
