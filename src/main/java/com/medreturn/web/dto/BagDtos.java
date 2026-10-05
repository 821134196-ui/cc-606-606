package com.medreturn.web.dto;

import java.util.List;

/** 封袋 / 拆袋 / 追溯相关的请求与响应结构。 */
public final class BagDtos {

    private BagDtos() {
    }

    public record SealEntryRequest(Long itemId, Integer qty) {
    }

    /**
     * 按类别封袋。危险药品必须携带 witnessEmployeeNo（双人核对）；
     * 冷藏药品必须携带 temperature（2-8℃），storageLocation 须为冷链库位。
     */
    public record SealBagRequest(String appointmentCode, String category,
                                 String staffEmployeeNo, String witnessEmployeeNo,
                                 String storageLocation, Double temperature,
                                 List<SealEntryRequest> entries) {
    }

    /** 拆袋必须两名不同工作人员：操作人 + 复核人。 */
    public record OpenBagRequest(String operatorEmployeeNo, String witnessEmployeeNo,
                                 String reason) {
    }

    /** 拆袋期间调整袋内某药品数量（仅 OPEN 状态允许）。 */
    public record AdjustEntryRequest(String operatorEmployeeNo, Long itemId, Integer qty) {
    }

    public record ResealBagRequest(String operatorEmployeeNo, String witnessEmployeeNo,
                                   String reason, String storageLocation, Double temperature) {
    }

    public record EntryView(Long itemId, String medicineName, int qty, String unit) {
    }

    public record OperationView(Long id, String type, String operatorName, String witnessName,
                                String reason, String operatedAt) {
    }

    public record ChangeView(Long id, String medicineName, int qtyBefore, int qtyAfter,
                             String operatorName, String changedAt) {
    }

    public record BagView(String bagNo, String category, String categoryLabel,
                          boolean dangerous, boolean cold, String handlingTip,
                          String status, int totalQty, String unit,
                          String sealedAt, String sealedByName, String witnessName,
                          String storageLocation, Double sealTemperature,
                          String appointmentCode, String signedOffAt,
                          List<EntryView> entries) {
    }

    /** 沿袋号追查：封袋信息 + 操作流水 + 内容变更 + 交接链。 */
    public record BagTraceView(BagView bag, String currentLocation, String currentHolderName,
                               List<OperationView> operations,
                               List<ChangeView> changes,
                               List<HandoverDtos.HandoverView> handovers) {
    }
}
