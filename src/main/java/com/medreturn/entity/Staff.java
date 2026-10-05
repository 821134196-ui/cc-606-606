package com.medreturn.entity;

import com.medreturn.domain.StaffRole;
import jakarta.persistence.*;

/**
 * 工作人员。SITE=社区回收点工作人员；DISPOSAL=处置单位签收人员。
 * 双人复核即要求两名不同的在岗 SITE 工作人员共同确认。
 */
@Entity
@Table(name = "staff")
public class Staff {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String name;

    /** 工号，用于现场双人签字确认。 */
    @Column(name = "employee_no", nullable = false, unique = true, length = 32)
    private String employeeNo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private StaffRole role;

    @Column(length = 128)
    private String organization;

    public Staff() {
    }

    public Staff(String name, String employeeNo, StaffRole role, String organization) {
        this.name = name;
        this.employeeNo = employeeNo;
        this.role = role;
        this.organization = organization;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getEmployeeNo() {
        return employeeNo;
    }

    public StaffRole getRole() {
        return role;
    }

    public String getOrganization() {
        return organization;
    }
}
