package com.medreturn;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

/**
 * 交付前可验证结果：
 * 1) 封袋后内容无法随意改动（追加 → 409）；
 * 2) 拆袋必须双人（同一人 → 409，两名不同人员 → 成功且留痕）；
 * 3) 冷藏交接温度/冷链强制 + 沿袋号可追溯完整冷链轨迹；
 * 4) 居民只能凭查询码查询本人凭证，且看不到他人信息与内部交接资料。
 */
@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class WorkflowTest {

    static String apptCode;
    static Long normalItemId;
    static Long hazardItemId;
    static Long coldItemId;
    static String normalBagNo;
    static String coldBagNo;
    static String hazardBagNo;
    static Long internalHandoverId;

    @Test
    @Order(1)
    void categoriesCarryTextWarnings() {
        // 危险/冷藏处理要求以文字提供，不仅依赖颜色
        given().get("/api/categories").then().statusCode(200)
                .body("find { it.code == 'HAZARDOUS' }.dangerous", is(true))
                .body("find { it.code == 'HAZARDOUS' }.handlingTip", containsString("危险药品"))
                .body("find { it.code == 'COLD' }.cold", is(true))
                .body("find { it.code == 'COLD' }.handlingTip", containsString("2-8"));
    }

    @Test
    @Order(10)
    void createAppointment() {
        Map<String, Object> body = Map.of(
                "residentName", "测试居民",
                "phone", "13900000000",
                "items", List.of(
                        Map.of("medicineName", "过期感冒药", "category", "ORAL_SOLID",
                                "plannedQty", 4, "unit", "盒"),
                        Map.of("medicineName", "过期抗肿瘤药", "category", "HAZARDOUS",
                                "plannedQty", 1, "unit", "盒"),
                        Map.of("medicineName", "胰岛素", "category", "COLD",
                                "plannedQty", 3, "unit", "支")));
        var resp = given().contentType("application/json").body(body)
                .post("/api/appointments").then().statusCode(200)
                .body("code", notNullValue())
                .body("status", is("PENDING"))
                .extract();
        apptCode = resp.path("code");
        normalItemId = ((Number) resp.path("items.find { it.category == 'ORAL_SOLID' }.id")).longValue();
        hazardItemId = ((Number) resp.path("items.find { it.category == 'HAZARDOUS' }.id")).longValue();
        coldItemId = ((Number) resp.path("items.find { it.category == 'COLD' }.id")).longValue();
    }

    @Test
    @Order(11)
    void sealingBlockedBeforeReceive() {
        given().contentType("application/json").body(Map.of(
                        "appointmentCode", apptCode, "category", "ORAL_SOLID",
                        "staffEmployeeNo", "W001", "storageLocation", "A-常温柜-01",
                        "entries", List.of(Map.of("itemId", normalItemId, "qty", 1))))
                .post("/api/bags").then().statusCode(409);
    }

    @Test
    @Order(12)
    void receiveWithDifference() {
        // 感冒药预约 4 实收 3，制造差异
        given().contentType("application/json").body(Map.of(
                        "staffEmployeeNo", "W001",
                        "items", List.of(
                                Map.of("itemId", normalItemId, "receivedQty", 3),
                                Map.of("itemId", hazardItemId, "receivedQty", 1),
                                Map.of("itemId", coldItemId, "receivedQty", 3))))
                .put("/api/appointments/" + apptCode + "/receive").then().statusCode(200)
                .body("difference", is(true))
                .body("differenceConfirmed", is(false))
                .body("sealingAllowed", is(false));
    }

    @Test
    @Order(13)
    void sealingBlockedUntilResidentConfirmsDifference() {
        given().contentType("application/json").body(Map.of(
                        "appointmentCode", apptCode, "category", "ORAL_SOLID",
                        "staffEmployeeNo", "W001", "storageLocation", "A-常温柜-01",
                        "entries", List.of(Map.of("itemId", normalItemId, "qty", 1))))
                .post("/api/bags").then().statusCode(409)
                .body("message", containsString("居民确认"));
    }

    @Test
    @Order(14)
    void residentConfirmsDifference() {
        given().contentType("application/json")
                .body(Map.of("confirmed", true, "note", "居民认可实收 3 盒"))
                .put("/api/appointments/" + apptCode + "/confirm-difference").then().statusCode(200)
                .body("sealingAllowed", is(true));
    }

    @Test
    @Order(20)
    void sealNormalBagBlocksAppendAfterwards() {
        var resp = given().contentType("application/json").body(Map.of(
                        "appointmentCode", apptCode, "category", "ORAL_SOLID",
                        "staffEmployeeNo", "W001", "storageLocation", "A-常温柜-01",
                        "entries", List.of(Map.of("itemId", normalItemId, "qty", 2))))
                .post("/api/bags").then().statusCode(200)
                .body("status", is("SEALED"))
                .body("totalQty", is(2))
                .body("handlingTip", not(emptyString()))
                .extract();
        normalBagNo = resp.path("bagNo");

        // 封袋后直接追加 → 409，内容无法随意改动
        given().contentType("application/json").body(Map.of(
                        "itemId", normalItemId, "qty", 1, "operatorEmployeeNo", "W001"))
                .post("/api/bags/" + normalBagNo + "/entries").then().statusCode(409)
                .body("message", containsString("禁止追加"));

        // 数量也不能超过可用实收（3）：已装 2，再开一袋装 2 应被拒
        given().contentType("application/json").body(Map.of(
                        "appointmentCode", apptCode, "category", "ORAL_SOLID",
                        "staffEmployeeNo", "W001", "storageLocation", "A-常温柜-02",
                        "entries", List.of(Map.of("itemId", normalItemId, "qty", 2))))
                .post("/api/bags").then().statusCode(409);
    }

    @Test
    @Order(30)
    void openingRequiresTwoDistinctStaffAndLeavesRecord() {
        // 同一人不能既操作又复核 → 409
        given().contentType("application/json").body(Map.of(
                        "operatorEmployeeNo", "W001", "witnessEmployeeNo", "W001",
                        "reason", "误装，需取出"))
                .post("/api/bags/" + normalBagNo + "/open").then().statusCode(409)
                .body("message", containsString("两名不同"));

        // 缺少复核人 → 400
        given().contentType("application/json").body(Map.of(
                        "operatorEmployeeNo", "W001", "reason", "误装"))
                .post("/api/bags/" + normalBagNo + "/open").then().statusCode(400);

        // 两名不同工作人员复核拆袋 → 成功
        given().contentType("application/json").body(Map.of(
                        "operatorEmployeeNo", "W001", "witnessEmployeeNo", "W002",
                        "reason", "居民指认其中 1 盒并非过期药，需取出"))
                .post("/api/bags/" + normalBagNo + "/open").then().statusCode(200)
                .body("status", is("OPENED"));

        // 拆袋期间调整 2 → 1（取出 1 盒），留下内容变更记录
        given().contentType("application/json").body(Map.of(
                        "operatorEmployeeNo", "W001", "itemId", normalItemId, "qty", 1))
                .put("/api/bags/" + normalBagNo + "/entries").then().statusCode(200)
                .body("totalQty", is(1));

        // 重新封袋仍须双人
        given().contentType("application/json").body(Map.of(
                        "operatorEmployeeNo", "W001", "witnessEmployeeNo", "W002",
                        "reason", "复核无误，重新封袋", "storageLocation", "A-常温柜-01"))
                .post("/api/bags/" + normalBagNo + "/reseal").then().statusCode(200)
                .body("status", is("SEALED"));

        // 拆袋留痕可查：OPEN / RESEAL 均有两名工作人员与原因
        given().get("/api/bags/" + normalBagNo + "/trace").then().statusCode(200)
                .body("operations.type", hasItems("SEAL", "OPEN", "RESEAL"))
                .body("operations.find { it.type == 'OPEN' }.witnessName", is("李晓梅"))
                .body("operations.find { it.type == 'OPEN' }.reason", containsString("过期药"))
                .body("changes[0].qtyBefore", is(2))
                .body("changes[0].qtyAfter", is(1));
    }

    @Test
    @Order(40)
    void hazardousBagRequiresWitness() {
        // 危险药品无双核对 → 400
        given().contentType("application/json").body(Map.of(
                        "appointmentCode", apptCode, "category", "HAZARDOUS",
                        "staffEmployeeNo", "W001", "storageLocation", "HZ-加锁专柜-01",
                        "entries", List.of(Map.of("itemId", hazardItemId, "qty", 1))))
                .post("/api/bags").then().statusCode(400)
                .body("message", containsString("双人核对"));

        // 双人核对后成功，且记录复核人
        var resp = given().contentType("application/json").body(Map.of(
                        "appointmentCode", apptCode, "category", "HAZARDOUS",
                        "staffEmployeeNo", "W001", "witnessEmployeeNo", "W003",
                        "storageLocation", "HZ-加锁专柜-01",
                        "entries", List.of(Map.of("itemId", hazardItemId, "qty", 1))))
                .post("/api/bags").then().statusCode(200)
                .body("dangerous", is(true))
                .body("witnessName", is("陈芳"))
                .body("handlingTip", containsString("加锁专柜"))
                .extract();
        hazardBagNo = resp.path("bagNo");
    }

    @Test
    @Order(50)
    void coldBagAndColdChainHandover() {
        // 冷藏袋不记录温度 → 400
        given().contentType("application/json").body(Map.of(
                        "appointmentCode", apptCode, "category", "COLD",
                        "staffEmployeeNo", "W001", "storageLocation", "COLD-冷藏柜-01",
                        "entries", List.of(Map.of("itemId", coldItemId, "qty", 3))))
                .post("/api/bags").then().statusCode(400);

        // 温度超区间 → 409
        given().contentType("application/json").body(Map.of(
                        "appointmentCode", apptCode, "category", "COLD",
                        "staffEmployeeNo", "W001", "storageLocation", "COLD-冷藏柜-01",
                        "temperature", 15.0,
                        "entries", List.of(Map.of("itemId", coldItemId, "qty", 3))))
                .post("/api/bags").then().statusCode(409)
                .body("message", containsString("2-8"));

        // 常温库位 → 400
        given().contentType("application/json").body(Map.of(
                        "appointmentCode", apptCode, "category", "COLD",
                        "staffEmployeeNo", "W001", "storageLocation", "A-常温柜-01",
                        "temperature", 5.0,
                        "entries", List.of(Map.of("itemId", coldItemId, "qty", 3))))
                .post("/api/bags").then().statusCode(400);

        // 合规封袋
        var resp = given().contentType("application/json").body(Map.of(
                        "appointmentCode", apptCode, "category", "COLD",
                        "staffEmployeeNo", "W001", "storageLocation", "COLD-冷藏柜-01",
                        "temperature", 5.0,
                        "entries", List.of(Map.of("itemId", coldItemId, "qty", 3))))
                .post("/api/bags").then().statusCode(200)
                .body("cold", is(true))
                .body("sealTemperature", is(5.0f))
                .extract();
        coldBagNo = resp.path("bagNo");

        // 交接缺温度/冷链设备 → 400
        given().contentType("application/json").body(Map.of(
                        "bagNo", coldBagNo, "fromEmployeeNo", "W001", "toEmployeeNo", "W002",
                        "toLocation", "COLD-冷藏柜-02", "handoverType", "INTERNAL"))
                .post("/api/handovers").then().statusCode(400);

        // 交接温度异常 → 409
        given().contentType("application/json").body(Map.of(
                        "bagNo", coldBagNo, "fromEmployeeNo", "W001", "toEmployeeNo", "W002",
                        "toLocation", "COLD-冷藏柜-02", "temperature", 10.0,
                        "coldChainDevice", "冷藏周转箱 CC-02", "handoverType", "INTERNAL"))
                .post("/api/handovers").then().statusCode(409);

        // 合规内部交接
        var h = given().contentType("application/json").body(Map.of(
                        "bagNo", coldBagNo, "fromEmployeeNo", "W001", "toEmployeeNo", "W002",
                        "toLocation", "COLD-冷藏柜-02", "temperature", 4.5,
                        "coldChainDevice", "冷藏周转箱 CC-02", "handoverType", "INTERNAL"))
                .post("/api/handovers").then().statusCode(200)
                .body("status", is("PENDING"))
                .body("cold", is(true))
                .body("transferTemperature", is(4.5f))
                .body("coldChainDevice", is("冷藏周转箱 CC-02"))
                .extract();
        internalHandoverId = ((Number) h.path("id")).longValue();

        // 非指定接收人不能签收 → 403
        given().contentType("application/json")
                .body(Map.of("employeeNo", "W003", "accepted", true, "temperature", 5.0))
                .put("/api/handovers/" + internalHandoverId + "/accept").then().statusCode(403);

        // 指定接收人复测温度合格后签收
        given().contentType("application/json")
                .body(Map.of("employeeNo", "W002", "accepted", true, "temperature", 5.2))
                .put("/api/handovers/" + internalHandoverId + "/accept").then().statusCode(200)
                .body("status", is("ACCEPTED"));

        // 沿袋号追溯完整冷链轨迹：封袋温度 + 交接链温度/设备/库位/人员
        given().get("/api/bags/" + coldBagNo + "/trace").then().statusCode(200)
                .body("currentLocation", is("COLD-冷藏柜-02"))
                .body("currentHolderName", is("李晓梅"))
                .body("handovers.size()", is(1))
                .body("handovers[0].fromStaffName", is("王建国"))
                .body("handovers[0].toStaffName", is("李晓梅"))
                .body("handovers[0].coldChainDevice", is("冷藏周转箱 CC-02"))
                .body("handovers[0].transferTemperature", is(4.5f))
                .body("bag.sealTemperature", is(5.0f));
    }

    @Test
    @Order(60)
    void disposalSignoffCompletesChain() {
        // 普通袋、危险药品袋、冷藏袋全部送交处置单位（冷藏袋需温度/冷链设备）
        for (String bagNo : new String[]{normalBagNo, hazardBagNo, coldBagNo}) {
            boolean cold = bagNo.equals(coldBagNo);
            var body = new java.util.HashMap<String, Object>();
            body.put("bagNo", bagNo);
            body.put("fromEmployeeNo", "W002");
            body.put("toEmployeeNo", "D001");
            body.put("toLocation", cold ? "COLD-处置冷库-01" : "DISPOSAL-暂存间-01");
            body.put("handoverType", "TO_DISPOSAL");
            if (cold) {
                body.put("temperature", 4.0);
                body.put("coldChainDevice", "冷藏周转箱 CC-09");
            }
            Long id = ((Number) given().contentType("application/json").body(body)
                    .post("/api/handovers").then().statusCode(200)
                    .body("handoverType", is("TO_DISPOSAL"))
                    .extract().path("id")).longValue();

            // 处置签收
            var acceptBody = new java.util.HashMap<String, Object>();
            acceptBody.put("employeeNo", "D001");
            acceptBody.put("accepted", true);
            if (cold) {
                acceptBody.put("temperature", 4.8);
            }
            given().contentType("application/json").body(acceptBody)
                    .put("/api/handovers/" + id + "/accept").then().statusCode(200)
                    .body("status", is("ACCEPTED"));
        }

        // 全部签收 → 预约完成；袋状态 SIGNED_OFF
        given().get("/api/bags/" + coldBagNo + "/trace").then().statusCode(200)
                .body("bag.status", is("SIGNED_OFF"));
        given().get("/api/appointments/" + apptCode).then().statusCode(200)
                .body("status", is("COMPLETED"));
    }

    @Test
    @Order(70)
    void residentCanOnlyQueryOwnReceiptWithoutInternalData() {
        // 演示居民 张桂兰 YY100001 / 111111；李国富 YY100002 / 222222
        String body = given().contentType("application/json")
                .body(Map.of("appointmentCode", "YY100001", "tokenCode", "111111"))
                .post("/api/resident/receipt").then().statusCode(200)
                .body("appointmentCode", is("YY100001"))
                .body("residentName", is("张桂兰"))
                .body("differenceConfirmed", is(true))
                .extract().asString();

        // 响应不得包含内部交接资料：库位、工作人员、交接链、工号、温度
        org.junit.jupiter.api.Assertions.assertFalse(body.contains("storageLocation"),
                "居民凭证不得暴露保管库位");
        org.junit.jupiter.api.Assertions.assertFalse(body.contains("王建国"),
                "居民凭证不得暴露工作人员姓名");
        org.junit.jupiter.api.Assertions.assertFalse(body.contains("W001"),
                "居民凭证不得暴露工号");
        org.junit.jupiter.api.Assertions.assertFalse(body.contains("handover"),
                "居民凭证不得暴露内部交接链");
        org.junit.jupiter.api.Assertions.assertFalse(body.contains("sealTemperature"),
                "居民凭证不得暴露内部温度记录");

        // 错误查询码 → 403
        given().contentType("application/json")
                .body(Map.of("appointmentCode", "YY100001", "tokenCode", "000000"))
                .post("/api/resident/receipt").then().statusCode(403);

        // 用居民 2 的查询码查居民 1 的凭证 → 403（看不到他人明细）
        given().contentType("application/json")
                .body(Map.of("appointmentCode", "YY100001", "tokenCode", "222222"))
                .post("/api/resident/receipt").then().statusCode(403);

        // 不存在的凭证编号同样 403，不暴露编号是否存在
        given().contentType("application/json")
                .body(Map.of("appointmentCode", "YY999999", "tokenCode", "111111"))
                .post("/api/resident/receipt").then().statusCode(403);
    }
}
