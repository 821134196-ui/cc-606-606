package com.medreturn.service;

import com.medreturn.domain.BagStatus;
import com.medreturn.entity.*;
import com.medreturn.web.BusinessException;
import com.medreturn.web.dto.CommonDtos.*;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 居民自助查询。权限边界：
 * 必须同时提供“凭证编号 + 查询码”，且查询码属于该凭证对应居民，
 * 二者任一不匹配都查不到任何信息；返回内容刻意剔除工作人员、库位、
 * 交接链等内部资料。居民之间互不可见。
 */
@ApplicationScoped
public class ResidentService {

    static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    @Inject
    EntityManager em;

    @Transactional
    public ReceiptView receipt(String appointmentCode, String tokenCode) {
        if (appointmentCode == null || appointmentCode.isBlank()
                || tokenCode == null || tokenCode.isBlank()) {
            throw BusinessException.badRequest("请输入凭证编号与查询码");
        }
        var list = em.createQuery(
                        "select a from Appointment a join fetch a.resident where a.code = :c",
                        Appointment.class)
                .setParameter("c", appointmentCode.trim()).getResultList();
        if (list.isEmpty()) {
            // 不暴露“凭证编号是否存在”，统一返回凭证或查询码错误
            throw BusinessException.forbidden("凭证编号或查询码不正确");
        }
        Appointment appt = list.get(0);
        if (!appt.getResident().getTokenCode().equals(tokenCode.trim())) {
            throw BusinessException.forbidden("凭证编号或查询码不正确");
        }

        List<Bag> bags = em.createQuery("select b from Bag b where b.appointmentId = :id order by b.id",
                        Bag.class).setParameter("id", appt.getId()).getResultList();
        int plannedTotal = 0, receivedTotal = 0;
        for (MedicineItem i : items(appt.getId())) {
            plannedTotal += i.getPlannedQty();
            receivedTotal += i.receivedOrZero();
        }

        List<ReceiptBagView> bagViews = bags.stream()
                .map(b -> new ReceiptBagView(b.getBagNo(), b.getCategory().label(), b.getTotalQty(),
                        b.getUnit(), b.getStatus() == BagStatus.SIGNED_OFF,
                        b.getSignedOffAt() == null ? null : b.getSignedOffAt().format(FMT)))
                .toList();

        return new ReceiptView(appt.getCode(), appt.getResident().getName(),
                appt.getStatus().name(), appt.getScheduledAt().format(FMT),
                appt.getReceivedAt() == null ? null : appt.getReceivedAt().format(FMT),
                appt.getDifferenceConfirmed(), appt.getDifferenceNote(),
                plannedTotal, receivedTotal, bagViews);
    }

    /** 查询本人通知：同样必须通过凭证+查询码校验，按居民手机号过滤。 */
    @Transactional
    public List<NotificationView> myNotifications(String appointmentCode, String tokenCode) {
        ReceiptView r = receipt(appointmentCode, tokenCode);
        var appt = em.createQuery("select a from Appointment a join fetch a.resident where a.code = :c",
                        Appointment.class).setParameter("c", r.appointmentCode()).getResultList()
                .get(0);
        String phone = appt.getResident().getPhone();
        return em.createQuery(
                        "select n from Notification n where n.target = :t and n.audience = 'RESIDENT' "
                                + "order by n.id desc", Notification.class)
                .setParameter("t", phone).getResultList().stream()
                .map(n -> new NotificationView(n.getId(), mask(n.getTarget()), n.getChannel(),
                        n.getAudience(), n.getTitle(), n.getContent(),
                        n.getCreatedAt().format(FMT), n.isRead()))
                .toList();
    }

    private List<MedicineItem> items(Long apptId) {
        return em.createQuery("select i from MedicineItem i where i.appointment.id = :id",
                MedicineItem.class).setParameter("id", apptId).getResultList();
    }

    private String mask(String phone) {
        if (phone == null || phone.length() < 7) {
            return "***";
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }
}
