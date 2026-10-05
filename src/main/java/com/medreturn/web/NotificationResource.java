package com.medreturn.web;

import com.medreturn.entity.Notification;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

import java.util.List;

/** 工作人员侧的本地模拟通知（扫码/短信均为本地模拟）。 */
@Path("/api/notifications")
@Produces(MediaType.APPLICATION_JSON)
public class NotificationResource {

    @Inject
    EntityManager em;

    @GET
    public List<Notification> list(@QueryParam("employeeNo") @DefaultValue("") String employeeNo) {
        if (employeeNo.isBlank()) {
            return List.of();
        }
        return em.createQuery(
                        "select n from Notification n where n.target = :t and n.audience = 'STAFF' "
                                + "order by n.id desc", Notification.class)
                .setParameter("t", employeeNo).setMaxResults(50).getResultList();
    }
}
