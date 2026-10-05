package com.community.medbox.service;

import com.community.medbox.entity.*;
import com.community.medbox.model.BagStatus;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.util.*;

/**
 * 居民凭证查询：
 * - 必须同时提供本人交回单号 + 查询码，核验通过才返回；
 * - 只返回该居民本人单据的汇总凭证（类别、数量、封袋/处置进度），
 *   不返回药品具体品名、不返回内部交接人/保管位置/冷链等内部资料；
 * - 任何其他居民的单据或错误凭证一律 403，无法枚举查看他人信息。
 */
@ApplicationScoped
public class ResidentService {

    @Inject NotificationService notifications;

    @Transactional
    public Map<String, Object> receipt(String apptNo, String queryCode) {
        Appointment a = Appointment.find("apptNo", apptNo).firstResult();
        if (a == null || !a.resident.queryCode.equals(queryCode)) {
            // 不区分"单号不存在"与"凭证错误"，避免枚举他人单号
            throw new BusinessException(403, "交回单号或查询码不正确，无权查询");
        }

        List<Map<String, Object>> bagViews = new ArrayList<>();
        for (Object row : CollectedDrug.getEntityManager()
                .createNativeQuery(
                        "select d.bag_no as bagNo, d.category as category, sum(d.quantity) as qty " +
                        "from collected_drug d where d.appointment_id = :aid and d.bag_no is not null " +
                        "group by d.bag_no, d.category")
                .setParameter("aid", a.id)
                .getResultList()) {
            Object[] r = (Object[]) row;
            Map<String, Object> v = new LinkedHashMap<>();
            v.put("bagNo", r[0]);
            v.put("category", String.valueOf(r[1]));
            v.put("quantity", r[2]);
            SealedBag bag = SealedBag.findByBagNo((String) r[0]);
            v.put("disposalSigned", bag != null && bag.status == BagStatus.SIGNED);
            bagViews.add(v);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("apptNo", a.apptNo);
        result.put("residentName", maskName(a.resident.name));
        result.put("declaredQty", a.declaredQty);
        result.put("receivedQty", a.receivedQty);
        result.put("differenceConfirmed", a.differenceConfirmed);
        result.put("differenceNote", a.differenceNote);
        result.put("status", a.status.name());
        result.put("bags", bagViews);
        result.put("notice", "本凭证仅展示交回汇总信息；内部交接与保管资料不对居民展示。");
        return result;
    }

    /** 姓名脱敏：张*三 / 李* */
    private String maskName(String name) {
        if (name == null || name.length() <= 1) return name;
        if (name.length() == 2) return name.charAt(0) + "*";
        return name.charAt(0) + "*".repeat(name.length() - 2) + name.charAt(name.length() - 1);
    }

    @Transactional
    public List<Map<String, Object>> inbox(String apptNo, String queryCode) {
        Appointment a = Appointment.find("apptNo", apptNo).firstResult();
        if (a == null || !a.resident.queryCode.equals(queryCode)) {
            throw new BusinessException(403, "交回单号或查询码不正确，无权查询");
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Notification n : notifications.residentInbox(a.resident.phone)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("content", n.content);
            m.put("createdAt", n.createdAt.toString());
            out.add(m);
        }
        return out;
    }
}
