package com.community.medbox.model;

/**
 * 袋状态：封袋 -> 拆袋(待处理) -> 重新封袋；封袋 -> 移交处置单位 -> 签收
 */
public enum BagStatus {
    SEALED("已封袋在库"),
    REOPENED("已拆袋待处理"),
    SUPERSEDED("拆袋后已由新袋承接"),
    HANDED_OVER("已移交处置单位"),
    SIGNED("处置单位已签收");

    public final String label;

    BagStatus(String label) {
        this.label = label;
    }
}
