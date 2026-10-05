package com.medreturn.entity;

import com.medreturn.domain.HandoverStatus;
import com.medreturn.domain.HandoverType;
import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * 封袋交接记录。每条记录形成一条转交链：交出人 → 接收人，
 * 接收人扫码签收前为 PENDING。冷藏袋必须记录交出温度与冷链设备，
 * 温度超 2-8℃ 区间时接收人可拒收（冷链中断可追责、可沿袋号追查）。
 */
@Entity
@Table(name = "handover")
public class Handover {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "bag_id")
    private Bag bag;

    @Enumerated(EnumType.STRING)
    @Column(name = "handover_type", nullable = false, length = 16)
    private HandoverType handoverType;

    @Column(name = "from_staff_id", nullable = false)
    private Long fromStaffId;

    @Column(name = "to_staff_id", nullable = false)
    private Long toStaffId;

    /** 交出时保管位置（冷藏为冷链库位/冷藏箱编号）。 */
    @Column(name = "from_location", nullable = false, length = 64)
    private String fromLocation;

    @Column(name = "to_location", length = 64)
    private String toLocation;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private HandoverStatus status = HandoverStatus.PENDING;

    @Column(name = "transferred_at", nullable = false)
    private LocalDateTime transferredAt = LocalDateTime.now();

    @Column(name = "accepted_at")
    private LocalDateTime acceptedAt;

    /** 交出温度（冷藏袋必填）。 */
    @Column(name = "transfer_temperature")
    private Double transferTemperature;

    /** 冷链设备，如“冷藏周转箱 CC-02”。 */
    @Column(name = "cold_chain_device", length = 64)
    private String coldChainDevice;

    @Column(name = "reject_reason", length = 256)
    private String rejectReason;

    public Handover() {
    }

    public Long getId() {
        return id;
    }

    public Bag getBag() {
        return bag;
    }

    public void setBag(Bag bag) {
        this.bag = bag;
    }

    public HandoverType getHandoverType() {
        return handoverType;
    }

    public void setHandoverType(HandoverType handoverType) {
        this.handoverType = handoverType;
    }

    public Long getFromStaffId() {
        return fromStaffId;
    }

    public void setFromStaffId(Long fromStaffId) {
        this.fromStaffId = fromStaffId;
    }

    public Long getToStaffId() {
        return toStaffId;
    }

    public void setToStaffId(Long toStaffId) {
        this.toStaffId = toStaffId;
    }

    public String getFromLocation() {
        return fromLocation;
    }

    public void setFromLocation(String fromLocation) {
        this.fromLocation = fromLocation;
    }

    public String getToLocation() {
        return toLocation;
    }

    public void setToLocation(String toLocation) {
        this.toLocation = toLocation;
    }

    public HandoverStatus getStatus() {
        return status;
    }

    public void setStatus(HandoverStatus status) {
        this.status = status;
    }

    public LocalDateTime getTransferredAt() {
        return transferredAt;
    }

    public void setTransferredAt(LocalDateTime transferredAt) {
        this.transferredAt = transferredAt;
    }

    public LocalDateTime getAcceptedAt() {
        return acceptedAt;
    }

    public void setAcceptedAt(LocalDateTime acceptedAt) {
        this.acceptedAt = acceptedAt;
    }

    public Double getTransferTemperature() {
        return transferTemperature;
    }

    public void setTransferTemperature(Double transferTemperature) {
        this.transferTemperature = transferTemperature;
    }

    public String getColdChainDevice() {
        return coldChainDevice;
    }

    public void setColdChainDevice(String coldChainDevice) {
        this.coldChainDevice = coldChainDevice;
    }

    public String getRejectReason() {
        return rejectReason;
    }

    public void setRejectReason(String rejectReason) {
        this.rejectReason = rejectReason;
    }
}
