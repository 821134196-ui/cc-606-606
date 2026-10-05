package com.medreturn.domain;

import java.util.List;

/**
 * 药品类别。危险药品 / 需冷藏药品不仅用颜色标记，
 * 这里携带强制的文字处理要求（handlingTip），现场页面与封袋结果都会展示。
 */
public enum MedicineCategory {

    ORAL_SOLID("口服固体制剂", false, false,
            "普通过期口服药，装入普通回收袋，常温干燥处保管。"),
    ORAL_LIQUID("口服液体制剂", false, false,
            "瓶口朝上竖直放置，封紧瓶盖防止泄漏，常温保管。"),
    EXTERNAL("外用药品", false, false,
            "外用与内服药必须分类装袋，防止交叉污染。"),
    INJECTION("注射剂", false, false,
            "注意防碎；针头、针管须放入硬质利器盒后再封袋。"),
    HAZARDOUS("危险药品", true, false,
            "【危险药品】含特殊管理、毒性、抗肿瘤等药品：必须双人核对、单独封袋、"
                  + "在袋身张贴“危险药品”文字标识、加锁专柜保管，严禁与普通药品混放。"),
    COLD("冷藏药品", false, true,
            "【需冷藏 2-8℃】胰岛素、生物制品等须立即放入 2-8℃ 冷藏柜；"
                  + "封袋、保管、交接全程记录温度，转运使用冷藏周转箱，冷链不得中断。");

    private final String label;
    private final boolean dangerous;
    private final boolean cold;
    private final String handlingTip;

    MedicineCategory(String label, boolean dangerous, boolean cold, String handlingTip) {
        this.label = label;
        this.dangerous = dangerous;
        this.cold = cold;
        this.handlingTip = handlingTip;
    }

    public String label() {
        return label;
    }

    public boolean dangerous() {
        return dangerous;
    }

    public boolean cold() {
        return cold;
    }

    public String handlingTip() {
        return handlingTip;
    }

    public static List<MedicineCategory> all() {
        return List.of(values());
    }
}
