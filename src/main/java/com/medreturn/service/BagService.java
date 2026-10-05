package com.medreturn.service;

import com.medreturn.domain.*;
import com.medreturn.entity.*;
import com.medreturn.web.BusinessException;
import com.medreturn.web.dto.BagDtos.*;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 封袋与拆袋核心规则：
 * 1) 只有“已核对、差异已确认”的预约才能封袋；
 * 2) 封袋后内容冻结，任何追加/修改一律 409 拒绝；
 * 3) 确需拆袋必须两名不同工作人员复核，拆袋与重封均留痕，内容逐条变更留痕；
 * 4) 危险药品封袋即双人核对、专柜加锁；冷藏药品 2-8℃、冷链库位、记录温度。
 */
@ApplicationScoped
public class BagService {

    static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    static final double COLD_MIN = 2.0;
    static final double COLD_MAX = 8.0;

    @Inject
    EntityManager em;

    @Inject
    LookupService lookup;

    @Inject
    HandoverService handoverService;

    @Inject
    NotificationService notifications;

    @Transactional
    public BagView seal(SealBagRequest req) {
        if (req == null) {
            throw BusinessException.badRequest("封袋参数缺失");
        }
        Appointment appt = lookup.requireAppointment(req.appointmentCode());
        if (appt.getStatus() == AppointmentStatus.PENDING) {
            throw BusinessException.conflict("预约单尚未完成现场核对，不能封袋：" + appt.getCode());
        }
        List<MedicineItem> items = itemsOf(appt.getId());
        boolean difference = items.stream().anyMatch(i -> i.receivedOrZero() != i.getPlannedQty());
        if (difference && !Boolean.TRUE.equals(appt.getDifferenceConfirmed())) {
            throw BusinessException.conflict("预约与实收数量存在差异，必须先请居民确认后才能封袋");
        }
        if (req.entries() == null || req.entries().isEmpty()) {
            throw BusinessException.badRequest("请填写需要装入本袋的药品及数量");
        }

        MedicineCategory category = lookup.parseCategory(req.category());
        Staff operator = lookup.requireSiteStaff(req.staffEmployeeNo());

        // 危险药品必须双人核对
        Staff witness = null;
        if (category.dangerous()) {
            if (req.witnessEmployeeNo() == null || req.witnessEmployeeNo().isBlank()) {
                throw BusinessException.badRequest(
                        "危险药品必须双人核对，请填写第二名工作人员（复核人）工号");
            }
            witness = lookup.requireSiteStaff(req.witnessEmployeeNo());
            lookup.requireDistinctSitePair(operator, witness);
        }

        // 冷藏药品：温度 2-8℃ + 冷链库位
        Double temperature = null;
        String location = req.storageLocation();
        if (location == null || location.isBlank()) {
            throw BusinessException.badRequest("请填写保管位置（库位编码）");
        }
        location = location.trim();
        if (category.cold()) {
            if (req.temperature() == null) {
                throw BusinessException.badRequest("冷藏药品封袋必须测量并记录温度（2-8℃）");
            }
            temperature = req.temperature();
            requireColdTemperature(temperature, "封袋");
            if (!isColdLocation(location)) {
                throw BusinessException.badRequest(
                        "冷藏药品必须存入冷链库位（库位需以 COLD- 开头，如 COLD-冷藏柜-01）");
            }
        } else if (isColdLocation(location)) {
            throw BusinessException.badRequest("非冷藏药品不能放入冷链库位");
        }

        // 校验每条明细：属于本预约、类别一致、数量不超过可用实收
        Map<MedicineItem, Integer> toSeal = new HashMap<>();
        int total = 0;
        String unit = "盒";
        for (SealEntryRequest line : req.entries()) {
            int qty = line.qty() == null ? 0 : line.qty();
            if (qty <= 0) {
                throw BusinessException.badRequest("装袋数量必须大于 0（封袋后无法追加，请一次装足）");
            }
            MedicineItem item = items.stream().filter(i -> i.getId().equals(line.itemId())).findFirst()
                    .orElseThrow(() -> BusinessException.badRequest("明细不属于本预约单：" + line.itemId()));
            if (item.getCategory() != category) {
                throw BusinessException.badRequest(
                        "同一封袋只能装入同一类别：「" + item.getMedicineName() + "」属于 "
                                + item.getCategory().label() + "，不能装入 " + category.label() + " 袋");
            }
            int sealedElsewhere = sealedQty(item.getId(), null);
            int available = item.receivedOrZero() - sealedElsewhere;
            if (qty > available) {
                throw BusinessException.conflict(
                        "「" + item.getMedicineName() + "」可封袋数量不足：实收 "
                                + item.receivedOrZero() + " " + item.getUnit() + "，已封袋 "
                                + sealedElsewhere + "，本次申请 " + qty + "，可用仅 " + available);
            }
            toSeal.merge(item, qty, Integer::sum);
            total += qty;
            unit = item.getUnit();
        }

        Bag bag = new Bag();
        bag.setBagNo(generateBagNo(category));
        bag.setCategory(category);
        bag.setStatus(BagStatus.SEALED);
        bag.setTotalQty(total);
        bag.setUnit(unit);
        bag.setSealedAt(LocalDateTime.now());
        bag.setSealedByStaffId(operator.getId());
        bag.setWitnessStaffId(witness == null ? null : witness.getId());
        bag.setStorageLocation(location);
        bag.setSealTemperature(temperature);
        bag.setAppointmentId(appt.getId());
        em.persist(bag);

        for (Map.Entry<MedicineItem, Integer> e : toSeal.entrySet()) {
            MedicineItem item = e.getKey();
            em.persist(new BagEntry(bag, item.getId(), item.getMedicineName(), e.getValue(),
                    item.getUnit()));
        }
        em.persist(new BagOperation(bag, BagOperationType.SEAL, operator.getId(),
                witness == null ? null : witness.getId(),
                category.dangerous() ? "危险药品双人核对封袋" : "分类封袋"));

        String warn = category.dangerous() ? "（危险药品·双人核对·专柜加锁）"
                : category.cold() ? "（冷藏 2-8℃·冷链库位）" : "";
        notifications.scanNotice(operator.getEmployeeNo(), "封袋完成",
                "袋号 " + bag.getBagNo() + " 已封袋" + warn + "，共 " + total + " " + unit
                        + "，保管位置 " + location + "。封袋后禁止追加药品。");
        em.flush();
        return bagView(bag);
    }

    /**
     * 封袋后追加药品：SEALED / SIGNED_OFF 状态一律拒绝（409）。
     * 只有经过双人复核拆袋（OPENED）后，才允许通过 adjustEntry 变更。
     */
    @Transactional
    public BagView appendEntry(String bagNo, Long itemId, int qty, String operatorEmployeeNo) {
        Bag bag = lookup.requireBag(bagNo);
        if (bag.getStatus() != BagStatus.OPENED) {
            throw BusinessException.conflict(
                    "袋号 " + bagNo + " 已封袋，禁止追加或改动药品；确需变更须先由两名工作人员复核拆袋");
        }
        return adjustEntry(bagNo, new AdjustEntryRequest(operatorEmployeeNo, itemId, qty));
    }

    /** 双人复核拆袋。 */
    @Transactional
    public BagView open(OpenBagRequest req, String bagNo) {
        Bag bag = lookup.requireBag(bagNo);
        if (bag.getStatus() == BagStatus.SIGNED_OFF) {
            throw BusinessException.conflict("袋号 " + bagNo + " 已被处置单位签收，不得拆袋");
        }
        if (bag.getStatus() == BagStatus.OPENED) {
            throw BusinessException.conflict("袋号 " + bagNo + " 已处于拆袋状态");
        }
        Staff operator = lookup.requireSiteStaff(req.operatorEmployeeNo());
        Staff witness = req.witnessEmployeeNo() == null ? null
                : lookup.requireSiteStaff(req.witnessEmployeeNo());
        lookup.requireDistinctSitePair(operator, witness);
        if (req.reason() == null || req.reason().isBlank()) {
            throw BusinessException.badRequest("拆袋必须填写原因并留档");
        }

        bag.setStatus(BagStatus.OPENED);
        em.persist(new BagOperation(bag, BagOperationType.OPEN, operator.getId(), witness.getId(),
                req.reason().trim()));
        notifications.scanNotice(operator.getEmployeeNo(), "拆袋登记",
                "袋号 " + bagNo + " 已由 " + operator.getName() + " 与复核人 "
                        + witness.getName() + " 双人拆袋，原因：" + req.reason());
        em.flush();
        return bagView(bag);
    }

    /** 拆袋期间调整某药品数量（qty 为调整后的目标数量，0 表示取出全部）。 */
    @Transactional
    public BagView adjustEntry(String bagNo, AdjustEntryRequest req) {
        Bag bag = lookup.requireBag(bagNo);
        if (bag.getStatus() != BagStatus.OPENED) {
            throw BusinessException.conflict("只有双人复核拆袋后才能调整袋内内容，当前状态："
                    + bag.getStatus());
        }
        Staff operator = lookup.requireSiteStaff(req.operatorEmployeeNo());
        int target = req.qty() == null ? 0 : req.qty();
        if (target < 0) {
            throw BusinessException.badRequest("数量不能为负数");
        }

        MedicineItem item = em.find(MedicineItem.class, req.itemId());
        if (item == null || !item.getAppointment().getId().equals(bag.getAppointmentId())) {
            throw BusinessException.badRequest("药品不属于本袋所属预约单：" + req.itemId());
        }
        if (item.getCategory() != bag.getCategory()) {
            throw BusinessException.badRequest("不能向 " + bag.getCategory().label()
                    + " 袋加入其他类别药品：" + item.getMedicineName());
        }

        List<BagEntry> entries = entriesOf(bag.getId());
        BagEntry existing = entries.stream().filter(e -> e.getMedicineItemId().equals(item.getId()))
                .findFirst().orElse(null);
        int before = existing == null ? 0 : existing.getQty();

        // 校验取出后 / 装入后总量不超过实收可用（排除本袋自身占用）
        int sealedElsewhere = sealedQty(item.getId(), bag.getId());
        if (target + sealedElsewhere > item.receivedOrZero()) {
            throw BusinessException.conflict(
                    "调整后数量超过可用实收：" + item.getMedicineName() + " 实收 "
                            + item.receivedOrZero() + "，其他袋已装 " + sealedElsewhere);
        }

        if (target == 0 && existing != null) {
            em.remove(existing);
        } else if (existing != null) {
            existing.setQty(target);
        } else {
            if (target == 0) {
                throw BusinessException.badRequest("新增数量必须大于 0");
            }
            em.persist(new BagEntry(bag, item.getId(), item.getMedicineName(), target,
                    item.getUnit()));
        }

        int newTotal = entries.stream().filter(e -> existing == null || e != existing)
                .mapToInt(BagEntry::getQty).sum() + target;
        bag.setTotalQty(newTotal);
        em.persist(new BagContentChange(bag, item.getId(), item.getMedicineName(), before, target,
                operator.getId()));
        em.flush();
        return bagView(bag);
    }

    /** 重新封袋，仍须双人；冷藏须复测温度与冷链库位。 */
    @Transactional
    public BagView reseal(ResealBagRequest req, String bagNo) {
        Bag bag = lookup.requireBag(bagNo);
        if (bag.getStatus() != BagStatus.OPENED) {
            throw BusinessException.conflict("袋号 " + bagNo + " 不在拆袋状态，无需重新封袋");
        }
        Staff operator = lookup.requireSiteStaff(req.operatorEmployeeNo());
        Staff witness = req.witnessEmployeeNo() == null ? null
                : lookup.requireSiteStaff(req.witnessEmployeeNo());
        lookup.requireDistinctSitePair(operator, witness);
        if (req.reason() == null || req.reason().isBlank()) {
            throw BusinessException.badRequest("重新封袋必须填写复核说明");
        }
        if (bag.getTotalQty() <= 0) {
            throw BusinessException.conflict("袋内已无药品，不能空袋重新封袋");
        }
        if (bag.getCategory().cold()) {
            if (req.temperature() == null) {
                throw BusinessException.badRequest("冷藏袋重新封袋必须复测温度（2-8℃）");
            }
            requireColdTemperature(req.temperature(), "重新封袋");
            String loc = req.storageLocation() == null ? "" : req.storageLocation().trim();
            if (!isColdLocation(loc)) {
                throw BusinessException.badRequest("冷藏袋必须放回冷链库位（COLD- 开头）");
            }
            bag.setSealTemperature(req.temperature());
            bag.setStorageLocation(loc);
        }
        bag.setStatus(BagStatus.SEALED);
        em.persist(new BagOperation(bag, BagOperationType.RESEAL, operator.getId(),
                witness.getId(), req.reason().trim()));
        notifications.scanNotice(operator.getEmployeeNo(), "重新封袋",
                "袋号 " + bagNo + " 经双人复核后重新封袋，共 " + bag.getTotalQty() + " "
                        + bag.getUnit() + "，保管位置 " + bag.getStorageLocation());
        em.flush();
        return bagView(bag);
    }

    @Transactional
    public List<BagView> list(String category) {
        String jpql = "select b from Bag b";
        if (category != null && !category.isBlank()) {
            jpql += " where b.category = :c";
        }
        jpql += " order by b.id desc";
        var q = em.createQuery(jpql, Bag.class);
        if (category != null && !category.isBlank()) {
            q.setParameter("c", lookup.parseCategory(category));
        }
        return q.getResultList().stream().map(this::bagView).toList();
    }

    @Transactional
    public BagTraceView trace(String bagNo) {
        Bag bag = lookup.requireBag(bagNo);
        List<OperationView> ops = em.createQuery(
                        "select o from BagOperation o where o.bag.id = :id order by o.id", BagOperation.class)
                .setParameter("id", bag.getId()).getResultList().stream()
                .map(o -> new OperationView(o.getId(), o.getType().name(),
                        lookup.staffName(o.getOperatorStaffId()),
                        lookup.staffName(o.getWitnessStaffId()), o.getReason(),
                        o.getOperatedAt().format(FMT)))
                .toList();
        List<ChangeView> changes = em.createQuery(
                        "select c from BagContentChange c where c.bag.id = :id order by c.id",
                        BagContentChange.class)
                .setParameter("id", bag.getId()).getResultList().stream()
                .map(c -> new ChangeView(c.getId(), c.getMedicineName(), c.getQtyBefore(),
                        c.getQtyAfter(), lookup.staffName(c.getOperatorStaffId()),
                        c.getChangedAt().format(FMT)))
                .toList();
        List<com.medreturn.web.dto.HandoverDtos.HandoverView> handovers = handoverService.viewsOfBag(bag);

        String currentLocation = bag.getStorageLocation();
        String currentHolder = lookup.staffName(bag.getSealedByStaffId());
        var accepted = em.createQuery(
                        "select h from Handover h where h.bag.id = :id and h.status = ACCEPTED order by h.id desc",
                        Handover.class).setParameter("id", bag.getId()).setMaxResults(1)
                .getResultList();
        if (!accepted.isEmpty()) {
            Handover h = accepted.get(0);
            currentLocation = h.getToLocation() == null ? h.getFromLocation() : h.getToLocation();
            currentHolder = lookup.staffName(h.getToStaffId());
        }

        return new BagTraceView(bagView(bag), currentLocation, currentHolder, ops, changes, handovers);
    }

    // ---------- 视图与工具 ----------

    BagView bagView(Bag bag) {
        MedicineCategory c = bag.getCategory();
        List<EntryView> entries = entriesOf(bag.getId()).stream()
                .map(e -> new EntryView(e.getMedicineItemId(), e.getMedicineName(), e.getQty(),
                        e.getUnit()))
                .toList();
        Appointment appt = em.find(Appointment.class, bag.getAppointmentId());
        return new BagView(bag.getBagNo(), c.name(), c.label(), c.dangerous(), c.cold(),
                c.handlingTip(), bag.getStatus().name(), bag.getTotalQty(), bag.getUnit(),
                bag.getSealedAt().format(FMT), lookup.staffName(bag.getSealedByStaffId()),
                lookup.staffName(bag.getWitnessStaffId()), bag.getStorageLocation(),
                bag.getSealTemperature(), appt == null ? null : appt.getCode(),
                bag.getSignedOffAt() == null ? null : bag.getSignedOffAt().format(FMT),
                entries);
    }

    List<BagEntry> entriesOf(Long bagId) {
        return em.createQuery("select e from BagEntry e where e.bag.id = :id order by e.id",
                        BagEntry.class).setParameter("id", bagId).getResultList();
    }

    List<MedicineItem> itemsOf(Long apptId) {
        return em.createQuery("select i from MedicineItem i where i.appointment.id = :id order by i.id",
                        MedicineItem.class).setParameter("id", apptId).getResultList();
    }

    int sealedQty(Long itemId, Long excludeBagId) {
        long total = em.createQuery(
                        "select coalesce(sum(be.qty),0) from BagEntry be where be.medicineItemId = :id",
                        Long.class).setParameter("id", itemId).getSingleResult();
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

    void requireColdTemperature(double t, String action) {
        if (t < COLD_MIN || t > COLD_MAX) {
            throw BusinessException.conflict("冷链温度异常：" + action + "温度 " + t + "℃ 超出 2-8℃ 区间，"
                    + "不得封袋/交接，请检查冷链设备并记录");
        }
    }

    boolean isColdLocation(String location) {
        return location != null && (location.startsWith("COLD") || location.contains("冷藏"));
    }

    private String generateBagNo(MedicineCategory category) {
        String no = "BAG-" + category.name() + "-"
                + (System.currentTimeMillis() % 1_000_000_000L)
                + "-" + ThreadLocalRandom.current().nextInt(100, 999);
        return no;
    }
}
