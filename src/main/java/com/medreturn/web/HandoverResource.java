package com.medreturn.web;

import com.medreturn.service.HandoverService;
import com.medreturn.web.dto.HandoverDtos.*;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

import java.util.List;

/** 封袋保管转交、冷藏交接、接收人扫码签收、处置单位签收。 */
@Path("/api/handovers")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class HandoverResource {

    @Inject
    HandoverService handovers;

    @POST
    public HandoverView create(HandoverRequest req) {
        return handovers.create(req);
    }

    @GET
    public List<HandoverView> list(@QueryParam("toEmployeeNo") String toEmployeeNo,
                                   @QueryParam("status") String status) {
        return handovers.list(toEmployeeNo, status);
    }

    /** 接收人扫码签收 / 拒收（本地模拟扫码）。 */
    @PUT
    @Path("/{id}/accept")
    public HandoverView accept(@PathParam("id") Long id, AcceptRequest req) {
        return handovers.accept(id, req);
    }
}
