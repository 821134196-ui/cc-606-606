package com.community.medbox.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * 拆袋复核记录：封袋后确需拆袋，必须由两名工作人员共同复核，
 * 填写原因并双人签名（工号），系统留痕，不得删除。
 */
@Entity
@Table(name = "bag_reopen_record")
public class BagReopenRecord extends BaseEntity {

    @Column(name = "bag_no", nullable = false)
    public String bagNo;

    /** 主操作人 */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "operator_id")
    public Staff operator;

    /** 复核人，必须与操作人不同 */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reviewer_id")
    public Staff reviewer;

    @Column(nullable = false, length = 500)
    public String reason;

    @Column(name = "created_at", nullable = false)
    public LocalDateTime createdAt = LocalDateTime.now();
}
