package com.medreturn.service;

import com.medreturn.domain.MedicineCategory;
import com.medreturn.domain.StaffRole;
import com.medreturn.entity.*;
import com.medreturn.web.BusinessException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import java.util.List;

/** 常用查询与人员校验。 */
@ApplicationScoped
public class LookupService {

    @Inject
    EntityManager em;

    public Staff requireStaff(String employeeNo) {
        if (employeeNo == null || employeeNo.isBlank()) {
            throw BusinessException.badRequest("请填写工号");
        }
        List<Staff> list = em.createQuery("select s from Staff s where s.employeeNo = :no", Staff.class)
                .setParameter("no", employeeNo.trim())
                .getResultList();
        if (list.isEmpty()) {
            throw BusinessException.badRequest("工号不存在：" + employeeNo);
        }
        return list.get(0);
    }

    public Staff requireSiteStaff(String employeeNo) {
        Staff s = requireStaff(employeeNo);
        if (s.getRole() != StaffRole.SITE) {
            throw BusinessException.badRequest("该操作只能由社区回收点工作人员执行：" + employeeNo);
        }
        return s;
    }

    public Staff requireDisposalStaff(String employeeNo) {
        Staff s = requireStaff(employeeNo);
        if (s.getRole() != StaffRole.DISPOSAL) {
            throw BusinessException.badRequest("该操作只能由处置单位签收人员执行：" + employeeNo);
        }
        return s;
    }

    /** 双人复核：两名工作人员都必须存在、都是回收点在岗人员且不能为同一人。 */
    public void requireDistinctSitePair(Staff operator, Staff witness) {
        if (operator.getRole() != StaffRole.SITE) {
            throw BusinessException.badRequest("操作人必须是回收点工作人员");
        }
        if (witness == null) {
            throw BusinessException.badRequest("拆袋/危险药品核对必须由两名工作人员共同完成，请填写复核人工号");
        }
        if (witness.getRole() != StaffRole.SITE) {
            throw BusinessException.badRequest("复核人必须是回收点工作人员");
        }
        if (operator.getId().equals(witness.getId())) {
            throw BusinessException.conflict("双人复核要求两名不同的工作人员，操作人与复核人不能为同一人");
        }
    }

    public Appointment requireAppointment(String code) {
        List<Appointment> list = em.createQuery("select a from Appointment a join fetch a.resident "
                        + "where a.code = :code", Appointment.class)
                .setParameter("code", code)
                .getResultList();
        if (list.isEmpty()) {
            throw BusinessException.notFound("预约单不存在：" + code);
        }
        return list.get(0);
    }

    public Bag requireBag(String bagNo) {
        List<Bag> list = em.createQuery("select b from Bag b where b.bagNo = :no", Bag.class)
                .setParameter("no", bagNo)
                .getResultList();
        if (list.isEmpty()) {
            throw BusinessException.notFound("袋号不存在：" + bagNo);
        }
        return list.get(0);
    }

    public MedicineCategory parseCategory(String code) {
        if (code == null || code.isBlank()) {
            throw BusinessException.badRequest("请选择药品类别");
        }
        try {
            return MedicineCategory.valueOf(code.trim());
        } catch (IllegalArgumentException e) {
            throw BusinessException.badRequest("未知药品类别：" + code);
        }
    }

    public String staffName(Long id) {
        if (id == null) {
            return null;
        }
        Staff s = em.find(Staff.class, id);
        return s == null ? null : s.getName();
    }
}
