package com.medreturn.service;

import com.medreturn.entity.Notification;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

/** 本地模拟通知：只写本地库 + 前端可见，不接入短信/扫码等外部服务。 */
@ApplicationScoped
public class NotificationService {

    @Inject
    EntityManager em;

    @Transactional
    public void notify(String target, String audience, String title, String content) {
        em.persist(new Notification(target, "LOCAL-SMS", audience, title, content));
    }

    @Transactional
    public void scanNotice(String target, String title, String content) {
        em.persist(new Notification(target, "LOCAL-SCAN", "STAFF", title, content));
    }
}
