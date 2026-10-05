package com.medreturn.domain;

public enum BagStatus {
    SEALED,      // 已封袋（内容冻结，禁止追加/改动）
    OPENED,      // 双人复核拆袋中（仅此状态允许变更内容，并逐条留痕）
    SIGNED_OFF   // 处置单位已签收
}
