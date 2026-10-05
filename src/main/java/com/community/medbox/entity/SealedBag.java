package com.community.medbox.entity;

import com.community.medbox.model.BagStatus;
import com.community.medbox.model.DrugCategory;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * 分类封袋。袋号关联：药品类别、件数、封袋人（交接起点）、保管位置。
 * 封袋后内容冻结，不能再追加药品；确需拆袋必须两人复核（见 BagReopenRecord）。
 */
@Entity
@Table(name = "sealed_bag")
public class SealedBag extends BaseEntity {

    @Column(name = "bag_no", nullable = false, unique = true)
    public String bagNo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public DrugCategory category;

    @Column(nullable = false)
    public int quantity;

    /** 封袋人（首次交接起点） */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sealed_by")
    public Staff sealedBy;

    /** 当前保管位置（冷藏袋指向冷藏柜编号） */
    @Column(name = "storage_location", nullable = false)
    public String storageLocation;

    /** 封袋时扫码生成/确认的封条码（本地模拟扫码） */
    @Column(name = "seal_code")
    public String sealCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public BagStatus status = BagStatus.SEALED;

    @Column(name = "sealed_at", nullable = false)
    public LocalDateTime sealedAt = LocalDateTime.now();

    public boolean frozen() {
        return status != BagStatus.REOPENED;
    }

    public static SealedBag findByBagNo(String bagNo) {
        return find("bagNo", bagNo).firstResult();
    }
}
