<script>
  import { createEventDispatcher } from 'svelte';
  import { api } from '../api.js';
  import { toast } from '../lib/toast.js';
  import CategoryBadge from '../lib/CategoryBadge.svelte';

  export let a; // AppointmentView
  export let categories = [];
  export let staff = [];

  const dispatch = createEventDispatcher();

  // 现场核对表单：每条实收数量
  let staffNo = 'W001';
  let received = {};
  function initReceived() {
    const map = {};
    for (const i of a.items) receivedMapSet(map, i.id, i.receivedQty ?? i.plannedQty);
    received = map;
  }
  function receivedMapSet(map, k, v) {
    map[k] = v;
  }
  initReceived();
  // 切换预约单时重置
  $: if (a && (lastCode !== a.code)) {
    lastCode = a.code;
    initReceived();
    sealCategory = (a.items.find((i) => i.availableQty > 0) || {}).category || null;
    sealLocation = '';
    sealTemp = null;
  }
  let lastCode = a.code;

  let confirmNote = '';

  async function receive() {
    try {
      await api.put(`/api/appointments/${a.code}/receive`, {
        staffEmployeeNo: staffNo,
        items: a.items.map((i) => ({ itemId: i.id, receivedQty: Number(received[i.id] ?? 0) }))
      });
      toast('现场核对完成');
      dispatch('changed');
    } catch (e) {
      toast(e.message, 'error');
    }
  }

  async function confirmDiff(ok) {
    try {
      await api.put(`/api/appointments/${a.code}/confirm-difference`, {
        confirmed: ok,
        note: confirmNote || (ok ? '居民现场确认实收数量' : '居民不认可，待复核')
      });
      toast(ok ? '居民已确认数量差异，可以封袋' : '已记录居民不认可');
      dispatch('changed');
    } catch (e) {
      toast(e.message, 'error');
    }
  }

  // 封袋表单：按类别
  const sealCategories = () => {
    const map = new Map();
    for (const i of a.items) {
      if (i.availableQty > 0) {
        if (!map.has(i.category)) map.set(i.category, []);
        map.get(i.category).push(i);
      }
    }
    return [...map.entries()].map(([category, items]) => ({
      category,
      items,
      meta: categories.find((c) => c.code === category)
    }));
  };

  let sealCategory = null;
  let sealLocation = '';
  let sealTemp = null;
  let witnessNo = '';
  // 每个类别装袋数量选择
  let sealQty = {};
  function defaultSealQty(group) {
    const m = {};
    for (const i of group.items) m[i.id] = i.availableQty;
    return m;
  }
  $: sealGroups = sealCategories();
  $: currentGroup = sealGroups.find((g) => g.category === sealCategory) || null;
  $: if (currentGroup) {
    const d = defaultSealQty(currentGroup);
    for (const k of Object.keys(d)) if (sealQty[k] === undefined) sealQty[k] = d[k];
  }

  async function seal() {
    if (!currentGroup) return;
    const entries = currentGroup.items
      .map((i) => ({ itemId: i.id, qty: Number(sealQty[i.id] ?? 0) }))
      .filter((e) => e.qty > 0);
    if (entries.length === 0) {
      toast('请填写装袋数量（封袋后无法追加，请一次装足）', 'error');
      return;
    }
    const body = {
      appointmentCode: a.code,
      category: currentGroup.category,
      staffEmployeeNo: staffNo,
      storageLocation: sealLocation,
      entries
    };
    if (currentGroup.meta.dangerous) body.witnessEmployeeNo = witnessNo || undefined;
    if (currentGroup.meta.cold) body.temperature = sealTemp === null || sealTemp === '' ? null : Number(sealTemp);
    try {
      const bag = await api.post('/api/bags', body);
      toast(`已封袋：${bag.bagNo}（${currentGroup.meta.label}，共 ${bag.totalQty} ${bag.unit}）。封袋后禁止追加药品`);
      sealLocation = '';
      sealTemp = null;
      witnessNo = '';
      sealQty = {};
      dispatch('changed');
    } catch (e) {
      toast(e.message, 'error');
    }
  }

  $: statusPending = a.status === 'PENDING';
</script>

<div class="detail">
  <!-- 明细 -->
  <table>
    <thead>
      <tr>
        <th>药品</th>
        <th style="width:130px;">类别</th>
        <th style="width:70px;">预约</th>
        <th style="width:120px;">实收（核对）</th>
        <th style="width:70px;">已封袋</th>
        <th style="width:70px;">可封袋</th>
      </tr>
    </thead>
    <tbody>
      {#each a.items as i}
        <tr>
          <td>{i.medicineName}</td>
          <td><CategoryBadge category={i.category} label={i.categoryLabel} dangerous={i.dangerous} cold={i.cold} /></td>
          <td>{i.plannedQty} {i.unit}</td>
          <td>
            {#if statusPending}
              <input type="number" min="0" bind:value={received[i.id]} />
            {:else}
              <strong>{i.receivedQty ?? 0}</strong> {i.unit}
              {#if (i.receivedQty ?? 0) !== i.plannedQty}
                <span class="tag warn" title="与预约数量不一致">差异</span>
              {/if}
            {/if}
          </td>
          <td>{i.sealedQty}</td>
          <td><strong>{i.availableQty}</strong></td>
        </tr>
        {#if i.dangerous || i.cold}
          <tr class="tiprow">
            <td colspan="6">
              {#if i.dangerous}<span class="tag danger">▲ 危险药品</span>{:else}<span class="tag cold">❄ 需冷藏</span>{/if}
              <span class="muted">{i.handlingTip}</span>
            </td>
          </tr>
        {/if}
      {/each}
    </tbody>
  </table>

  <!-- 核对操作 -->
  {#if statusPending}
    <div class="banner info">
      <div class="banner-head">① 现场核对实收数量</div>
      逐条登记实收数量；如与预约数量存在差异，保存后须请居民本人确认，确认前不能封袋。
    </div>
    <div class="btn-row">
      <label style="margin:0;width:180px;">收件工作人员工号
        <select bind:value={staffNo}>
          {#each staff.filter((s) => s.role === 'SITE') as s}
            <option value={s.employeeNo}>{s.employeeNo} · {s.name}</option>
          {/each}
        </select>
      </label>
      <button class="btn" on:click={receive}>保存核对结果</button>
    </div>
  {/if}

  <!-- 差异确认 -->
  {#if a.difference && a.differenceConfirmed !== true}
    <div class="banner warn">
      <div class="banner-head">② 预约与实收数量存在差异 —— 必须请居民本人确认后才能封袋</div>
      <div>差异说明：{a.differenceNote || '实收数量与预约不一致'}</div>
      <div class="btn-row">
        <input style="max-width:340px;" bind:value={confirmNote} placeholder="请居民确认的备注（可选）" />
      </div>
      <div class="btn-row">
        <button class="btn" on:click={() => confirmDiff(true)}>居民在场，确认实收数量</button>
        <button class="btn ghost" on:click={() => confirmDiff(false)}>居民暂不确认</button>
      </div>
    </div>
  {:else if a.difference && a.differenceConfirmed === true}
    <div class="banner info">
      <div class="banner-head">✓ 数量差异已经居民确认</div>
      <div>{a.differenceNote}</div>
    </div>
  {/if}

  <!-- 按类别封袋 -->
  {#if a.sealingAllowed}
    <div class="banner info">
      <div class="banner-head">③ 按类别封袋</div>
      封袋后袋内内容冻结、<strong>禁止追加药品</strong>；确需拆袋须两名工作人员复核并留记录。请确认装袋数量后封袋。
    </div>
    <div class="seal-box panel">
      <div class="grid two">
        <label>药品类别（仅显示尚有可封袋数量的类别）
          <select bind:value={sealCategory}>
            <option value={null}>请选择类别</option>
            {#each sealGroups as g}
              <option value={g.category}>
                {g.meta.label}{g.meta.dangerous ? '（▲危险药品·需双人）' : ''}{g.meta.cold ? '（❄冷藏2-8℃）' : ''}
              </option>
            {/each}
          </select>
        </label>
        <label>保管位置 / 库位编码
          <input bind:value={sealLocation}
                 placeholder={currentGroup?.meta?.cold ? '如：COLD-冷藏柜-01' : '如：A-常温柜-01'} />
        </label>
      </div>

      {#if currentGroup}
        <div class="banner {currentGroup.meta.dangerous ? 'danger' : currentGroup.meta.cold ? 'cold' : 'info'}">
          <strong>{currentGroup.meta.dangerous ? '▲ 危险药品' : currentGroup.meta.cold ? '❄ 需冷藏 2-8℃' : currentGroup.meta.label}处理要求：</strong>
          {currentGroup.meta.handlingTip}
        </div>

        {#if currentGroup.meta.dangerous}
          <label>第二名工作人员（复核人）工号 —— 危险药品必须双人核对
            <select bind:value={witnessNo}>
              <option value="">请选择复核人（须与操作人不同）</option>
              {#each staff.filter((s) => s.role === 'SITE') as s}
                <option value={s.employeeNo}>{s.employeeNo} · {s.name}</option>
              {/each}
            </select>
          </label>
        {/if}
        {#if currentGroup.meta.cold}
          <label>封袋温度（℃，必须在 2-8℃ 区间）
            <input type="number" step="0.1" min="2" max="8" bind:value={sealTemp} placeholder="如：5.0" />
          </label>
        {/if}

        <table>
          <thead><tr><th>药品</th><th style="width:120px;">可封袋</th><th style="width:150px;">本次装袋数量</th></tr></thead>
          <tbody>
            {#each currentGroup.items as i}
              <tr>
                <td>{i.medicineName}</td>
                <td>{i.availableQty} {i.unit}</td>
                <td><input type="number" min="0" max={i.availableQty} bind:value={sealQty[i.id]} /></td>
              </tr>
            {/each}
          </tbody>
        </table>
        <div class="btn-row">
          <button class="btn danger" on:click={seal}>确认封袋（封袋后禁止追加）</button>
        </div>
      {/if}
    </div>
  {:else if !statusPending && a.status !== 'COMPLETED'}
    <div class="muted" style="margin-top:.6rem;">所有实收药品均已封袋。如需拆袋调整，请到「封袋管理 · 袋号追溯」页面双人复核办理。</div>
  {/if}
</div>

<style>
  .tiprow td { background: #fcfcfd; padding-top: .1rem; }
  .tiprow .muted { margin-left: .4rem; }
  .seal-box { margin-top: .8rem; background: #fafbfc; }
</style>
