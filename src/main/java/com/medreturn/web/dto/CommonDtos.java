package com.medreturn.web.dto;

import java.util.List;

/** 类别字典、人员、通知、居民凭证等公共结构。 */
public final class CommonDtos {

    private CommonDtos() {
    }

    public record CategoryView(String code, String label, boolean dangerous, boolean cold,
                               String handlingTip) {
    }

    public record StaffView(Long id, String name, String employeeNo, String role,
                            String organization) {
    }

    public record NotificationView(Long id, String target, String channel, String audience,
                                   String title, String content, String createdAt, boolean read) {
    }

    public record MessageResponse(String message) {
    }

    /**
     * 居民交回凭证（对外视图）：只含本人预约的交回与处置进度，
     * 刻意不包含任何工作人员姓名、库位、交接链等内部资料。
     */
    public record ReceiptBagView(String bagNo, String categoryLabel, int qty, String unit,
                                 boolean signedOff, String signedOffAt) {
    }

    public record ReceiptView(String appointmentCode, String residentName, String status,
                              String scheduledAt, String receivedAt,
                              Boolean differenceConfirmed, String differenceNote,
                              int plannedTotal, int receivedTotal,
                              List<ReceiptBagView> bags) {
    }
}
