package com.medreturn.bootstrap;

import com.medreturn.domain.*;
import com.medreturn.entity.*;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** 首次启动写入演示数据：工作人员、居民、预约单（含危险/冷藏药品）。 */
@ApplicationScoped
public class DataInitializer {

    @Inject
    EntityManager em;

    @Transactional
    void onStart(@Observes StartupEvent ev) {
        Long staffCount = em.createQuery("select count(s) from Staff s", Long.class).getSingleResult();
        if (staffCount != null && staffCount > 0) {
            return;
        }

        // 工作人员（回收点）
        Staff w1 = new Staff("王建国", "W001", StaffRole.SITE, "阳光社区回收点");
        Staff w2 = new Staff("李晓梅", "W002", StaffRole.SITE, "阳光社区回收点");
        Staff w3 = new Staff("陈芳", "W003", StaffRole.SITE, "阳光社区回收点");
        // 处置单位签收人员
        Staff d1 = new Staff("赵强", "D001", StaffRole.DISPOSAL, "绿源医废无害化处置中心");
        em.persist(w1);
        em.persist(w2);
        em.persist(w3);
        em.persist(d1);
        em.flush();

        // 居民 1：已完成现场核对、差异已确认，可直接封袋（含危险药品与冷藏药品）
        Resident r1 = new Resident("张桂兰", "13800001111", "111111");
        // 居民 2：刚预约，待核对
        Resident r2 = new Resident("李国富", "13800002222", "222222");
        em.persist(r1);
        em.persist(r2);
        em.flush();

        Appointment a1 = new Appointment();
        a1.setCode("YY100001");
        a1.setResident(r1);
        a1.setScheduledAt(LocalDateTime.now().minusHours(2));
        a1.setStatus(AppointmentStatus.RECEIVED);
        a1.setDifferenceConfirmed(true);
        a1.setConfirmedAt(LocalDateTime.now().minusHours(1));
        a1.setDifferenceNote("过期口服液 1 瓶破损，居民现场确认实收数量");
        a1.setReceivedByStaffId(w1.getId());
        a1.setReceivedAt(LocalDateTime.now().minusHours(1));
        em.persist(a1);
        em.flush();

        // 药品：预约数量、实收数量（止咳糖浆预约 2 实收 1，演示差异）
        List<MedicineItem> items = new ArrayList<>();
        items.add(new MedicineItem(a1, "复方降压片", MedicineCategory.ORAL_SOLID, 3, "盒"));
        items.add(new MedicineItem(a1, "布洛芬缓释胶囊", MedicineCategory.ORAL_SOLID, 2, "盒"));
        items.add(new MedicineItem(a1, "止咳糖浆", MedicineCategory.ORAL_LIQUID, 2, "瓶"));
        items.add(new MedicineItem(a1, "红霉素软膏", MedicineCategory.EXTERNAL, 1, "支"));
        items.add(new MedicineItem(a1, "废弃注射器（带针头）", MedicineCategory.INJECTION, 5, "支"));
        items.add(new MedicineItem(a1, "过期抗肿瘤药（剩余）", MedicineCategory.HAZARDOUS, 1, "盒"));
        items.add(new MedicineItem(a1, "胰岛素注射液", MedicineCategory.COLD, 2, "支"));
        int[] received = {3, 2, 1, 1, 5, 1, 2};
        for (int i = 0; i < items.size(); i++) {
            em.persist(items.get(i));
            items.get(i).setReceivedQty(received[i]);
        }

        Appointment a2 = new Appointment();
        a2.setCode("YY100002");
        a2.setResident(r2);
        a2.setScheduledAt(LocalDateTime.now().plusHours(3));
        a2.setStatus(AppointmentStatus.PENDING);
        em.persist(a2);
        em.flush();
        MedicineItem m = new MedicineItem(a2, "胃药（过期）", MedicineCategory.ORAL_SOLID, 1, "盒");
        em.persist(m);
    }
}
