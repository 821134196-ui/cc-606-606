package com.medreturn.service;

import com.medreturn.domain.*;
import com.medreturn.entity.*;
import com.medreturn.web.BusinessException;
import com.medreturn.web.dto.HandoverDtos.*;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 封袋交接链。每次转交都记录交出人、接收人、起止保管位置与时间；
 * 接收人扫码签收前为待签收。冷藏袋全程记录温度与冷链设备，
 * 温度超 2-8℃ 必须拒收。处置单位签收后封袋状态变为 SIGNED_OFF。
 */
@ApplicationScoped
public class HandoverService {

    static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    @Inject
    EntityManager em;

    @Inject
    LookupService lookup;

    @Inject
    NotificationService notifications;

    @Transactional
    public HandoverView create(HandoverRequest req) {
        if (req == null) {
            throw BusinessException.badRequest("交接参数缺失");
        }
        Bag bag = lookup.requireBag(req.bagNo());
        if (bag.getStatus() == BagStatus.SIGNED_OFF) {
            throw BusinessException.conflict("袋号 " + bag.getBagNo() + " 已完成处置签收，不能再交接");
        }
        if (bag.getStatus() == BagStatus.OPENED) {
            throw BusinessException.conflict("袋号 " + bag.getBagNo() + " 处于拆袋状态，重新封袋前不能交接");
        }

        HandoverType type;
        try {
            type = HandoverType.valueOf(req.handoverType() == null ? "INTERNAL" : req.handoverType());
        } catch (IllegalArgumentException e) {
            throw BusinessException.badRequest("未知交接类型：" + req.handoverType());
        }

        Staff from = lookup.requireSiteStaff(req.fromEmployeeNo());
        Staff to = lookup.requireStaff(req.toEmployeeNo());
        if (from.getId().equals(to.getId())) {
            throw BusinessException.conflict("交出人与接收人不能为同一人");
        }
        if (type == HandoverType.TO_DISPOSAL && to.getRole() != StaffRole.DISPOSAL) {
            throw BusinessException.badRequest("送交处置单位时，接收人必须是处置单位签收人员");
        }
        if (type == HandoverType.INTERNAL && to.getRole() != StaffRole.SITE) {
            throw BusinessException.badRequest("内部保管转交的接收人必须是回收点工作人员");
        }

        String toLocation = req.toLocation();
        if (toLocation == null || toLocation.isBlank()) {
            throw BusinessException.badRequest("请填写接收保管位置");
        }
        toLocation = toLocation.trim();

        Double temperature = null;
        String coldDevice = req.coldChainDevice();
        if (bag.getCategory().cold()) {
            if (req.temperature() == null) {
                throw BusinessException.badRequest("冷藏袋交接必须测量并记录交出温度（2-8℃）");
            }
            temperature = req.temperature();
            if (temperature < 2.0 || temperature > 8.0) {
                throw BusinessException.conflict("冷链温度异常：交出温度 " + temperature
                        + "℃ 超出 2-8℃ 区间，禁止转出，请先恢复冷链并记录");
            }
            if (coldDevice == null || coldDevice.isBlank()) {
                throw BusinessException.badRequest("冷藏袋交接必须使用并登记冷藏周转箱等冷链设备");
            }
            if (!(toLocation.startsWith("COLD") || toLocation.contains("冷藏"))) {
                throw BusinessException.badRequest("冷藏袋的接收位置必须是冷链库位（COLD- 开头）");
            }
            coldDevice = coldDevice.trim();
        }

        // 存在未完成的交接链时，不允许重复发起
        long pending = em.createQuery(
                        "select count(h) from Handover h where h.bag.id = :id and h.status = :st", Long.class)
                .setParameter("id", bag.getId()).setParameter("st", HandoverStatus.PENDING)
                .getSingleResult();
        if (pending > 0) {
            throw BusinessException.conflict("该袋已有待接收人签收的交接记录，不能重复转交");
        }

        Handover h = new Handover();
        h.setBag(bag);
        h.setHandoverType(type);
        h.setFromStaffId(from.getId());
        h.setToStaffId(to.getId());
        h.setFromLocation(bag.getStorageLocation());
        h.setToLocation(toLocation);
        h.setStatus(HandoverStatus.PENDING);
        h.setTransferredAt(LocalDateTime.now());
        h.setTransferTemperature(temperature);
        h.setColdChainDevice(coldDevice);
        em.persist(h);
        em.flush();

        // 本地模拟扫码：向接收人“推送”待签收扫码提醒
        notifications.scanNotice(to.getEmployeeNo(), "待扫码签收",
                (bag.getCategory().cold() ? "【冷藏 2-8℃】" : "") + "袋号 " + bag.getBagNo()
                        + " 由 " + from.getName() + " 转交给您，请核对温度与袋身后扫码签收。交接单号 #"
                        + h.getId());
        return view(h);
    }

    /** 接收人扫码签收 / 拒收（本地模拟扫码：提交交接单号与工号）。 */
    @Transactional
    public HandoverView accept(Long handoverId, AcceptRequest req) {
        Handover h = em.find(Handover.class, handoverId);
        if (h == null) {
            throw BusinessException.notFound("交接单不存在：#" + handoverId);
        }
        if (h.getStatus() != HandoverStatus.PENDING) {
            throw BusinessException.conflict("该交接单已处理，当前状态：" + h.getStatus());
        }
        Staff receiver = lookup.requireStaff(req.employeeNo());
        if (!receiver.getId().equals(h.getToStaffId())) {
            throw BusinessException.forbidden("只有指定接收人才能扫码签收本交接单");
        }

        boolean accepted = req.accepted();
        // 冷藏袋：接收人复测温度，超出区间不得签收
        if (accepted && h.getBag().getCategory().cold() && req.temperature() != null) {
            double t = req.temperature();
            if (t < 2.0 || t > 8.0) {
                throw BusinessException.conflict("复测温度 " + t + "℃ 超出 2-8℃，冷链可能中断，"
                        + "请选择拒收并上报，不得签收");
            }
        }

        Bag bag = h.getBag();
        if (accepted) {
            h.setStatus(HandoverStatus.ACCEPTED);
            h.setAcceptedAt(LocalDateTime.now());
            bag.setStorageLocation(h.getToLocation());
            notifications.scanNotice(receiver.getEmployeeNo(), "签收成功",
                    "袋号 " + bag.getBagNo() + " 已签收，保管位置 " + h.getToLocation());

            if (h.getHandoverType() == HandoverType.TO_DISPOSAL) {
                if (receiver.getRole() != StaffRole.DISPOSAL) {
                    throw BusinessException.badRequest("处置签收必须由处置单位人员完成");
                }
                bag.setStatus(BagStatus.SIGNED_OFF);
                bag.setSignedOffAt(LocalDateTime.now());
                markAppointmentCompleteIfAllSigned(bag.getAppointmentId());

                Appointment appt = em.find(Appointment.class, bag.getAppointmentId());
                notifications.notify(appt.getResident().getPhone(), "RESIDENT",
                        "您交回的过期药已完成无害化处置",
                        "您的过期药（凭证 " + appt.getCode() + "，袋号 " + bag.getBagNo()
                                + "）已由处置单位 " + receiver.getOrganization()
                                + " 签收并进入无害化处置，感谢配合。");
            }
        } else {
            h.setStatus(HandoverStatus.REJECTED);
            h.setAcceptedAt(LocalDateTime.now());
            h.setRejectReason(req.reason() == null || req.reason().isBlank()
                    ? "接收人拒收" : req.reason().trim());
            notifications.scanNotice(receiver.getEmployeeNo(), "已拒收",
                    "袋号 " + bag.getBagNo() + " 已拒收，原因：" + h.getRejectReason()
                            + "。药品保留在原保管位置 " + h.getFromLocation());
        }
        em.flush();
        return view(h);
    }

    @Transactional
    public List<HandoverView> list(String toEmployeeNo, String status) {
        StringBuilder jpql = new StringBuilder("select h from Handover h where 1=1");
        Long toStaffId = null;
        HandoverStatus st = null;
        if (toEmployeeNo != null && !toEmployeeNo.isBlank()) {
            toStaffId = lookup.requireStaff(toEmployeeNo).getId();
            jpql.append(" and h.toStaffId = :toStaffId");
        }
        if (status != null && !status.isBlank()) {
            try {
                st = HandoverStatus.valueOf(status);
            } catch (IllegalArgumentException e) {
                throw BusinessException.badRequest("未知交接状态：" + status);
            }
            jpql.append(" and h.status = :status");
        }
        jpql.append(" order by h.id desc");
        var q = em.createQuery(jpql.toString(), Handover.class);
        if (toStaffId != null) {
            q.setParameter("toStaffId", toStaffId);
        }
        if (st != null) {
            q.setParameter("status", st);
        }
        return q.getResultList().stream().map(this::view).toList();
    }

    /** 沿袋号返回完整交接链（按时间正序）。 */
    public List<HandoverView> viewsOfBag(Bag bag) {
        return em.createQuery("select h from Handover h where h.bag.id = :id order by h.id",
                        Handover.class).setParameter("id", bag.getId()).getResultList().stream()
                .map(this::view).toList();
    }

    private void markAppointmentCompleteIfAllSigned(Long appointmentId) {
        List<Bag> bags = em.createQuery("select b from Bag b where b.appointmentId = :id", Bag.class)
                .setParameter("id", appointmentId).getResultList();
        if (!bags.isEmpty() && bags.stream().allMatch(b -> b.getStatus() == BagStatus.SIGNED_OFF)) {
            Appointment appt = em.find(Appointment.class, appointmentId);
            appt.setStatus(AppointmentStatus.COMPLETED);
        }
    }

    private HandoverView view(Handover h) {
        Bag bag = h.getBag();
        return new HandoverView(h.getId(), bag.getBagNo(), h.getHandoverType().name(),
                h.getStatus().name(), lookup.staffName(h.getFromStaffId()),
                lookup.staffName(h.getToStaffId()), h.getFromLocation(), h.getToLocation(),
                h.getTransferredAt().format(FMT),
                h.getAcceptedAt() == null ? null : h.getAcceptedAt().format(FMT),
                h.getTransferTemperature(), h.getColdChainDevice(), h.getRejectReason(),
                bag.getCategory().cold());
    }
}
