package com.community.medbox.service;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/** 业务规则冲突统一抛出 4xx，供前端展示中文提示。 */
public class BusinessException extends WebApplicationException {
    public BusinessException(String message) {
        super(Response.status(Response.Status.BAD_REQUEST)
                .type(MediaType.APPLICATION_JSON)
                .entity(java.util.Map.of("error", message))
                .build());
    }

    public BusinessException(int status, String message) {
        super(Response.status(status)
                .type(MediaType.APPLICATION_JSON)
                .entity(java.util.Map.of("error", message))
                .build());
    }
}
