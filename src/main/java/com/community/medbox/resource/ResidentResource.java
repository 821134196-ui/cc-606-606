package com.community.medbox.resource;

import com.community.medbox.dto.Requests.ReceiptQuery;
import com.community.medbox.service.ResidentService;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import java.util.List;
import java.util.Map;

/**
 * 居民端：只能凭本人"交回单号 + 查询码"查询自己的交回凭证与通知。
 * 返回内容不含药品品名、内部交接人、保管位置、冷链等内部资料。
 */
@Path("/api/resident")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ResidentResource {

    @Inject ResidentService residentService;

    @POST
    @Path("/receipt")
    public Map<String, Object> receipt(@Valid ReceiptQuery query) {
        return residentService.receipt(query.apptNo(), query.queryCode());
    }

    @POST
    @Path("/notifications")
    public List<Map<String, Object>> notifications(@Valid ReceiptQuery query) {
        return residentService.inbox(query.apptNo(), query.queryCode());
    }
}
