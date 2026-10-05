package com.community.medbox.entity;

import com.community.medbox.model.AppointmentStatus;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 回收预约/上门交回单。先登记预约数量，现场核对实收数量；
 * 存在差异时必须由居民确认后方可继续封袋。
 */
@Entity
@Table(name = "appointment")
public class Appointment extends BaseEntity {

    @Column(name = "appt_no", nullable = false, unique = true)
    public String apptNo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "resident_id")
    public Resident resident;

    /** 预约时申报的总件数 */
    @Column(name = "declared_qty", nullable = false)
    public int declaredQty;

    /** 现场实收总件数（核对后填写） */
    @Column(name = "received_qty")
    public Integer receivedQty;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public AppointmentStatus status = AppointmentStatus.SCHEDULED;

    /** 实收与申报存在差异时，居民确认记录（签名/确认码+文字） */
    @Column(name = "difference_confirmed")
    public Boolean differenceConfirmed;

    @Column(name = "difference_note", length = 500)
    public String differenceNote;

    @Column(name = "confirmed_at")
    public LocalDateTime confirmedAt;

    @Column(name = "created_at")
    public LocalDateTime createdAt = LocalDateTime.now();

    @OneToMany(mappedBy = "appointment", cascade = CascadeType.ALL, orphanRemoval = true)
    public List<CollectedDrug> drugs = new ArrayList<>();
}
