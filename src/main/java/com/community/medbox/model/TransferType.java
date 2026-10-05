package com.community.medbox.model;

/**
 * 转交类型：社区内部保管转交 / 移交处置单位 / 处置单位签收
 */
public enum TransferType {
    INTERNAL("内部保管转交"),
    HANDOVER("移交处置单位"),
    SIGN("处置单位签收");

    public final String label;

    TransferType(String label) {
        this.label = label;
    }
}
