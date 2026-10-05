package com.community.medbox.bootstrap;

import com.community.medbox.entity.*;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/** 启动时写入演示账号（本地模拟环境，测试环境关闭）。 */
@ApplicationScoped
public class DataSeeder {

    @ConfigProperty(name = "medbox.seed", defaultValue = "true")
    boolean seed;

    @Transactional
    public void onStart(@Observes StartupEvent ev) {
        if (!seed || Staff.count() > 0) return;

        staff("zhang", "张敏（回收点）", "STAFF");
        staff("li", "李强（回收点）", "STAFF");
        staff("chen", "陈芳（回收点）", "STAFF");
        staff("wang", "王建国（绿环处置单位）", "DISPOSAL");

        Resident r1 = resident("周岚", "13800000001", "RC1001");
        Resident r2 = resident("吴磊", "13800000002", "RC1002");

        Appointment a1 = new Appointment();
        a1.apptNo = "A-DEMO-0001";
        a1.resident = r1;
        a1.declaredQty = 6;
        a1.persist();

        Appointment a2 = new Appointment();
        a2.apptNo = "A-DEMO-0002";
        a2.resident = r2;
        a2.declaredQty = 2;
        a2.persist();
    }

    private void staff(String u, String n, String role) {
        Staff s = new Staff();
        s.username = u;
        s.displayName = n;
        s.role = role;
        s.persist();
    }

    private Resident resident(String name, String phone, String code) {
        Resident r = new Resident();
        r.name = name;
        r.phone = phone;
        r.queryCode = code;
        r.persist();
        return r;
    }
}
