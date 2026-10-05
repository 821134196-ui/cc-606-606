package com.medreturn.web;

import com.medreturn.domain.MedicineCategory;
import com.medreturn.entity.Staff;
import com.medreturn.web.dto.CommonDtos.CategoryView;
import com.medreturn.web.dto.CommonDtos.StaffView;
import jakarta.enterprise.context.RequestScoped;
import jakarta.persistence.EntityManager;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import java.util.Arrays;
import java.util.List;

/** 基础字典：药品类别（含危险/冷藏的文字处理要求）、工作人员。 */
@Path("/api")
@Produces(MediaType.APPLICATION_JSON)
@RequestScoped
public class DictResource {

    @Inject
    EntityManager em;

    @GET
    @Path("/categories")
    public List<CategoryView> categories() {
        return Arrays.stream(MedicineCategory.values())
                .map(c -> new CategoryView(c.name(), c.label(), c.dangerous(), c.cold(),
                        c.handlingTip()))
                .toList();
    }

    @GET
    @Path("/staff")
    public List<StaffView> staff() {
        return em.createQuery("select s from Staff s order by s.role, s.id", Staff.class)
                .getResultList().stream()
                .map(s -> new StaffView(s.getId(), s.getName(), s.getEmployeeNo(),
                        s.getRole().name(), s.getOrganization()))
                .toList();
    }
}
