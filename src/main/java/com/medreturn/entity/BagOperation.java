package com.medreturn.entity;

import com.medreturn.domain.BagOperationType;
import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * 封袋/拆袋操作流水。拆袋（OPEN）与重新封袋（RESEAL）
 * 必须记录两名不同工作人员（操作人 + 复核人）及原因，缺一不可。
 */
@Entity
@Table(name = "bag_operation")
public class BagOperation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "bag_id")
    private Bag bag;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private BagOperationType type;

    @Column(name = "operator_staff_id", nullable = false)
    private Long operatorStaffId;

    @Column(name = "witness_staff_id")
    private Long witnessStaffId;

    @Column(nullable = false, length = 256)
    private String reason;

    @Column(name = "operated_at", nullable = false)
    private LocalDateTime operatedAt = LocalDateTime.now();

    public BagOperation() {
    }

    public BagOperation(Bag bag, BagOperationType type, Long operatorStaffId, Long witnessStaffId,
                        String reason) {
        this.bag = bag;
        this.type = type;
        this.operatorStaffId = operatorStaffId;
        this.witnessStaffId = witnessStaffId;
        this.reason = reason;
    }

    public Long getId() {
        return id;
    }

    public Bag getBag() {
        return bag;
    }

    public BagOperationType getType() {
        return type;
    }

    public Long getOperatorStaffId() {
        return operatorStaffId;
    }

    public Long getWitnessStaffId() {
        return witnessStaffId;
    }

    public String getReason() {
        return reason;
    }

    public LocalDateTime getOperatedAt() {
        return operatedAt;
    }
}
