package com.medreturn.web;

import com.medreturn.service.BagService;
import com.medreturn.web.dto.BagDtos.*;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

import java.util.List;
import java.util.Map;

/** 分类封袋、双人拆袋、袋号追溯。 */
@Path("/api/bags")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class BagResource {

    @Inject
    BagService bags;

    @POST
    public BagView seal(SealBagRequest req) {
        return bags.seal(req);
    }

    @GET
    public List<BagView> list(@QueryParam("category") String category) {
        return bags.list(category);
    }

    /** 沿袋号追查：基本信息、封/拆袋流水、内容变更、交接链、当前位置与持有人。 */
    @GET
    @Path("/{bagNo}/trace")
    public BagTraceView trace(@PathParam("bagNo") String bagNo) {
        return bags.trace(bagNo);
    }

    /**
     * 封袋后追加药品：对 SEALED/SIGNED_OFF 一律 409。
     * 仅双人复核拆袋（OPENED）后允许，且逐条留痕。
     */
    @POST
    @Path("/{bagNo}/entries")
    public BagView append(@PathParam("bagNo") String bagNo, Map<String, Object> body) {
        Long itemId = Long.valueOf(String.valueOf(body.get("itemId")));
        int qty = Integer.parseInt(String.valueOf(body.get("qty")));
        String op = String.valueOf(body.get("operatorEmployeeNo"));
        return bags.appendEntry(bagNo, itemId, qty, op);
    }

    /** 双人复核拆袋。 */
    @POST
    @Path("/{bagNo}/open")
    public BagView open(@PathParam("bagNo") String bagNo, OpenBagRequest req) {
        return bags.open(req, bagNo);
    }

    /** 拆袋期间调整袋内某药品数量。 */
    @PUT
    @Path("/{bagNo}/entries")
    public BagView adjust(@PathParam("bagNo") String bagNo, AdjustEntryRequest req) {
        return bags.adjustEntry(bagNo, req);
    }

    /** 双人复核后重新封袋。 */
    @POST
    @Path("/{bagNo}/reseal")
    public BagView reseal(@PathParam("bagNo") String bagNo, ResealBagRequest req) {
        return bags.reseal(req, bagNo);
    }
}
