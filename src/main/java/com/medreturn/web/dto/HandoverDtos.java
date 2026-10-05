package com.medreturn.web.dto;

/** 交接（含冷链）相关的请求与响应结构。 */
public final class HandoverDtos {

    private HandoverDtos() {
    }

    /**
     * 发起转交。冷藏袋必须携带 temperature 与 coldChainDevice，
     * toLocation 必须是冷链库位；温度超出 2-8℃ 直接拒绝。
     */
    public record HandoverRequest(String bagNo, String fromEmployeeNo, String toEmployeeNo,
                                  String toLocation, Double temperature,
                                  String coldChainDevice, String handoverType) {
    }

    /** 接收人扫码签收（本地模拟扫码：直接提交袋号/交接单号）。 */
    public record AcceptRequest(String employeeNo, boolean accepted, Double temperature,
                                String reason) {
    }

    public record HandoverView(Long id, String bagNo, String handoverType, String status,
                               String fromStaffName, String toStaffName,
                               String fromLocation, String toLocation,
                               String transferredAt, String acceptedAt,
                               Double transferTemperature, String coldChainDevice,
                               String rejectReason, boolean cold) {
    }
}
