package com.community.medbox;

import com.community.medbox.entity.*;
import com.community.medbox.model.*;
import com.community.medbox.service.*;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.WebApplicationException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.function.Executable;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 交付前核心规则验证：
 * 1) 封袋后的内容无法随意改动、不能追加；
 * 2) 拆袋必须双人（不同两人）复核且留记录；
 * 3) 冷藏药品的存放/流转沿袋号可追查、温度越限拒绝/告警；
 * 4) 居民只能查询本人凭证，看不到他人明细或内部交接资料；
 * 5) 实收差异必须居民本人确认。
 *
 * 所有数据库访问都经过 Tx 辅助 Bean（每次调用独立事务），
 * 与真实 REST 调用边界一致，避免无事务上下文读取泄漏 SQLite 单连接。
 */
@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class WorkflowTest {

    @Inject AppointmentService appointments;
    @Inject BagService bags;
    @Inject TransferService transfers;
    @Inject ResidentService residentService;
    @Inject Tx tx;

    static Long apptA, apptB;
    static String coldBagNo, normalBagNo, apptNoA, apptNoB;

    /** 小视图对象，避免懒加载逸出事务。 */
    public record BagView(String bagNo, BagStatus status, String category, int quantity,
                          String storageLocation) {}
    public record ReopenView(String operator, String reviewer, String reason) {}

    @ApplicationScoped
    public static class Tx {
        @Transactional
        public void staffs() {
            if (Staff.count() > 0) return;
            staff("zhang", "张敏", "STAFF");
            staff("li", "李强", "STAFF");
            staff("chen", "陈芳", "STAFF");
            staff("wang", "处置王", "DISPOSAL");
        }

        @Transactional
        public Long appt(String name, String phone, String code, int declared) {
            Resident r = new Resident();
            r.name = name; r.phone = phone; r.queryCode = code;
            r.persist();
            Appointment a = new Appointment();
            a.apptNo = "AT-" + System.nanoTime();
            a.resident = r;
            a.declaredQty = declared;
            a.persist();
            a.flush();
            return a.id;
        }

        @Transactional
        public String apptNo(long id) {
            return Appointment.<Appointment>findById(id).apptNo;
        }

        @Transactional
        public long count(Class<?> type, String query, Object... params) {
            if (type == null) return com.community.medbox.entity.CollectedDrug.count(query, params);
            return io.quarkus.hibernate.orm.panache.PanacheEntityBase
                    .<io.quarkus.hibernate.orm.panache.PanacheEntityBase>count(
                            type == CollectedDrug.class ? "CollectedDrug"
                            : type == BagReopenRecord.class ? "BagReopenRecord"
                            : type == ColdChainLog.class ? "ColdChainLog"
                            : type.getSimpleName(), query, params);
        }

        @Transactional
        public long drugCount(String query, Object... params) {
            return CollectedDrug.count(query, params);
        }

        @Transactional
        public long reopenCount() {
            return BagReopenRecord.count();
        }

        @Transactional
        public long coldLogCount(String bagNo) {
            return ColdChainLog.count("bagNo", bagNo);
        }

        @Transactional
        public BagView bag(String bagNo) {
            SealedBag b = SealedBag.findByBagNo(bagNo);
            return new BagView(b.bagNo, b.status, b.category.name(), b.quantity, b.storageLocation);
        }

        @Transactional
        public ReopenView lastReopen(String bagNo) {
            BagReopenRecord r = BagReopenRecord.<BagReopenRecord>find("bagNo", bagNo).firstResult();
            return r == null ? null
                    : new ReopenView(r.operator.username, r.reviewer.username, r.reason);
        }

        @Transactional
        public boolean coldAlarmExists(String bagNo) {
            return !ColdChainLog.find("bagNo = ?1 and alarm = true", bagNo).list().isEmpty();
        }

        private void staff(String u, String n, String role) {
            Staff s = new Staff();
            s.username = u; s.displayName = n; s.role = role;
            s.persist();
        }
    }

    static com.community.medbox.dto.Requests.DrugRequest drug(String name, String cat, int qty, Boolean safe) {
        return new com.community.medbox.dto.Requests.DrugRequest(name, cat, qty, safe);
    }
    static com.community.medbox.dto.Requests.SealRequest seal(String cat, String loc, Double t) {
        return new com.community.medbox.dto.Requests.SealRequest(cat, loc, t);
    }

    private static WebApplicationException expect400(Executable e) {
        WebApplicationException ex = assertThrows(WebApplicationException.class, e);
        assertEquals(400, ex.getResponse().getStatus());
        return ex;
    }
    private static void expect403(Executable e) {
        WebApplicationException ex = assertThrows(WebApplicationException.class, e);
        assertEquals(403, ex.getResponse().getStatus());
    }

    @Test
    @Order(1)
    void prepareData() {
        tx.staffs();
        apptA = tx.appt("周岚", "13900000001", "QA-CODE-1", 10);
        apptB = tx.appt("吴磊", "13900000002", "QA-CODE-2", 3);
        apptNoA = tx.apptNo(apptA);
        apptNoB = tx.apptNo(apptB);
        assertNotNull(apptA);
    }

    @Test
    @Order(2)
    void differenceMustBeConfirmedByResident() {
        // 申报10，实收8：差异
        appointments.check(apptA, new com.community.medbox.dto.Requests.CheckRequest(8));
        // 未确认差异前，不能登记药品
        expect400(() -> appointments.addDrug(apptA, drug("过期降压片", "COMMON_SOLID", 2, null)));

        // 错误查询码不能确认（403，工作人员不能代确认）
        expect403(() -> appointments.confirmDifference(apptA,
                new com.community.medbox.dto.Requests.ConfirmDifferenceRequest("WRONG", null)));

        // 居民本人正确查询码确认
        appointments.confirmDifference(apptA,
                new com.community.medbox.dto.Requests.ConfirmDifferenceRequest("QA-CODE-1", "两盒已丢"));

        // B 单数量一致，核对后无需差异确认
        appointments.check(apptB, new com.community.medbox.dto.Requests.CheckRequest(3));
    }

    @Test
    @Order(3)
    void registerAndSeal() {
        // 危险药品未确认防护措施 -> 拒绝
        expect400(() -> appointments.addDrug(apptA, drug("过期化疗药", "HAZARDOUS", 1, false)));
        appointments.addDrug(apptA, drug("过期化疗药", "HAZARDOUS", 1, true));
        appointments.addDrug(apptA, drug("过期胰岛素", "REFRIGERATED", 2, null));
        appointments.addDrug(apptA, drug("过期降压片", "COMMON_SOLID", 5, null));

        // 冷藏封袋缺温度 -> 拒绝；越限温度 -> 拒绝
        expect400(() -> bags.seal(apptA, "zhang", seal("REFRIGERATED", "冷藏柜2号", null)));
        expect400(() -> bags.seal(apptA, "zhang", seal("REFRIGERATED", "冷藏柜2号", 12.0)));

        coldBagNo = bags.seal(apptA, "zhang", seal("REFRIGERATED", "冷藏柜2号", 5.0)).bagNo;
        normalBagNo = bags.seal(apptA, "zhang", seal("COMMON_SOLID", "常温货架A", null)).bagNo;
        bags.seal(apptA, "zhang", seal("HAZARDOUS", "危废柜1号", null));
        assertNotNull(coldBagNo);

        // B 居民药品（保持未封袋，供权限测试对照）
        appointments.addDrug(apptB, drug("过期感冒药", "COMMON_SOLID", 3, null));
    }

    @Test
    @Order(4)
    void sealedContentsAreImmutableAndAppendForbidden() {
        // 已封袋的药品均挂袋号
        assertTrue(tx.drugCount("bagNo is not null") >= 3);

        // 再次对已全部封袋的类别封袋 -> 没有待封药品，拒绝（封袋后不能再追加）
        expect400(() -> bags.seal(apptA, "zhang", seal("COMMON_SOLID", "常温货架A", null)));

        // 即使又登记一件普通固体，它也只能进入"新袋"，不能被塞进已封袋：
        appointments.addDrug(apptA, drug("后补的过期维生素", "COMMON_SOLID", 1, null));
        assertEquals(0L, tx.drugCount("name = ?1 and bagNo = ?2", "后补的过期维生素", normalBagNo));
        assertEquals(5, tx.bag(normalBagNo).quantity());

        // 拆袋不能同一人复核；原因必填
        expect400(() -> bags.reopen(normalBagNo,
                new com.community.medbox.dto.Requests.ReopenRequest("zhang", "zhang", "测试")));
        expect400(() -> bags.reopen(normalBagNo,
                new com.community.medbox.dto.Requests.ReopenRequest("zhang", "li", "  ")));
    }

    @Test
    @Order(5)
    void reopenRequiresTwoPeopleAndKeepsRecord() {
        long before = tx.reopenCount();
        // 双人复核拆袋成功
        assertEquals(BagStatus.REOPENED,
                bags.reopen(normalBagNo,
                        new com.community.medbox.dto.Requests.ReopenRequest(
                                "zhang", "li", "一件分类错误，复核后改封")).status);

        // 记录留痕：操作人/复核人为不同两人
        WorkflowTest.ReopenView rec = tx.lastReopen(normalBagNo);
        assertNotNull(rec);
        assertEquals("zhang", rec.operator());
        assertEquals("li", rec.reviewer());
        assertNotEquals(rec.operator(), rec.reviewer());
        assertEquals(before + 1, tx.reopenCount());

        // 拆出的药品不再属于任何袋，但可追溯到原袋
        assertEquals(0L, tx.drugCount("bagNo", normalBagNo));
        assertTrue(tx.drugCount("reopenedFrom", normalBagNo) >= 1);

        // 重新封袋：新袋号新封条，只收回原袋 5 件；旧袋标记为被新袋承接
        SealedBag re = bags.reseal(normalBagNo, "zhang", "常温货架A", null);
        assertEquals(BagStatus.SEALED, re.status);
        assertEquals(5, re.quantity);
        assertNotEquals(normalBagNo, re.bagNo);
        assertEquals(BagStatus.SUPERSEDED, tx.bag(normalBagNo).status());
        // 后补登记的药品不能借拆袋混入原袋，仍是未封袋
        assertEquals(0L, tx.drugCount("name = ?1 and bagNo = ?2", "后补的过期维生素", re.bagNo));
        assertEquals(1L, tx.drugCount("name = ?1 and bagNo is null", "后补的过期维生素"));
    }

    @Test
    @Order(6)
    void coldChainIsTraceableByBagNo() {
        // 巡检正常温度
        bags.logCold(coldBagNo, new com.community.medbox.dto.Requests.ColdLogRequest(
                "CHECK", 6.2, "冷藏柜2号", "zhang", "巡检正常"));
        // 越限温度登记 -> 告警记录保留
        ColdChainLog alarmLog = bags.logCold(coldBagNo, new com.community.medbox.dto.Requests.ColdLogRequest(
                "CHECK", 9.5, "冷藏柜2号", "zhang", "柜门未关"));
        assertTrue(alarmLog.alarm);
        assertTrue(tx.coldAlarmExists(coldBagNo));

        // 冷藏袋转交不带温度 -> 拒绝；越限 -> 拒绝
        expect400(() -> transfers.transfer(coldBagNo,
                new com.community.medbox.dto.Requests.TransferRequest("zhang", "li", "冷藏柜3号", null, "RCV-X")));
        expect400(() -> transfers.transfer(coldBagNo,
                new com.community.medbox.dto.Requests.TransferRequest("zhang", "li", "冷藏柜3号", 10.0, "RCV-X")));
        // 合规温度交接成功，记录接收人
        assertEquals("li", transfers.transfer(coldBagNo,
                new com.community.medbox.dto.Requests.TransferRequest("zhang", "li", "冷藏柜3号", 4.5, "RCV-OK"))
                .toStaff.username);

        // 沿袋号追查：入柜 + 2条巡检 + 转交点（OUT），温度链完整
        assertTrue(tx.coldLogCount(coldBagNo) >= 4,
                "冷链记录应含入柜/巡检/取出，实际 " + tx.coldLogCount(coldBagNo));
        // 保管位置随转交更新
        assertEquals("冷藏柜3号", tx.bag(coldBagNo).storageLocation());
    }

    @Test
    @Order(7)
    void handoverAndSignFlow() {
        String code = "DISP-SCAN-1";
        // 移交处置单位必须扫签收码
        expect400(() -> transfers.handover(coldBagNo,
                new com.community.medbox.dto.Requests.HandoverRequest("li", "wang", null, 5.0, "出库口")));
        transfers.handover(coldBagNo,
                new com.community.medbox.dto.Requests.HandoverRequest("li", "wang", code, 5.2, "出库口"));
        assertEquals(BagStatus.HANDED_OVER, tx.bag(coldBagNo).status());

        // 已移交的袋不能再拆
        expect400(() -> bags.reopen(coldBagNo,
                new com.community.medbox.dto.Requests.ReopenRequest("zhang", "li", "x")));

        // 签收码错误拒绝；非接收人不能签收
        expect400(() -> transfers.sign(coldBagNo,
                new com.community.medbox.dto.Requests.SignRequest("wang", "WRONG-CODE")));
        expect400(() -> transfers.sign(coldBagNo,
                new com.community.medbox.dto.Requests.SignRequest("zhang", code)));
        // 正确签收闭环
        transfers.sign(coldBagNo, new com.community.medbox.dto.Requests.SignRequest("wang", code));
        assertEquals(BagStatus.SIGNED, tx.bag(coldBagNo).status());
    }

    @Test
    @Order(8)
    void residentCanOnlySeeOwnReceipt() {
        // A 用自己的单号+查询码：可见汇总
        var receiptA = residentService.receipt(apptNoA, "QA-CODE-1");
        assertEquals(apptNoA, receiptA.get("apptNo"));
        @SuppressWarnings("unchecked")
        List<java.util.Map<String, Object>> bagViews =
                (List<java.util.Map<String, Object>>) receiptA.get("bags");
        assertFalse(bagViews.isEmpty());

        // 凭证不含内部字段：保管位置、交接人、药品名称、冷链
        String json = receiptA.toString();
        assertFalse(json.contains("storageLocation"), "居民凭证不得包含保管位置");
        assertFalse(json.contains("sealedBy"), "居民凭证不得包含内部交接人");
        assertFalse(json.contains("胰岛素"), "居民凭证不得暴露具体药品名称");
        assertFalse(json.contains("coldTemp"), "居民凭证不得包含冷链内部资料");

        // A 的查询码查 B 的单号 -> 403；错误查询码 -> 403；不存在单号同样 403（无法枚举）
        expect403(() -> residentService.receipt(apptNoB, "QA-CODE-1"));
        expect403(() -> residentService.receipt(apptNoA, "HACK"));
        expect403(() -> residentService.receipt("A-NO-SUCH", "QA-CODE-1"));
    }
}
