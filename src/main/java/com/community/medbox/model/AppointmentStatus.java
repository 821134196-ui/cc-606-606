package com.community.medbox.model;

public enum AppointmentStatus {
    SCHEDULED("已预约"),
    CHECKED("已核对实收"),
    COMPLETED("已完成交接");

    public final String label;

    AppointmentStatus(String label) {
        this.label = label;
    }
}
