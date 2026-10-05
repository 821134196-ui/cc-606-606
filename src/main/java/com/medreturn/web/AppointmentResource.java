package com.medreturn.web;

import com.medreturn.service.AppointmentService;
import com.medreturn.web.dto.AppointmentDtos.*;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

import java.util.List;

/** 预约登记、现场核对、差异居民确认。 */
@Path("/api/appointments")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AppointmentResource {

    @Inject
    AppointmentService appointments;

    @POST
    public AppointmentView create(CreateAppointmentRequest req) {
        return appointments.create(req);
    }

    @GET
    public List<AppointmentView> list() {
        return appointments.list();
    }

    @GET
    @Path("/{code}")
    public AppointmentView get(@PathParam("code") String code) {
        return appointments.view(code);
    }

    /** 现场核对实收数量。 */
    @PUT
    @Path("/{code}/receive")
    public AppointmentView receive(@PathParam("code") String code, ReceiveRequest req) {
        return appointments.receive(code, req);
    }

    /** 预约与实收存在差异时，请居民本人确认。 */
    @PUT
    @Path("/{code}/confirm-difference")
    public AppointmentView confirmDifference(@PathParam("code") String code,
                                             ConfirmDifferenceRequest req) {
        return appointments.confirmDifference(code, req);
    }
}
