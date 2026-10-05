package com.medreturn.service;

import com.medreturn.domain.AppointmentStatus;
import com.medreturn.domain.MedicineCategory;
import com.medreturn.entity.*;
import com.medreturn.web.BusinessException;
import com.medreturn.web.dto.AppointmentDtos.*;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/** 预约登记、现场核对、差异居民确认。 */
@ApplicationScoped
public class AppointmentService {

    static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    @Inject
    EntityManager em;

    @Inject
    LookupService lookup;

    @Inject
    NotificationService notifications;

    @Transactional
    public AppointmentView create(CreateAppointmentRequest req) {
        if (req == null || req.residentName() == null || req.residentName().isBlank()
                || req.phone() == null || req.phone().isBlank()) {
            throw BusinessException.badRequest("请填写居民姓名与手机号");
        }
        if (req.items() == null || req.items().isEmpty()) {
            throw BusinessException.badRequest("请至少登记一条预约药品");
        }

        String tokenCode = generateTokenCode();
        Resident resident = new Resident(req.residentName().trim(), req.phone().trim(), tokenCode);
        em.persist(resident);

        Appointment appt = new Appointment();
        appt.setCode("YY" + System.currentTimeMillis() % 1_000_000_000L
                + ThreadLocalRandom.current().nextInt(10, 99));
        appt.setResident(resident);
        appt.setScheduledAt(LocalDateTime.now());
        appt.setStatus(AppointmentStatus.PENDING);
        em.persist(appt);

        for (ItemLine line : req.items()) {
            if (line.medicineName() == null || line.medicineName().isBlank()) {
                throw BusinessException.badRequest("药品名称不能为空");
            }
            int qty = line.plannedQty() == null ? 0 : line.plannedQty();
            if (qty <= 0) {
                throw BusinessException.badRequest("预约数量必须大于 0：" + line.medicineName());
            }
            MedicineCategory cat = MedicineCategory.valueOf(line.category());
            MedicineItem item = new MedicineItem(appt, line.medicineName().trim(), cat, qty,
                    line.unit() == null || line.unit().isBlank() ? "盒" : line.unit().trim());
            em.persist(item);
        }

        // 本地模拟短信：把凭证编号与查询码“发送”给居民
        notifications.notify(resident.getPhone(), "RESIDENT", "过期药回收预约成功",
                "您已预约交回过期药，凭证编号 " + appt.getCode()
                        + "，查询码 " + tokenCode + "。交药完成后可凭此查询处置进度。");

        em.flush();
        return view(appt.getCode());
    }

    /** 现场核对实收数量；存在差异时进入“待居民确认”状态。 */
    @Transactional
    public AppointmentView receive(String code, ReceiveRequest req) {
        Appointment appt = lookup(code);
        Staff staff = lookup.requireSiteStaff(req.staffEmployeeNo());

        List<MedicineItem> items = itemsOf(appt.getId());
        boolean anyDifference = false;
        for (ReceivedLine line : req.items()) {
            MedicineItem item = items.stream().filter(i -> i.getId().equals(line.itemId())).findFirst()
                    .orElseThrow(() -> BusinessException.badRequest("明细不属于本预约单：" + line.itemId()));
            int qty = line.receivedQty() == null ? 0 : line.receivedQty();
            if (qty < 0) {
                throw BusinessException.badRequest("实收数量不能为负数");
            }
            item.setReceivedQty(qty);
            if (qty != item.getPlannedQty()) {
                anyDifference = true;
            }
        }

        appt.setStatus(AppointmentStatus.RECEIVED);
        appt.setReceivedByStaffId(staff.getId());
        appt.setReceivedAt(LocalDateTime.now());
        if (!anyDifference) {
            appt.setDifferenceConfirmed(null);
            appt.setDifferenceNote(null);
        } else {
            // 有差异：必须请居民确认，未确认前禁止封袋
            appt.setDifferenceConfirmed(false);
            appt.setDifferenceNote("现场核对发现预约数量与实收数量不一致，待居民确认");
        }
        em.flush();
        return view(code);
    }

    /** 差异确认：只有真的存在差异时才有意义。 */
    @Transactional
    public AppointmentView confirmDifference(String code, ConfirmDifferenceRequest req) {
        Appointment appt = lookup(code);
        if (appt.getStatus() == AppointmentStatus.PENDING) {
            throw BusinessException.conflict("尚未完成现场核对，无法确认差异");
        }
        if (!hasDifference(appt.getId())) {
            appt.setDifferenceConfirmed(null);
            appt.setDifferenceNote(null);
            em.flush();
            return view(code);
        }
        if (req == null || req.confirmed() == null) {
            throw BusinessException.badRequest("请居民明确确认是否认可实收数量");
        }
        appt.setDifferenceConfirmed(req.confirmed());
        appt.setConfirmedAt(LocalDateTime.now());
        appt.setDifferenceNote(req.note() == null || req.note().isBlank()
                ? "居民已现场确认实收数量" : req.note().trim());
        em.flush();

        if (req.confirmed()) {
            notifications.notify(appt.getResident().getPhone(), "RESIDENT", "数量差异已确认",
                    "您的过期药实收数量与预约数量存在差异，您已现场确认，预约单 " + appt.getCode());
        }
        return view(code);
    }

    @Transactional
    public AppointmentView view(String code) {
        Appointment appt = lookup(code);
        List<MedicineItem> items = itemsOf(appt.getId());
        List<ItemView> itemViews = new ArrayList<>();
        int plannedTotal = 0, receivedTotal = 0, sealedTotal = 0;

        for (MedicineItem item : items) {
            plannedTotal += item.getPlannedQty();
            receivedTotal += item.receivedOrZero();
            int sealed = sealedQty(item.getId(), null);
            sealedTotal += sealed;
            MedicineCategory c = item.getCategory();
            itemViews.add(new ItemView(item.getId(), item.getMedicineName(), c.name(), c.label(),
                    c.dangerous(), c.cold(), c.handlingTip(),
                    item.getPlannedQty(), item.getReceivedQty(), sealed,
                    item.receivedOrZero() - sealed, item.getUnit()));
        }

        boolean difference = items.stream().anyMatch(i -> i.receivedOrZero() != i.getPlannedQty());
        boolean sealingAllowed = appt.getStatus() != AppointmentStatus.PENDING
                && (!difference || Boolean.TRUE.equals(appt.getDifferenceConfirmed()))
                && receivedTotal > sealedTotal;

        return new AppointmentView(appt.getId(), appt.getCode(), appt.getResident().getName(),
                appt.getStatus().name(), appt.getScheduledAt().format(FMT),
                difference, appt.getDifferenceConfirmed(), appt.getDifferenceNote(),
                sealingAllowed, itemViews, plannedTotal, receivedTotal, sealedTotal);
    }

    @Transactional
    public List<AppointmentView> list() {
        List<Appointment> all = em.createQuery(
                "select a from Appointment a join fetch a.resident order by a.id desc",
                Appointment.class).getResultList();
        return all.stream().map(a -> view(a.getCode())).toList();
    }

    // ---------- 内部工具 ----------

    /** 某药品已装入（未作废）封袋的数量合计；excludeBagId 用于拆袋调整时排除当前袋。 */
    int sealedQty(Long itemId, Long excludeBagId) {
        var q = em.createQuery(
                "select coalesce(sum(be.qty),0) from BagEntry be where be.medicineItemId = :id",
                Long.class).setParameter("id", itemId);
        long total = q.getSingleResult();
        if (excludeBagId != null) {
            long inBag = em.createQuery(
                    "select coalesce(sum(be.qty),0) from BagEntry be where be.medicineItemId = :id "
                            + "and be.bag.id = :bag", Long.class)
                    .setParameter("id", itemId).setParameter("bag", excludeBagId)
                    .getSingleResult();
            total -= inBag;
        }
        return (int) total;
    }

    boolean hasDifference(Long apptId) {
        return itemsOf(apptId).stream().anyMatch(i -> i.receivedOrZero() != i.getPlannedQty());
    }

    List<MedicineItem> itemsOf(Long apptId) {
        return em.createQuery("select i from MedicineItem i where i.appointment.id = :id order by i.id",
                        MedicineItem.class).setParameter("id", apptId).getResultList();
    }

    private Appointment lookup(String code) {
        var list = em.createQuery("select a from Appointment a join fetch a.resident where a.code = :c",
                        Appointment.class).setParameter("c", code).getResultList();
        if (list.isEmpty()) {
            throw BusinessException.notFound("预约单不存在：" + code);
        }
        return list.get(0);
    }

    private String generateTokenCode() {
        for (int attempt = 0; attempt < 10; attempt++) {
            String code = String.format("%06d", ThreadLocalRandom.current().nextInt(1_000_000));
            long exists = em.createQuery("select count(r) from Resident r where r.tokenCode = :c",
                            Long.class).setParameter("c", code).getSingleResult();
            if (exists == 0) {
                return code;
            }
        }
        throw new IllegalStateException("无法生成唯一查询码");
    }
}
