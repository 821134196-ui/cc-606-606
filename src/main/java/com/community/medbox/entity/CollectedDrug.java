package com.community.medbox.entity;

import com.community.medbox.model.DrugCategory;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;

/**
 * 现场收取的单条药品明细（内部资料，居民端不可见他人明细，
 * 居民凭证中也仅展示汇总类别与数量，不展示具体品名）。
 */
@Entity
@Table(name = "collected_drug")
public class CollectedDrug extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "appointment_id")
    public Appointment appointment;

    /** 药品名称（仅工作人员可见） */
    @Column(nullable = false)
    public String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public DrugCategory category;

    @Column(nullable = false)
    public int quantity;

    /** 封袋后写入袋号；拆袋时清空、重新封袋再写入 */
    @Column(name = "bag_no")
    public String bagNo;

    /** 拆袋时记录原袋号，用于重新封袋时精确找回原袋内容并留痕 */
    @Column(name = "reopened_from")
    public String reopenedFrom;

    /** 危险药品防护提示确认（如防泄漏、双层袋） */
    @Column(name = "safety_confirmed")
    public Boolean safetyConfirmed;
}
