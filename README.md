# 社区药箱 · 过期药回收与分类交接系统

社区回收点收取居民过期药后，记录**核对数量 → 分类封袋 → 保管 → 转交 → 处置单位签收**的全过程。
技术栈：**Java 17 + Quarkus 3 + Hibernate ORM/Panache + SQLite**，前端 **Svelte 4 + Vite**，扫码与通知均为本地模拟。

## 一条命令启动

```bash
./start.sh
```

自动完成前端构建、后端打包、SQLite 初始化（`data/medbox.db`），启动后访问 <http://localhost:8080>。

> 环境要求：JDK 17+、Maven 3.9+、Node 18+。脚本会自动识别 `~/tools` 下的便携 JDK/Maven。

演示账号（启动时自动写入）：

| 角色 | 工号 | 姓名 |
|---|---|---|
| 社区工作人员 | zhang / li / chen | 张敏 / 李强 / 陈芳 |
| 处置单位接收人 | wang | 王建国（绿环处置单位） |
| 居民 | 交回单号 `A-DEMO-0001`，查询码 `RC1001`（周岚）；`A-DEMO-0002` / `RC1002`（吴磊） |

## 现场业务流程（工作人员页）

1. **预约核对**：登记预约申报数量 → 现场录入实收数量。
2. **差异确认**：申报与实收不一致时，必须由**居民本人输入查询码**确认，工作人员不能代确认；确认后才能登记与封袋。
3. **分类登记**：
   - ☣ **危险药品**：页面有红色文字告警（佩戴手套口罩、防泄漏双层袋、单独封装、交接双人核对），登记时必须勾选防护确认，**不仅用颜色表示**，徽标含「☣ 危险药品」文字。
   - ❄ **需冷藏药品**：蓝色文字提示全程 2~8℃，封袋必须入柜测温，每次转交测温，沿袋号可查，徽标含「❄ 需冷藏」文字。
4. **分类封袋**：同类别一次性封入一袋。袋号关联**药品类别、件数、封袋人（交接起点）、保管位置、封条码**。
   - 封袋后内容冻结：不能追加药品进已封袋、无任何修改/删除袋内明细的入口。
5. **保管与转交**：每次转交都记录**交出人、接收人、时间、位置、签收码**；冷藏袋必须带 2~8℃ 温度，越限拒绝。
6. **拆袋（例外）**：确需拆袋时必须填写原因，由**两名不同工作人员**（操作人 + 复核人）双人确认并永久留痕；重新封袋生成新袋号/新封条，旧袋标记为被新袋承接。
7. **移交与签收**：移交处置单位需模拟扫描签收码；处置单位凭相同签收码签收，形成闭环。已移交/已签收的袋不能再拆。

## 居民端（页签「居民凭证查询」）

- 凭**本人交回单号 + 查询码**查询，只返回汇总凭证（类别、数量、封袋进度、是否已签收）。
- **不展示**药品具体品名、保管位置、内部交接人、冷链记录等内部资料。
- 他人单号 / 错误查询码 / 不存在单号一律 `403`（不区分提示，防止枚举）。可用 `RC1002` 查 `A-DEMO-0001` 验证被拒绝。

## 交付前验证

```bash
./verify.sh        # 等价于 mvn test
```

`WorkflowTest`（8 个有序用例，全部通过）可验证：

| 规则 | 用例 |
|---|---|
| 实收差异须居民本人确认，工作人员不能代确认 | `differenceMustBeConfirmedByResident` |
| 危险药品须防护确认；冷藏封袋缺温度/越限温度被拒绝 | `registerAndSeal` |
| 封袋后无法追加/改动，后补药品进不了旧袋 | `sealedContentsAreImmutableAndAppendForbidden` |
| 拆袋必须双人（同人被拒、缺原因被拒）并留记录，重封生成新袋 | `reopenRequiresTwoPeopleAndKeepsRecord` |
| 冷链沿袋号追查（入柜/巡检/交接温度、越限告警、位置更新） | `coldChainIsTraceableByBagNo` |
| 移交扫码、签收码核验、非接收人不能签收、移交后不可拆 | `handoverAndSignFlow` |
| 居民只能查本人凭证，凭证无任何内部字段 | `residentCanOnlySeeOwnReceipt` |

## 主要 API

- `POST /api/staff/appointments`、`POST …/{id}/check`、`POST …/{id}/confirm-difference`
- `POST …/{id}/drugs`、`POST …/{id}/seal`
- `POST /api/staff/bags/{bagNo}/reopen`（双人）、`…/reseal`
- `POST …/transfer`、`…/handover`、`…/sign`、`POST …/cold-logs`
- `GET  /api/staff/bags/{bagNo}/trace`（袋号全链路：交接链 + 冷链 + 双人拆袋记录）
- `POST /api/resident/receipt`、`POST /api/resident/notifications`（居民，单号+查询码）

## 目录结构

```
src/main/java/com/community/medbox/
├── model/       枚举：药品类别(危险/冷藏标记)、袋状态、转交类型
├── entity/      Resident/Appointment/CollectedDrug/SealedBag/BagReopenRecord/
│                TransferRecord/ColdChainLog/Notification
├── service/     业务规则（封袋冻结、双人拆袋、冷链、签收、居民权限）
├── resource/    REST 接口（工作人员 / 居民）
└── bootstrap/   演示数据
web/             Svelte 前端（构建产物输出到后端静态资源目录）
```
