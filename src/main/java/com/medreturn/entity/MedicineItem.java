package com.medreturn.entity;

import com.medreturn.domain.MedicineCategory;
import jakarta.persistence.*;

/**
 * 预约/收件的明细药品。plannedQty=预约数量，receivedQty=现场实收数量；
 * 封袋时按“已实收且尚未装袋”的可用数量扣减。
 */
@Entity
@Table(name = "medicine_item")
public class MedicineItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "appointment_id")
    private Appointment appointment;

    @Column(name = "medicine_name", nullable = false, length = 128)
    private String medicineName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private MedicineCategory category;

    @Column(name = "planned_qty", nullable = false)
    private int plannedQty;

    @Column(name = "received_qty")
    private Integer receivedQty;

    @Column(nullable = false, length = 16)
    private String unit = "盒";

    public MedicineItem() {
    }

    public MedicineItem(Appointment appointment, String medicineName, MedicineCategory category,
                        int plannedQty, String unit) {
        this.appointment = appointment;
        this.medicineName = medicineName;
        this.category = category;
        this.plannedQty = plannedQty;
        this.unit = unit;
    }

    public Long getId() {
        return id;
    }

    public Appointment getAppointment() {
        return appointment;
    }

    public String getMedicineName() {
        return medicineName;
    }

    public MedicineCategory getCategory() {
        return category;
    }

    public int getPlannedQty() {
        return plannedQty;
    }

    public Integer getReceivedQty() {
        return receivedQty;
    }

    public void setReceivedQty(Integer receivedQty) {
        this.receivedQty = receivedQty;
    }

    public String getUnit() {
        return unit;
    }

    /** 实收数量（未核收时视为 0）。 */
    public int receivedOrZero() {
        return receivedQty == null ? 0 : receivedQty;
    }
}
