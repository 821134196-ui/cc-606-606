package com.community.medbox.service;

import com.community.medbox.dto.Requests.*;
import com.community.medbox.entity.*;
import com.community.medbox.model.AppointmentStatus;
import com.community.medbox.model.DrugCategory;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 预约核对与现场收药：
 * 1) 工作人员先核对预约数量与实收数量；
 * 2) 存在差异时必须请居民确认（凭本人查询码）后方可继续；
 * 3) 收药登记在"已核对/差异已确认"之后；危险药品须确认防护措施。
 */
@ApplicationScoped
public class AppointmentService {

    @Inject NotificationService notifications;

    @Transactional
    public Appointment create(AppointmentRequest req) {
        Resident r = Resident.find("phone", req.phone()).firstResult();
        if (r == null) {
            r = new Resident();
            r.name = req.residentName();
            r.phone = req.phone();
            r.queryCode = req.queryCode() != null && !req.queryCode().isBlank()
                    ? req.queryCode() : "RC" + (1000 + (int) (System.nanoTime() % 9000));
            r.persist();
        }
        Appointment a = new Appointment();
        a.apptNo = "A-" + System.nanoTime() % 10_000_000L;
        a.resident = r;
        a.declaredQty = req.declaredQty();
        a.status = AppointmentStatus.SCHEDULED;
        a.persist();
        return a;
    }

    /** 现场核对实收数量。一致则直接通过；不一致置为"待居民确认差异"。 */
    @Transactional
    public Appointment check(Long id, CheckRequest req) {
        Appointment a = mustGet(id);
        a.receivedQty = req.receivedQty();
        if (req.receivedQty() == a.declaredQty) {
            a.differenceConfirmed = Boolean.TRUE;
            a.differenceNote = "实收与预约一致";
            a.confirmedAt = LocalDateTime.now();
            a.status = AppointmentStatus.CHECKED;
        } else {
            a.differenceConfirmed = Boolean.FALSE;
            a.differenceNote = "差异 " + (req.receivedQty() - a.declaredQty) + " 件，待居民确认";
            a.status = AppointmentStatus.SCHEDULED;
        }
        return a;
    }

    /** 差异确认：必须由居民本人凭查询码确认，工作人员不能代确认。 */
    @Transactional
    public Appointment confirmDifference(Long id, ConfirmDifferenceRequest req) {
        Appointment a = mustGet(id);
        if (a.receivedQty == null) {
            throw new BusinessException("请先完成实收数量核对");
        }
        if (a.receivedQty == a.declaredQty) {
            throw new BusinessException("实收与预约一致，无需差异确认");
        }
        if (!a.resident.queryCode.equals(req.queryCode())) {
            throw new BusinessException(403, "查询码不正确，居民身份核验失败");
        }
        a.differenceConfirmed = Boolean.TRUE;
        a.differenceNote = "居民已确认差异（实收 " + a.receivedQty + "/" + a.declaredQty + "）"
                + (req.note() != null && !req.note().isBlank() ? "：" + req.note() : "");
        a.confirmedAt = LocalDateTime.now();
        a.status = AppointmentStatus.CHECKED;
        notifications.notifyResident(a.resident.phone,
                "【社区药箱】您的交回单 " + a.apptNo + " 数量差异已由您本人确认，实收 " + a.receivedQty + " 件。");
        return a;
    }

    /** 登记一条实收药品。封袋后的药品不再允许修改/删除（在封袋服务中冻结袋号来源）。 */
    @Transactional
    public CollectedDrug addDrug(Long apptId, DrugRequest req) {
        Appointment a = mustGet(apptId);
        ensureReadyForSealing(a);
        DrugCategory category;
        try {
            category = DrugCategory.valueOf(req.category());
        } catch (Exception e) {
            throw new BusinessException("未知药品类别: " + req.category());
        }
        if (category.hazardous && !Boolean.TRUE.equals(req.safetyConfirmed())) {
            throw new BusinessException("危险药品必须确认防护措施（防泄漏/双层封袋/单独交接）后再登记");
        }
        CollectedDrug d = new CollectedDrug();
        d.appointment = a;
        d.name = req.name();
        d.category = category;
        d.quantity = req.quantity();
        d.safetyConfirmed = category.hazardous ? Boolean.TRUE : req.safetyConfirmed();
        d.persist();
        return d;
    }

    /** 关键校验：未核对或差异未经居民确认，不能进入封袋环节。 */
    public void ensureReadyForSealing(Appointment a) {
        if (a.receivedQty == null) {
            throw new BusinessException("尚未核对实收数量，不能登记/封袋");
        }
        if (!Boolean.TRUE.equals(a.differenceConfirmed)) {
            throw new BusinessException("实收与预约数量存在差异，须居民确认后才能继续");
        }
    }

    @Transactional
    public Appointment complete(Long id) {
        Appointment a = mustGet(id);
        long unsealed = CollectedDrug.count("appointment = ?1 and bagNo is null", a);
        if (unsealed > 0) {
            throw new BusinessException("还有 " + unsealed + " 条药品未封袋，不能完成交接");
        }
        a.status = AppointmentStatus.COMPLETED;
        notifications.notifyResident(a.resident.phone,
                "【社区药箱】您的交回单 " + a.apptNo + " 已全部封袋完成，凭证可在居民端查询。");
        return a;
    }

    public Appointment mustGet(Long id) {
        Appointment a = Appointment.findById(id);
        if (a == null) throw new BusinessException(404, "预约单不存在: " + id);
        return a;
    }

    public List<Appointment> list() {
        return Appointment.list("order by createdAt desc");
    }
}
