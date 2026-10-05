package com.medreturn.web;

import com.medreturn.service.ResidentService;
import com.medreturn.web.dto.CommonDtos.NotificationView;
import com.medreturn.web.dto.CommonDtos.ReceiptView;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

import java.util.List;
import java.util.Map;

/**
 * 居民自助：凭“凭证编号 + 查询码”查询本人交回凭证。
 * 不设鉴权态：每次都必须通过查询码校验，查不到/不匹配统一返回 403，
 * 且响应中不包含任何其他居民或内部交接信息。
 */
@Path("/api/resident")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ResidentResource {

    @Inject
    ResidentService residents;

    @POST
    @Path("/receipt")
    public ReceiptView receipt(Map<String, String> body) {
        return residents.receipt(body.get("appointmentCode"), body.get("tokenCode"));
    }

    @POST
    @Path("/notifications")
    public List<NotificationView> notifications(Map<String, String> body) {
        return residents.myNotifications(body.get("appointmentCode"), body.get("tokenCode"));
    }
}
