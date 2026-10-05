package com.community.medbox.service;

import com.community.medbox.dto.Requests.ReopenRequest;
import com.community.medbox.dto.Requests.SealRequest;
import com.community.medbox.entity.*;
import com.community.medbox.model.BagStatus;
import com.community.medbox.model.DrugCategory;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 分类封袋 / 拆袋 / 冷链：
 * - 封袋：把该预约下某类别未封袋的药品一次性封入新袋，袋号关联类别、数量、封袋人、保管位置；
 * - 封袋后药品内容冻结，不能再向已封袋追加药品，也不能修改/删除袋内明细；
 * - 确需拆袋：必须两名不同的工作人员双人复核、写明原因并留记录，拆袋后袋内药品才可调整；
 * - 冷藏袋：封袋即记录入柜温度，存放/取出温度沿袋号全程可查。
 */
@ApplicationScoped
public class BagService {

    public static final double COLD_MIN = 2.0;
    public static final double COLD_MAX = 8.0;

    @Inject ScanService scanService;
    @Inject NotificationService notifications;

    @Transactional
    public SealedBag seal(Long apptId, String staffUser, SealRequest req) {
        Appointment a = Appointment.findById(apptId);
        if (a == null) throw new BusinessException(404, "预约单不存在: " + apptId);
        Staff staff = requireStaff(staffUser);

        DrugCategory category;
        try {
            category = DrugCategory.valueOf(req.category());
        } catch (Exception e) {
            throw new BusinessException("未知药品类别: " + req.category());
        }

        List<CollectedDrug> items = CollectedDrug
                .find("appointment = ?1 and category = ?2 and bagNo is null", a, category).list();
        if (items.isEmpty()) {
            throw new BusinessException("该类别下没有待封袋的药品（已封袋的药品不能再追加进其他袋）");
        }

        String location = req.storageLocation();
        if (category.refrigerated) {
            double t = req.fridgeTemp() == null ? Double.NaN : req.fridgeTemp();
            if (Double.isNaN(t)) {
                throw new BusinessException("冷藏药品封袋必须登记入柜温度（要求 2~8℃）");
            }
            ensureColdRange(t, "封袋入柜");
            if (location == null || location.isBlank()) {
                throw new BusinessException("冷藏药品必须填写冷藏柜编号作为保管位置");
            }
        }

        SealedBag bag = new SealedBag();
        bag.bagNo = scanService.nextBagNo();
        bag.category = category;
        bag.quantity = items.stream().mapToInt(x -> x.quantity).sum();
        bag.sealedBy = staff;
        bag.storageLocation = location;
        bag.sealCode = scanService.simulateScan("SEAL");
        bag.status = BagStatus.SEALED;
        bag.sealedAt = LocalDateTime.now();
        bag.persist();

        for (CollectedDrug d : items) {
            d.bagNo = bag.bagNo;
        }

        if (category.refrigerated) {
            ColdChainLog log = new ColdChainLog();
            log.bagNo = bag.bagNo;
            log.action = "IN";
            log.temperature = req.fridgeTemp();
            log.fridgeNo = location;
            log.operator = staff;
            log.alarm = false;
            log.note = "封袋入冷藏柜";
            log.persist();
            notifications.notifyStaff(staffUser,
                    "【冷链】冷藏袋 " + bag.bagNo + " 已入柜 " + location + "，温度 " + req.fridgeTemp() + "℃（要求2~8℃）。");
        }
        if (category.hazardous) {
            notifications.notifyStaff(staffUser,
                    "【危险药品】袋 " + bag.bagNo + " 为危险药品，须单独存放、防泄漏，交接时双人核对。");
        }
        return bag;
    }

    /**
     * 拆袋：双人复核硬规则——
     * 两个工号都必须存在、不能是同一人、原因必填；全程留痕（BagReopenRecord）。
     * 只有"已封袋在库"的袋可拆；已移交/已签收的袋不可拆。
     */
    @Transactional
    public SealedBag reopen(String bagNo, ReopenRequest req) {
        SealedBag bag = requireBag(bagNo);
        if (bag.status != BagStatus.SEALED) {
            throw new BusinessException("袋当前状态为「" + bag.status.label + "」，不能拆袋（仅在库已封袋可申请拆袋）");
        }
        Staff operator = requireStaff(req.operator());
        Staff reviewer = requireStaff(req.reviewer());
        if (operator.id.equals(reviewer.id)) {
            throw new BusinessException("双人复核失败：操作人与复核人不能是同一人");
        }
        if (req.reason() == null || req.reason().isBlank()) {
            throw new BusinessException("拆袋必须填写原因并留记录");
        }

        BagReopenRecord rec = new BagReopenRecord();
        rec.bagNo = bagNo;
        rec.operator = operator;
        rec.reviewer = reviewer;
        rec.reason = req.reason();
        rec.persist();

        // 拆袋：解除内容冻结，袋内药品变为未封袋，可调整后重新封袋
        for (CollectedDrug d : CollectedDrug.<CollectedDrug>list("bagNo", bagNo)) {
            d.bagNo = null;
            d.reopenedFrom = bagNo;
        }
        bag.status = BagStatus.REOPENED;
        notifications.notifyStaff(req.reviewer(),
                "【拆袋复核】" + operator.displayName + " 申请拆开 " + bagNo + "，请确认您已现场复核：" + req.reason());
        return bag;
    }

    /** 拆袋调整后重新封袋：生成新袋号、新封条，原拆袋记录与旧袋号保留可查。 */
    @Transactional
    public SealedBag reseal(String oldBagNo, String staffUser, String storageLocation, Double fridgeTemp) {
        SealedBag old = requireBag(oldBagNo);
        if (old.status != BagStatus.REOPENED) {
            throw new BusinessException("只有已拆袋待处理的袋可以重新封袋");
        }
        Staff staff = requireStaff(staffUser);
        // 精确找回：本次从该袋拆出的散药（reopenedFrom 记录原袋号）
        List<CollectedDrug> items = CollectedDrug.list("reopenedFrom", oldBagNo);
        if (items.isEmpty()) {
            throw new BusinessException("没有可重新封入的药品");
        }
        if (old.category.refrigerated) {
            if (fridgeTemp == null) throw new BusinessException("冷藏袋重新封袋必须登记温度（2~8℃）");
            ensureColdRange(fridgeTemp, "重新封袋入柜");
        }

        SealedBag bag = new SealedBag();
        bag.bagNo = scanService.nextBagNo();
        bag.category = old.category;
        bag.quantity = items.stream().mapToInt(x -> x.quantity).sum();
        bag.sealedBy = staff;
        bag.storageLocation = storageLocation != null && !storageLocation.isBlank() ? storageLocation : old.storageLocation;
        bag.sealCode = scanService.simulateScan("SEAL");
        bag.status = BagStatus.SEALED;
        bag.sealedAt = LocalDateTime.now();
        bag.persist();
        for (CollectedDrug d : items) {
            d.bagNo = bag.bagNo;
            d.reopenedFrom = null;
        }
        old.status = BagStatus.SUPERSEDED;
        notifications.notifyStaff(staffUser,
                "【重新封袋】原袋 " + oldBagNo + " 已重新封装为 " + bag.bagNo + "，拆袋记录保留备查。");
        return bag;
    }

    /** 允许工作人员在拆袋后调整（增/删/改数量）散药——仅此状态可改。 */
    @Transactional
    public CollectedDrug adjustAfterReopen(String oldBagNo, String name, DrugCategory category, int quantity) {
        SealedBag bag = requireBag(oldBagNo);
        if (bag.status != BagStatus.REOPENED) {
            throw new BusinessException("仅已拆袋待处理状态可调整内容；封袋后的内容不得随意改动");
        }
        CollectedDrug d = new CollectedDrug();
        CollectedDrug first = CollectedDrug.<CollectedDrug>find("reopenedFrom", oldBagNo).firstResult();
        if (first == null) throw new BusinessException("拆袋记录下找不到药品来源");
        d.appointment = first.appointment;
        d.name = name;
        d.category = category;
        d.quantity = quantity;
        d.reopenedFrom = oldBagNo;
        d.persist();
        return d;
    }

    /** 冷链温度登记：入柜/巡检/取出，越限文字告警。 */
    @Transactional
    public ColdChainLog logCold(String bagNo, com.community.medbox.dto.Requests.ColdLogRequest req) {
        SealedBag bag = requireBag(bagNo);
        if (!bag.category.refrigerated) {
            throw new BusinessException("袋 " + bagNo + " 不是冷藏药品，不能登记冷链温度");
        }
        Staff operator = requireStaff(req.operator());
        boolean alarm = req.temperature() < COLD_MIN || req.temperature() > COLD_MAX;
        ColdChainLog log = new ColdChainLog();
        log.bagNo = bagNo;
        log.action = req.action();
        log.temperature = req.temperature();
        log.fridgeNo = req.fridgeNo() != null ? req.fridgeNo() : bag.storageLocation;
        log.operator = operator;
        log.alarm = alarm;
        log.note = (alarm
                ? "⚠ 温度越限！要求2~8℃，实测" + req.temperature() + "℃，立即转移并报告；"
                : "温度正常（2~8℃）；") + (req.note() != null ? req.note() : "");
        log.persist();
        if (alarm) {
            notifications.notifyStaff(req.operator(),
                    "【冷链告警】冷藏袋 " + bagNo + " 实测 " + req.temperature() + "℃，超出2~8℃范围，请立即处置！");
        }
        return log;
    }

    public static void ensureColdRange(double t, String phase) {
        if (t < COLD_MIN || t > COLD_MAX) {
            throw new BusinessException("冷藏温度 " + t + "℃ 越限（要求2~8℃），" + phase + "被拒绝");
        }
    }

    public static Staff requireStaff(String username) {
        Staff s = Staff.findByUsername(username);
        if (s == null) throw new BusinessException(404, "工作人员不存在: " + username);
        return s;
    }

    public static SealedBag requireBag(String bagNo) {
        SealedBag b = SealedBag.findByBagNo(bagNo);
        if (b == null) throw new BusinessException(404, "袋号不存在: " + bagNo);
        return b;
    }
}
