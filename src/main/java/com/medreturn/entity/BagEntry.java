package com.medreturn.entity;

import jakarta.persistence.*;

/**
 * 袋内明细：封袋时写入，SEALED 状态下不可变。
 * 每次拆袋调整都会同步产生 BagContentChange 留痕记录。
 */
@Entity
@Table(name = "bag_entry")
public class BagEntry {

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

    @Column(nullable = false)
    private int qty;

    @Column(nullable = false, length = 16)
    private String unit = "盒";

    public BagEntry() {
    }

    public BagEntry(Bag bag, Long medicineItemId, String medicineName, int qty, String unit) {
        this.bag = bag;
        this.medicineItemId = medicineItemId;
        this.medicineName = medicineName;
        this.qty = qty;
        this.unit = unit;
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

    public int getQty() {
        return qty;
    }

    public void setQty(int qty) {
        this.qty = qty;
    }

    public String getUnit() {
        return unit;
    }
}
