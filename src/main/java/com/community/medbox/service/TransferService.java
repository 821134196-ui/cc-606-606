package com.community.medbox.service;

import com.community.medbox.dto.Requests.HandoverRequest;
import com.community.medbox.dto.Requests.SignRequest;
import com.community.medbox.dto.Requests.TransferRequest;
import com.community.medbox.entity.*;
import com.community.medbox.model.BagStatus;
import com.community.medbox.model.TransferType;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 转交链：每次转交都记录交出人、接收人、位置、时间（与扫码签收码）。
 * - 内部保管转交：接收人为社区工作人员；冷藏袋必须登记交接温度；
 * - 移交处置单位：接收人角色须为 DISPOSAL，扫码核验；
 * - 处置单位签收：终态，沿袋号可回溯全部转交与温度记录。
 */
@ApplicationScoped
public class TransferService {

    @Inject NotificationService notifications;

    @Transactional
    public TransferRecord transfer(String bagNo, TransferRequest req) {
        SealedBag bag = BagService.requireBag(bagNo);
        if (bag.status != BagStatus.SEALED) {
            throw new BusinessException("仅「已封袋在库」的袋可转交，当前状态：" + bag.status.label);
        }
        Staff from = BagService.requireStaff(req.fromUser());
        Staff to = BagService.requireStaff(req.toUser());
        if (from.id.equals(to.id)) {
            throw new BusinessException("交出人与接收人不能是同一人");
        }
        if (!"STAFF".equals(to.role)) {
            throw new BusinessException("内部保管转交的接收人应为社区工作人员；移交处置单位请使用专用操作");
        }
        double cold = Double.NaN;
        if (bag.category.refrigerated) {
            if (req.coldTemp() == null) {
                throw new BusinessException("冷藏袋交接必须登记温度（要求2~8℃），沿袋号记录冷链");
            }
            cold = req.coldTemp();
            BagService.ensureColdRange(cold, "冷藏交接");
        }
        TransferRecord rec = save(bag, TransferType.INTERNAL, from, to,
                req.location(), cold, req.scanCode());
        bag.storageLocation = req.location() != null && !req.location().isBlank()
                ? req.location() : bag.storageLocation;

        if (bag.category.refrigerated) {
            ColdChainLog log = new ColdChainLog();
            log.bagNo = bagNo;
            log.action = "OUT";
            log.temperature = cold;
            log.fridgeNo = req.location();
            log.operator = from;
            log.alarm = false;
            log.note = "内部转交点温度登记，接收人：" + to.displayName;
            log.persist();
        }
        notifications.notifyStaff(req.toUser(),
                "【转交接收】袋 " + bagNo + "（" + bag.category.label + "）已由 " + from.displayName + " 转交给您，请核对签收。");
        return rec;
    }

    @Transactional
    public TransferRecord handover(String bagNo, HandoverRequest req) {
        SealedBag bag = BagService.requireBag(bagNo);
        if (bag.status != BagStatus.SEALED) {
            throw new BusinessException("仅在库已封袋可移交处置单位，当前状态：" + bag.status.label);
        }
        Staff from = BagService.requireStaff(req.fromUser());
        Staff disposal = BagService.requireStaff(req.disposalUser());
        if (!"DISPOSAL".equals(disposal.role)) {
            throw new BusinessException("接收人不是处置单位账户");
        }
        if (req.scanCode() == null || req.scanCode().isBlank()) {
            throw new BusinessException("移交处置单位必须扫描接收签收码");
        }
        double cold = Double.NaN;
        if (bag.category.refrigerated) {
            if (req.coldTemp() == null) {
                throw new BusinessException("冷藏袋移交必须登记交接温度（2~8℃）");
            }
            cold = req.coldTemp();
            BagService.ensureColdRange(cold, "冷藏移交处置单位");
            ColdChainLog log = new ColdChainLog();
            log.bagNo = bagNo;
            log.action = "OUT";
            log.temperature = cold;
            log.fridgeNo = req.location();
            log.operator = from;
            log.alarm = false;
            log.note = "移交处置单位温度登记";
            log.persist();
        }
        TransferRecord rec = save(bag, TransferType.HANDOVER, from, disposal,
                req.location(), cold, req.scanCode());
        bag.status = BagStatus.HANDED_OVER;
        notifications.notifyStaff(req.disposalUser(),
                "【待签收】社区已向贵单位移交袋 " + bagNo + "（" + bag.category.label + "，" + bag.quantity + " 件），请完成签收。");
        return rec;
    }

    @Transactional
    public TransferRecord sign(String bagNo, SignRequest req) {
        SealedBag bag = BagService.requireBag(bagNo);
        if (bag.status != BagStatus.HANDED_OVER) {
            throw new BusinessException("仅已移交待签收的袋可签收，当前状态：" + bag.status.label);
        }
        TransferRecord last = TransferRecord
                .find("bagNo = ?1 order by createdAt desc", bagNo).firstResult();
        if (last == null || !"DISPOSAL".equals(last.toStaff.role)
                || !last.toStaff.username.equals(req.disposalUser())) {
            throw new BusinessException("只有该袋的处置单位接收人本人可以签收");
        }
        if (req.scanCode() == null || !req.scanCode().equals(last.scanCode)) {
            throw new BusinessException("签收码与移交时扫描的签收码不一致，签收被拒绝");
        }
        TransferRecord rec = save(bag, TransferType.SIGN, last.fromStaff, last.toStaff,
                last.location, last.coldTemp, req.scanCode());
        bag.status = BagStatus.SIGNED;
        notifications.notifyStaff(last.fromStaff.username,
                "【签收完成】处置单位 " + last.toStaff.displayName + " 已签收袋 " + bagNo + "，交接闭环完成。");
        return rec;
    }

    private TransferRecord save(SealedBag bag, TransferType type, Staff from, Staff to,
                                String location, Double cold, String scanCode) {
        TransferRecord rec = new TransferRecord();
        rec.bagNo = bag.bagNo;
        rec.type = type;
        rec.fromStaff = from;
        rec.toStaff = to;
        rec.location = location;
        rec.coldTemp = Double.isNaN(cold) ? null : cold;
        rec.scanCode = scanCode;
        rec.createdAt = LocalDateTime.now();
        rec.persist();
        return rec;
    }

    public List<TransferRecord> history(String bagNo) {
        BagService.requireBag(bagNo);
        return TransferRecord.find("bagNo", bagNo).list();
    }
}
