package com.medreturn.entity;

import com.medreturn.domain.BagStatus;
import com.medreturn.domain.MedicineCategory;
import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * 分类封袋。袋号 bagNo 全局唯一并印在袋身二维码上，
 * 关联药品类别、袋内总数量、封袋交接人、保管位置。
 * SEALED / SIGNED_OFF 状态下内容冻结，只能经双人复核拆袋变更。
 */
@Entity
@Table(name = "bag")
public class Bag {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "bag_no", nullable = false, unique = true, length = 40)
    private String bagNo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private MedicineCategory category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private BagStatus status = BagStatus.SEALED;

    /** 袋内药品总数量（最小计数单位），封袋时快照；拆袋调整后重算。 */
    @Column(name = "total_qty", nullable = false)
    private int totalQty;

    @Column(nullable = false, length = 16)
    private String unit = "盒";

    @Column(name = "sealed_at", nullable = false)
    private LocalDateTime sealedAt;

    @Column(name = "sealed_by_staff_id", nullable = false)
    private Long sealedByStaffId;

    /** 危险药品双人核对时的第二人。 */
    @Column(name = "witness_staff_id")
    private Long witnessStaffId;

    /** 当前保管位置（库位编码，如 A-常温柜-03 / COLD-冷藏柜-01）。 */
    @Column(name = "storage_location", nullable = false, length = 64)
    private String storageLocation;

    /** 冷藏袋封袋时温度（℃），必须在 2-8℃ 区间。 */
    @Column(name = "seal_temperature")
    private Double sealTemperature;

    @Column(name = "appointment_id", nullable = false)
    private Long appointmentId;

    @Column(name = "signed_off_at")
    private LocalDateTime signedOffAt;

    public Bag() {
    }

    public Long getId() {
        return id;
    }

    public String getBagNo() {
        return bagNo;
    }

    public void setBagNo(String bagNo) {
        this.bagNo = bagNo;
    }

    public MedicineCategory getCategory() {
        return category;
    }

    public void setCategory(MedicineCategory category) {
        this.category = category;
    }

    public BagStatus getStatus() {
        return status;
    }

    public void setStatus(BagStatus status) {
        this.status = status;
    }

    public int getTotalQty() {
        return totalQty;
    }

    public void setTotalQty(int totalQty) {
        this.totalQty = totalQty;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    public LocalDateTime getSealedAt() {
        return sealedAt;
    }

    public void setSealedAt(LocalDateTime sealedAt) {
        this.sealedAt = sealedAt;
    }

    public Long getSealedByStaffId() {
        return sealedByStaffId;
    }

    public void setSealedByStaffId(Long sealedByStaffId) {
        this.sealedByStaffId = sealedByStaffId;
    }

    public Long getWitnessStaffId() {
        return witnessStaffId;
    }

    public void setWitnessStaffId(Long witnessStaffId) {
        this.witnessStaffId = witnessStaffId;
    }

    public String getStorageLocation() {
        return storageLocation;
    }

    public void setStorageLocation(String storageLocation) {
        this.storageLocation = storageLocation;
    }

    public Double getSealTemperature() {
        return sealTemperature;
    }

    public void setSealTemperature(Double sealTemperature) {
        this.sealTemperature = sealTemperature;
    }

    public Long getAppointmentId() {
        return appointmentId;
    }

    public void setAppointmentId(Long appointmentId) {
        this.appointmentId = appointmentId;
    }

    public LocalDateTime getSignedOffAt() {
        return signedOffAt;
    }

    public void setSignedOffAt(LocalDateTime signedOffAt) {
        this.signedOffAt = signedOffAt;
    }
}
