<script>
  import { onMount } from 'svelte';
  import { createEventDispatcher } from 'svelte';
  import { api } from '../api.js';
  import { toast } from '../lib/toast.js';
  import CategoryBadge from '../lib/CategoryBadge.svelte';

  export let bagNo;
  export let staff = [];

  const dispatch = createEventDispatcher();

  let trace = null;

  // 拆袋表单
  let openOp = 'W001';
  let openWitness = 'W002';
  let openReason = '';
  // 调整数量
  let adjustQty = {};
  // 重封表单
  let resealOp = 'W001';
  let resealWitness = 'W002';
  let resealReason = '';
  let resealTemp = null;
  let resealLocation = '';

  async function load() {
    try {
      trace = await api.get(`/api/bags/${bagNo}/trace`);
      const m = {};
      for (const e of trace.bag.entries) m[e.itemId] = e.qty;
      adjustQty = m;
      resealLocation = trace.bag.storageLocation;
    } catch (e) {
      toast(e.message, 'error');
    }
  }
  onMount(load);

  async function openBag() {
    try {
      await api.post(`/api/bags/${bagNo}/open`, {
        operatorEmployeeNo: openOp,
        witnessEmployeeNo: openWitness,
        reason: openReason
      });
      toast('已由两名工作人员复核拆袋，拆袋期间的变更将逐条留痕');
      openReason = '';
      await load();
      dispatch('changed');
    } catch (e) {
      toast(e.message, 'error');
    }
  }

  async function adjust(itemId) {
    try {
      await api.put(`/api/bags/${bagNo}/entries`, {
        operatorEmployeeNo: openOp,
        itemId,
        qty: Number(adjustQty[itemId] ?? 0)
      });
      toast('袋内数量已调整，已记录内容变更');
      await load();
      dispatch('changed');
    } catch (e) {
      toast(e.message, 'error');
    }
  }

  async function tryAppendFrozen() {
    // 现场验证：封袋状态下服务器直接 409 拒绝
    try {
      await api.post(`/api/bags/${bagNo}/entries`, {
        itemId: trace.bag.entries[0]?.itemId,
        qty: 1,
        operatorEmployeeNo: openOp
      });
      toast('追加成功（当前处于拆袋状态）');
      await load();
    } catch (e) {
      toast(`已被系统拦截：${e.message}`, 'error', 6000);
    }
  }

  async function reseal() {
    try {
      const body = {
        operatorEmployeeNo: resealOp,
        witnessEmployeeNo: resealWitness,
        reason: resealReason,
        storageLocation: resealLocation
      };
      if (trace.bag.cold) body.temperature = resealTemp === '' || resealTemp == null ? null : Number(resealTemp);
      await api.post(`/api/bags/${bagNo}/reseal`, body);
      toast('双人复核后已重新封袋');
      resealReason = '';
      await load();
      dispatch('changed');
    } catch (e) {
      toast(e.message, 'error');
    }
  }

  $: opened = trace?.bag.status === 'OPENED';
  $: sealed = trace?.bag.status === 'SEALED';
  $: signedOff = trace?.bag.status === 'SIGNED_OFF';
</script>

<div class="modal-mask" on:click|self={() => dispatch('close')}>
  <div class="modal">
    {#if trace}
      <h2>
        袋号追溯 <span class="mono code-chip">{trace.bag.bagNo}</span>
        <CategoryBadge category={trace.bag.category} label={trace.bag.categoryLabel}
                       dangerous={trace.bag.dangerous} cold={trace.bag.cold} />
        {#if sealed}<span class="tag ok">已封袋·内容冻结</span>{/if}
        {#if opened}<span class="tag warn">双人拆袋中</span>{/if}
        {#if signedOff}<span class="tag plain">处置已签收</span>{/if}
        <span class="spacer"></span>
        <button class="btn ghost small" on:click={() => dispatch('close')}>关闭</button>
      </h2>

      <div class="grid two">
        <div class="panel">
          <h3>封袋信息</h3>
          <dl class="kv">
            <dt>所属凭证</dt><dd class="mono">{trace.bag.appointmentCode}</dd>
            <dt>袋内数量</dt><dd>{trace.bag.totalQty} {trace.bag.unit}</dd>
            <dt>封袋人</dt><dd>{trace.bag.sealedByName}</dd>
            <dt>复核人</dt><dd>{trace.bag.witnessName || '—'}{trace.bag.dangerous ? '（危险药品双人核对）' : ''}</dd>
            <dt>封袋时间</dt><dd>{trace.bag.sealedAt}</dd>
            <dt>当前保管位置</dt><dd class="mono"><strong>{trace.currentLocation}</strong></dd>
            <dt>当前持有人</dt><dd>{trace.currentHolderName}</dd>
            {#if trace.bag.sealTemperature != null}
              <dt>封袋温度</dt><dd>❄ {trace.bag.sealTemperature} ℃（冷链 2-8℃）</dd>
            {/if}
            {#if trace.bag.signedOffAt}<dt>处置签收时间</dt><dd>{trace.bag.signedOffAt}</dd>{/if}
          </dl>

          <h3 style="margin-top:.7rem;">袋内明细</h3>
          <table>
            <thead><tr><th>药品</th>{#if opened}<th style="width:130px;">调整为</th><th style="width:70px;"></th>{:else}<th style="width:70px;">数量</th>{/if}</tr></thead>
            <tbody>
              {#each trace.bag.entries as e}
                <tr>
                  <td>{e.medicineName}</td>
                  {#if opened}
                    <td><input type="number" min="0" bind:value={adjustQty[e.itemId]} /></td>
                    <td><button class="btn small secondary" on:click={() => adjust(e.itemId)}>保存</button></td>
                  {:else}
                    <td>{e.qty} {e.unit}</td>
                  {/if}
                </tr>
              {/each}
            </tbody>
          </table>
          {#if sealed}
            <div class="banner info" style="margin-top:.6rem;">
              封袋后内容冻结，<strong>禁止追加或改动药品</strong>。确需变更请由两名不同工作人员在下方复核拆袋。
              <div class="btn-row">
                <button class="btn small ghost" on:click={tryAppendFrozen}>现场验证：尝试直接追加（应被拦截）</button>
              </div>
            </div>
          {/if}
          {#if signedOff}
            <div class="banner warn" style="margin-top:.6rem;">该袋已被处置单位签收，不得拆袋或交接。</div>
          {/if}
        </div>

        <div class="panel">
          <h3>操作流水（封袋 / 双人拆袋 / 重封留痕）</h3>
          <div class="timeline">
            {#each trace.operations as o}
              <div class="node">
                <strong>
                  {#if o.type === 'SEAL'}封袋{:else if o.type === 'OPEN'}▲ 双人复核拆袋{:else}双人复核重新封袋{/if}
                </strong>
                {#if o.witnessName}<span class="tag ok">双人：{o.operatorName} ＋ 复核 {o.witnessName}</span>{:else}<span class="muted">操作人 {o.operatorName}</span>{/if}
                <div>{o.reason}</div>
                <div class="t">{o.operatedAt}</div>
              </div>
            {/each}
          </div>

          {#if trace.changes.length > 0}
            <h3 style="margin-top:.8rem;">拆袋内容变更记录</h3>
            <table>
              <thead><tr><th>药品</th><th>调整前</th><th>调整后</th><th>操作人</th><th>时间</th></tr></thead>
              <tbody>
                {#each trace.changes as c}
                  <tr><td>{c.medicineName}</td><td>{c.qtyBefore}</td><td><strong>{c.qtyAfter}</strong></td><td>{c.operatorName}</td><td class="small muted">{c.changedAt}</td></tr>
                {/each}
              </tbody>
            </table>
          {/if}

          <h3 style="margin-top:.8rem;">交接链（含冷藏温度 / 冷链设备）</h3>
          {#if trace.handovers.length === 0}
            <div class="muted small">暂无交接记录，仍在回收点保管。</div>
          {:else}
            <div class="timeline">
              {#each trace.handovers as h}
                <div class="node">
                  <strong>{h.handoverType === 'TO_DISPOSAL' ? '送交处置单位' : '内部保管转交'}</strong>
                  {#if h.status === 'PENDING'}<span class="tag warn">待接收人扫码签收</span>
                  {:else if h.status === 'ACCEPTED'}<span class="tag ok">已签收</span>
                  {:else}<span class="tag danger">已拒收</span>{/if}
                  <div>{h.fromStaffName}（{h.fromLocation}）→ {h.toStaffName}（{h.toLocation}）</div>
                  {#if h.cold}
                    <div class="cold-line">❄ 交出 {h.transferTemperature}℃ · 冷链设备：{h.coldChainDevice}</div>
                  {/if}
                  {#if h.rejectReason}<div class="muted">拒收原因：{h.rejectReason}</div>{/if}
                  <div class="t">转出 {h.transferredAt}{#if h.acceptedAt} · 签收 {h.acceptedAt}{/if}</div>
                </div>
              {/each}
            </div>
          {/if}
        </div>
      </div>

      <!-- 双人拆袋 -->
      {#if sealed}
        <div class="panel" style="background:#fafbfc;">
          <h3>确需拆袋：两名不同工作人员复核（全程留痕）</h3>
          <div class="grid two">
            <label>操作人工号
              <select bind:value={openOp}>
                {#each staff.filter((s) => s.role === 'SITE') as s}<option value={s.employeeNo}>{s.employeeNo} · {s.name}</option>{/each}
              </select>
            </label>
            <label>复核人工号（必须与操作人不同）
              <select bind:value={openWitness}>
                {#each staff.filter((s) => s.role === 'SITE') as s}<option value={s.employeeNo}>{s.employeeNo} · {s.name}</option>{/each}
              </select>
            </label>
          </div>
          <label>拆袋原因（必填，留档）<textarea bind:value={openReason} placeholder="如：居民指认其中 1 盒并非过期药，需取出" /></label>
          <div class="btn-row"><button class="btn danger" on:click={openBag}>双人复核拆袋</button></div>
        </div>
      {/if}

      <!-- 双人重封 -->
      {#if opened}
        <div class="panel" style="background:#fafbfc;">
          <h3>调整完成：双人复核重新封袋</h3>
          <div class="grid two">
            <label>操作人工号
              <select bind:value={resealOp}>
                {#each staff.filter((s) => s.role === 'SITE') as s}<option value={s.employeeNo}>{s.employeeNo} · {s.name}</option>{/each}
              </select>
            </label>
            <label>复核人工号（必须与操作人不同）
              <select bind:value={resealWitness}>
                {#each staff.filter((s) => s.role === 'SITE') as s}<option value={s.employeeNo}>{s.employeeNo} · {s.name}</option>{/each}
              </select>
            </label>
            <label>保管位置 / 库位
              <input bind:value={resealLocation} placeholder={trace.bag.cold ? 'COLD-冷藏柜-01' : 'A-常温柜-01'} />
            </label>
            {#if trace.bag.cold}
              <label>复测温度（℃，2-8℃）<input type="number" step="0.1" min="2" max="8" bind:value={resealTemp} placeholder="如：5.1" /></label>
            {/if}
          </div>
          <label>复核说明（必填）<textarea bind:value={resealReason} placeholder="如：已取出非过期药品 1 盒，双人复核无误" /></label>
          <div class="btn-row"><button class="btn" on:click={reseal}>双人复核重新封袋</button></div>
        </div>
      {/if}
    {/if}
  </div>
</div>

<style>
  .cold-line { color: var(--cold); font-weight: 600; font-size: .84rem; }
</style>
