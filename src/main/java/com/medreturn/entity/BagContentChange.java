package com.medreturn.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * 拆袋期间的内容变更留痕：逐条记录调整前/后数量、操作人。
 * 封袋状态下永远不会产生此记录（服务层直接拒绝）。
 */
@Entity
@Table(name = "bag_content_change")
public class BagContentChange {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "bag_id")
    private Bag bag;

    @Column(name = "medicine_item_id", nullable = false)
    private Long medicineItemId;

    @Column(name = "medicine_name", nullable = false, length = 128)
    private String medicineName;

    @Column(name = "qty_before", nullable = false)
    private int qtyBefore;

    @Column(name = "qty_after", nullable = false)
    private int qtyAfter;

    @Column(name = "operator_staff_id", nullable = false)
    private Long operatorStaffId;

    @Column(name = "changed_at", nullable = false)
    private LocalDateTime changedAt = LocalDateTime.now();

    public BagContentChange() {
    }

    public BagContentChange(Bag bag, Long medicineItemId, String medicineName, int qtyBefore,
                            int qtyAfter, Long operatorStaffId) {
        this.bag = bag;
        this.medicineItemId = medicineItemId;
        this.medicineName = medicineName;
        this.qtyBefore = qtyBefore;
        this.qtyAfter = qtyAfter;
        this.operatorStaffId = operatorStaffId;
    }

    public Long getId() {
        return id;
    }

    public Bag getBag() {
        return bag;
    }

    public Long getMedicineItemId() {
        return medicineItemId;
    }

    public String getMedicineName() {
        return medicineName;
    }

    public int getQtyBefore() {
        return qtyBefore;
    }

    public int getQtyAfter() {
        return qtyAfter;
    }

    public Long getOperatorStaffId() {
        return operatorStaffId;
    }

    public LocalDateTime getChangedAt() {
        return changedAt;
    }
}
