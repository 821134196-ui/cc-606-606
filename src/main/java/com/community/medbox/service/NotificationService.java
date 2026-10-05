package com.community.medbox.service;

import com.community.medbox.entity.Notification;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.util.List;

/**
 * 本地模拟通知服务：仅落库展示，不真正调用短信/推送。
 */
@ApplicationScoped
public class NotificationService {

    @Transactional
    public void notifyResident(String phone, String content) {
        Notification n = new Notification();
        n.targetType = "RESIDENT";
        n.targetId = phone;
        n.content = content;
        n.persist();
    }

    @Transactional
    public void notifyStaff(String username, String content) {
        Notification n = new Notification();
        n.targetType = "STAFF";
        n.targetId = username;
        n.content = content;
        n.persist();
    }

    public List<Notification> residentInbox(String phone) {
        return Notification.find("targetType = ?1 and targetId = ?2 order by createdAt desc",
                "RESIDENT", phone).list();
    }

    public List<Notification> staffInbox(String username) {
        return Notification.find("targetType = ?1 and targetId = ?2 order by createdAt desc",
                "STAFF", username).list();
    }
}
