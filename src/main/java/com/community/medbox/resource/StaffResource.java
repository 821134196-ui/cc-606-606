package com.community.medbox.resource;

import com.community.medbox.dto.Requests.*;
import com.community.medbox.entity.*;
import com.community.medbox.model.DrugCategory;
import com.community.medbox.service.*;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.transaction.Transactional;
import java.time.LocalDateTime;
import java.util.*;

/** 工作人员现场操作接口：预约核对、差异确认、登记、封袋、拆袋、交接、冷链、签收。 */
@Path("/api/staff")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Transactional
public class StaffResource {

    @Inject AppointmentService appointments;
    @Inject BagService bags;
    @Inject TransferService transfers;
    @Inject NotificationService notifications;

    // ---------- 基础数据：类别与现场处理要求（危险/冷藏须有文字提示） ----------

    @GET
    @Path("/categories")
    public List<Map<String, Object>> categories() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (DrugCategory c : DrugCategory.values()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("code", c.name());
            m.put("label", c.label);
            m.put("hazardous", c.hazardous);
            m.put("refrigerated", c.refrigerated);
            if (c.hazardous) {
                m.put("handling", "【危险药品】必须佩戴手套口罩，使用防泄漏双层封袋单独封装；"
                        + "登记前逐项确认防护措施，交接时双人核对，禁止与其他药品混放。");
            } else if (c.refrigerated) {
                m.put("handling", "【需冷藏药品】全程保持 2~8℃；封袋立即入冷藏柜并登记温度，"
                        + "每次转交必须测温并沿袋号记录，取出后 30 分钟内完成交接。");
            } else {
                m.put("handling", "按常规分类封袋，保持原包装并清点数量。");
            }
            out.add(m);
        }
        return out;
    }

    @GET
    @Path("/staff")
    public List<Map<String, Object>> staffList() {
        return Staff.<Staff>listAll().stream().map(s -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("username", s.username);
            m.put("displayName", s.displayName);
            m.put("role", s.role);
            return m;
        }).toList();
    }

    // ---------- 预约与实收核对 ----------

    @POST
    @Path("/appointments")
    public Map<String, Object> create(@Valid AppointmentRequest req) {
        return appointmentView(appointments.create(req));
    }

    @GET
    @Path("/appointments")
    public List<Map<String, Object>> listAppointments() {
        return appointments.list().stream().map(this::appointmentView).toList();
    }

    @GET
    @Path("/appointments/{id}")
    public Map<String, Object> get(@PathParam("id") Long id) {
        Appointment a = appointments.mustGet(id);
        Map<String, Object> v = appointmentView(a);
        v.put("resident", Map.of(
                "name", a.resident.name, "phone", a.resident.phone, "queryCode", a.resident.queryCode));
        List<Map<String, Object>> drugs = new ArrayList<>();
        for (CollectedDrug d : a.drugs) {
            drugs.add(drugView(d));
        }
        v.put("drugs", drugs);
        return v;
    }

    @POST
    @Path("/appointments/{id}/check")
    public Map<String, Object> check(@PathParam("id") Long id, @Valid CheckRequest req) {
        return appointmentView(appointments.check(id, req));
    }

    @POST
    @Path("/appointments/{id}/confirm-difference")
    public Map<String, Object> confirmDifference(@PathParam("id") Long id, @Valid ConfirmDifferenceRequest req) {
        return appointmentView(appointments.confirmDifference(id, req));
    }

    @POST
    @Path("/appointments/{id}/drugs")
    public Map<String, Object> addDrug(@PathParam("id") Long id, @Valid DrugRequest req) {
        return drugView(appointments.addDrug(id, req));
    }

    @POST
    @Path("/appointments/{id}/complete")
    public Map<String, Object> complete(@PathParam("id") Long id) {
        return appointmentView(appointments.complete(id));
    }

    // ---------- 封袋 / 拆袋 / 重新封袋 ----------

    @POST
    @Path("/appointments/{id}/seal")
    public Map<String, Object> seal(@PathParam("id") Long id, @QueryParam("staff") String staff,
                                    @Valid SealRequest req) {
        return bagView(bags.seal(id, staff, req));
    }

    @POST
    @Path("/bags/{bagNo}/reopen")
    public Map<String, Object> reopen(@PathParam("bagNo") String bagNo, @Valid ReopenRequest req) {
        return bagView(bags.reopen(bagNo, req));
    }

    @POST
    @Path("/bags/{bagNo}/reseal")
    public Map<String, Object> reseal(@PathParam("bagNo") String bagNo,
                                      @QueryParam("staff") String staff,
                                      @QueryParam("location") String location,
                                      @QueryParam("fridgeTemp") Double fridgeTemp) {
        return bagView(bags.reseal(bagNo, staff, location, fridgeTemp));
    }

    @POST
    @Path("/bags/{bagNo}/adjust")
    public Map<String, Object> adjust(@PathParam("bagNo") String bagNo, @Valid DrugRequest req) {
        return drugView(bags.adjustAfterReopen(bagNo, req.name(),
                DrugCategory.valueOf(req.category()), req.quantity()));
    }

    // ---------- 冷链 ----------

    @POST
    @Path("/bags/{bagNo}/cold-logs")
    public Map<String, Object> coldLog(@PathParam("bagNo") String bagNo, @Valid ColdLogRequest req) {
        ColdChainLog log = bags.logCold(bagNo, req);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", log.id);
        m.put("bagNo", log.bagNo);
        m.put("action", log.action);
        m.put("temperature", log.temperature);
        m.put("alarm", log.alarm);
        m.put("note", log.note);
        m.put("createdAt", log.createdAt.toString());
        return m;
    }

    // ---------- 转交 / 移交 / 签收 ----------

    @POST
    @Path("/bags/{bagNo}/transfer")
    public Map<String, Object> transfer(@PathParam("bagNo") String bagNo, @Valid TransferRequest req) {
        return transferView(transfers.transfer(bagNo, req));
    }

    @POST
    @Path("/bags/{bagNo}/handover")
    public Map<String, Object> handover(@PathParam("bagNo") String bagNo, @Valid HandoverRequest req) {
        return transferView(transfers.handover(bagNo, req));
    }

    @POST
    @Path("/bags/{bagNo}/sign")
    public Map<String, Object> sign(@PathParam("bagNo") String bagNo, @Valid SignRequest req) {
        return transferView(transfers.sign(bagNo, req));
    }

    @GET
    @Path("/bags")
    public List<Map<String, Object>> bags(@QueryParam("status") String status) {
        List<SealedBag> list = (status == null || status.isBlank())
                ? SealedBag.list("order by sealedAt desc")
                : SealedBag.list("status = ?1 order by sealedAt desc",
                        com.community.medbox.model.BagStatus.valueOf(status));
        return list.stream().map(this::bagView).toList();
    }

    /** 袋号全链路追溯：袋信息 + 每次转交接收人 + 冷链温度 + 双人拆袋记录。 */
    @GET
    @Path("/bags/{bagNo}/trace")
    public Map<String, Object> trace(@PathParam("bagNo") String bagNo) {
        SealedBag bag = BagService.requireBag(bagNo);
        Map<String, Object> m = bagView(bag);
        m.put("transfers", transfers.history(bagNo).stream().map(this::transferView).toList());
        m.put("coldLogs", ColdChainLog.<ColdChainLog>list("bagNo", bagNo).stream().map(l -> {
            Map<String, Object> x = new LinkedHashMap<>();
            x.put("action", l.action);
            x.put("temperature", l.temperature);
            x.put("alarm", l.alarm);
            x.put("fridgeNo", l.fridgeNo);
            x.put("note", l.note);
            x.put("operator", l.operator == null ? null : l.operator.displayName);
            x.put("createdAt", l.createdAt.toString());
            return x;
        }).toList());
        m.put("reopenRecords", BagReopenRecord.<BagReopenRecord>list("bagNo", bagNo).stream().map(r -> {
            Map<String, Object> x = new LinkedHashMap<>();
            x.put("operator", r.operator.displayName + "(" + r.operator.username + ")");
            x.put("reviewer", r.reviewer.displayName + "(" + r.reviewer.username + ")");
            x.put("reason", r.reason);
            x.put("createdAt", r.createdAt.toString());
            return x;
        }).toList());
        m.put("contents", CollectedDrug.<CollectedDrug>list("bagNo", bagNo).stream().map(d -> {
            Map<String, Object> x = new LinkedHashMap<>();
            x.put("name", d.name);
            x.put("category", d.category.name());
            x.put("quantity", d.quantity);
            return x;
        }).toList());
        return m;
    }

    @GET
    @Path("/notifications")
    public List<Map<String, Object>> inbox(@QueryParam("user") String user) {
        return notifications.staffInbox(user).stream().map(n -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("content", n.content);
            m.put("read", n.read);
            m.put("createdAt", n.createdAt.toString());
            return m;
        }).toList();
    }

    // ---------- 视图映射 ----------

    private Map<String, Object> appointmentView(Appointment a) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", a.id);
        m.put("apptNo", a.apptNo);
        m.put("residentName", a.resident.name);
        m.put("residentPhone", a.resident.phone);
        m.put("declaredQty", a.declaredQty);
        m.put("receivedQty", a.receivedQty);
        m.put("status", a.status.name());
        m.put("statusLabel", a.status.label);
        m.put("differenceConfirmed", a.differenceConfirmed);
        m.put("differenceNote", a.differenceNote);
        m.put("createdAt", String.valueOf(a.createdAt));
        m.put("sealedCount", CollectedDrug.count("appointment = ?1 and bagNo is not null", a));
        m.put("unsealedCount", CollectedDrug.count("appointment = ?1 and bagNo is null", a));
        return m;
    }

    private Map<String, Object> drugView(CollectedDrug d) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", d.id);
        m.put("name", d.name);
        m.put("category", d.category.name());
        m.put("categoryLabel", d.category.label);
        m.put("hazardous", d.category.hazardous);
        m.put("refrigerated", d.category.refrigerated);
        m.put("quantity", d.quantity);
        m.put("bagNo", d.bagNo);
        m.put("safetyConfirmed", d.safetyConfirmed);
        return m;
    }

    private Map<String, Object> bagView(SealedBag b) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", b.id);
        m.put("bagNo", b.bagNo);
        m.put("category", b.category.name());
        m.put("categoryLabel", b.category.label);
        m.put("hazardous", b.category.hazardous);
        m.put("refrigerated", b.category.refrigerated);
        m.put("quantity", b.quantity);
        m.put("sealedBy", b.sealedBy.displayName);
        m.put("storageLocation", b.storageLocation);
        m.put("sealCode", b.sealCode);
        m.put("status", b.status.name());
        m.put("statusLabel", b.status.label);
        m.put("sealedAt", b.sealedAt.toString());
        return m;
    }

    private Map<String, Object> transferView(TransferRecord t) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", t.id);
        m.put("bagNo", t.bagNo);
        m.put("type", t.type.name());
        m.put("typeLabel", t.type.label);
        m.put("fromStaff", t.fromStaff.displayName + "(" + t.fromStaff.username + ")");
        m.put("toStaff", t.toStaff.displayName + "(" + t.toStaff.username + ")");
        m.put("toStaffUsername", t.toStaff.username);
        m.put("location", t.location);
        m.put("coldTemp", t.coldTemp);
        m.put("scanCode", t.scanCode);
        m.put("createdAt", t.createdAt.toString());
        return m;
    }
}
