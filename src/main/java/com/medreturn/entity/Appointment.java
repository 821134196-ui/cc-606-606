package com.medreturn.entity;

import com.medreturn.domain.AppointmentStatus;
import jakarta.persistence.*;
import java.time.LocalDateTime;

/** 居民交回过期药的预约单，记录预约数量与现场实收数量的核对结果。 */
@Entity
@Table(name = "appointment")
public class Appointment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 32)
    private String code;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "resident_id")
    private Resident resident;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private AppointmentStatus status = AppointmentStatus.PENDING;

    @Column(name = "scheduled_at", nullable = false)
    private LocalDateTime scheduledAt;

    /** 预约与实收存在差异时，须由居民本人确认；未确认不能继续封袋。 */
    @Column(name = "difference_confirmed")
    private Boolean differenceConfirmed;

    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    /** 差异说明（现场记录的差异原因，居民确认内容）。 */
    @Column(name = "difference_note", length = 256)
    private String differenceNote;

    @Column(name = "received_by_staff_id")
    private Long receivedByStaffId;

    @Column(name = "received_at")
    private LocalDateTime receivedAt;

    public Appointment() {
    }

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public Resident getResident() {
        return resident;
    }

    public void setResident(Resident resident) {
        this.resident = resident;
    }

    public AppointmentStatus getStatus() {
        return status;
    }

    public void setStatus(AppointmentStatus status) {
        this.status = status;
    }

    public LocalDateTime getScheduledAt() {
        return scheduledAt;
    }

    public void setScheduledAt(LocalDateTime scheduledAt) {
        this.scheduledAt = scheduledAt;
    }

    public Boolean getDifferenceConfirmed() {
        return differenceConfirmed;
    }

    public void setDifferenceConfirmed(Boolean differenceConfirmed) {
        this.differenceConfirmed = differenceConfirmed;
    }

    public LocalDateTime getConfirmedAt() {
        return confirmedAt;
    }

    public void setConfirmedAt(LocalDateTime confirmedAt) {
        this.confirmedAt = confirmedAt;
    }

    public String getDifferenceNote() {
        return differenceNote;
    }

    public void setDifferenceNote(String differenceNote) {
        this.differenceNote = differenceNote;
    }

    public Long getReceivedByStaffId() {
        return receivedByStaffId;
    }

    public void setReceivedByStaffId(Long receivedByStaffId) {
        this.receivedByStaffId = receivedByStaffId;
    }

    public LocalDateTime getReceivedAt() {
        return receivedAt;
    }

    public void setReceivedAt(LocalDateTime receivedAt) {
        this.receivedAt = receivedAt;
    }
}
