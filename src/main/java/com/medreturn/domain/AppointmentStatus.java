package com.medreturn.domain;

public enum AppointmentStatus {
    PENDING,    // 已预约，待收件核对
    RECEIVED,   // 已核对收件（差异已由居民确认）
    COMPLETED   // 全部封袋均被处置单位签收
}
