package com.medreturn.domain;

public enum HandoverStatus {
    PENDING,    // 已转出，等待接收人扫码签收
    ACCEPTED,   // 接收人已签收
    REJECTED    // 接收人拒收（温度异常等）
}
