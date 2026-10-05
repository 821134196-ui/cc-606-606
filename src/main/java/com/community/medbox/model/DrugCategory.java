package com.community.medbox.model;

/**
 * 药品分类（封袋按此分类，一袋对应一个类别）
 */
public enum DrugCategory {
    COMMON_SOLID("普通固体（片剂/胶囊/粉剂）", false, false),
    COMMON_LIQUID("普通液体（糖浆/口服液/注射液）", false, false),
    ANTIBIOTIC("抗菌药及特殊管理药品", false, false),
    HAZARDOUS("危险药品（细胞毒/易制毒/含汞等）", true, false),
    CHINESE_MED("中成药/中药饮片", false, false),
    REFRIGERATED("需冷藏药品（胰岛素/活菌制剂等）", false, true),
    OTHER("其他过期药品/耗材", false, false);

    public final String label;
    public final boolean hazardous;
    public final boolean refrigerated;

    DrugCategory(String label, boolean hazardous, boolean refrigerated) {
        this.label = label;
        this.hazardous = hazardous;
        this.refrigerated = refrigerated;
    }
}
