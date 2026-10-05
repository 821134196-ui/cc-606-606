package com.medreturn.web.dto;

import java.util.List;

/** 预约/收件相关的请求与响应结构。 */
public final class AppointmentDtos {

    private AppointmentDtos() {
    }

    public record ItemLine(String medicineName, String category, Integer plannedQty, String unit) {
    }

    public record CreateAppointmentRequest(String residentName, String phone,
                                           List<ItemLine> items) {
    }

    public record ReceivedLine(Long itemId, Integer receivedQty) {
    }

    /** 现场核对：逐条登记实收数量。 */
    public record ReceiveRequest(String staffEmployeeNo, List<ReceivedLine> items) {
    }

    /** 预约与实收存在差异时，请居民本人确认。 */
    public record ConfirmDifferenceRequest(Boolean confirmed, String note) {
    }

    public record ItemView(Long id, String medicineName, String category, String categoryLabel,
                           boolean dangerous, boolean cold, String handlingTip,
                           int plannedQty, Integer receivedQty, int sealedQty, int availableQty,
                           String unit) {
    }

    public record AppointmentView(Long id, String code, String residentName, String status,
                                  String scheduledAt, boolean difference,
                                  Boolean differenceConfirmed, String differenceNote,
                                  boolean sealingAllowed,
                                  List<ItemView> items,
                                  int plannedTotal, int receivedTotal, int sealedTotal) {
    }
}
