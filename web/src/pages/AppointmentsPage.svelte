<script>
  import { onMount } from 'svelte';
  import { api } from '../api.js';
  import { toast } from '../lib/toast.js';
  import CategoryBadge from '../lib/CategoryBadge.svelte';
  import HandlingBanners from '../lib/HandlingBanners.svelte';

  export let categories = [];
  export let staff = [];

  let appointments = [];
  let loading = false;

  // 新建预约
  let showCreate = false;
  let form = { residentName: '', phone: '', items: [{ medicineName: '', category: 'ORAL_SOLID', plannedQty: 1, unit: '盒' }] };

  // 核对 / 封袋操作中的预约
  let activeCode = null;

  async function load() {
    loading = true;
    try {
      appointments = await api.get('/api/appointments');
    } catch (e) {
      toast(e.message, 'error');
    }
    loading = false;
  }
  onMount(load);

  function addLine() {
    form.items.push({ medicineName: '', category: 'ORAL_SOLID', plannedQty: 1, unit: '盒' });
    form.items = form.items;
  }
  function removeLine(i) {
    form.items.splice(i, 1);
    form.items = form.items;
  }

  async function createAppointment() {
    try {
      const body = {
        residentName: form.residentName,
        phone: form.phone,
        items: form.items.map((i) => ({
          medicineName: i.medicineName,
          category: i.category,
          plannedQty: Number(i.plannedQty),
          unit: i.unit || '盒'
        }))
      };
      const created = await api.post('/api/appointments', body);
      toast(`预约成功：凭证编号 ${created.code}，查询码已通过本地模拟短信发送`);
      showCreate = false;
      form = { residentName: '', phone: '', items: [{ medicineName: '', category: 'ORAL_SOLID', plannedQty: 1, unit: '盒' }] };
      activeCode = created.code;
      await load();
    } catch (e) {
      toast(e.message, 'error');
    }
  }

  function cat(code) {
    return categories.find((c) => c.code === code);
  }

  function toggle(code) {
    activeCode = activeCode === code ? null : code;
  }
</script>

<!-- 现场始终突出的文字处理要求 -->
<HandlingBanners {categories} />

<div class="panel">
  <div class="panel-title">
    <h2>预约与收件单</h2>
    <span class="spacer"></span>
    <button class="btn secondary small" on:click={load}>刷新</button>
    <button class="btn small" on:click={() => (showCreate = !showCreate)}>
      {showCreate ? '收起' : '＋ 新建居民预约'}
    </button>
  </div>

  {#if showCreate}
    <div class="panel" style="background:#fafbfc;">
      <h3>新建居民预约（登记拟交回药品）</h3>
      <div class="grid two">
        <div><label>居民姓名</label><input bind:value={form.residentName} placeholder="如：张桂兰" /></div>
        <div><label>手机号（接收本地模拟短信）</label><input bind:value={form.phone} placeholder="如：13800001111" /></div>
      </div>
      <table>
        <thead>
          <tr><th>药品名称</th><th style="width:170px;">类别</th><th style="width:90px;">预约数量</th><th style="width:80px;">单位</th><th style="width:60px;"></th></tr>
        </thead>
        <tbody>
          {#each form.items as item, i}
            <tr>
              <td><input bind:value={item.medicineName} placeholder="药品名称" /></td>
              <td>
                <select bind:value={item.category}>
                  {#each categories as c}
                    <option value={c.code}>
                      {c.label}{c.dangerous ? '（▲危险）' : ''}{c.cold ? '（❄冷藏）' : ''}
                    </option>
                  {/each}
                </select>
              </td>
              <td><input type="number" min="1" bind:value={item.plannedQty} /></td>
              <td><input bind:value={item.unit} /></td>
              <td><button class="btn ghost small" on:click={() => removeLine(i)}>删除</button></td>
            </tr>
          {/each}
        </tbody>
      </table>
      <div class="btn-row">
        <button class="btn secondary small" on:click={addLine}>＋ 增加药品</button>
        <button class="btn" on:click={createAppointment}>提交预约（生成凭证与查询码）</button>
      </div>
    </div>
  {/if}

  {#if loading}
    <div class="empty">加载中…</div>
  {:else if appointments.length === 0}
    <div class="empty">暂无预约单</div>
  {:else}
    {#each appointments as a}
      <div class="panel" class:active={activeCode === a.code}>
        <div class="panel-title">
          <div>
            <strong class="mono">{a.code}</strong>
            <span style="margin:0 .5rem;">{a.residentName}</span>
            {#if a.status === 'PENDING'}
              <span class="tag warn">待核对</span>
            {:else if a.status === 'RECEIVED'}
              <span class="tag ok">已核对</span>
            {:else}
              <span class="tag plain">已处置完成</span>
            {/if}
          </div>
          <span class="spacer"></span>
          <span class="muted small">预约 {a.plannedTotal} · 实收 {a.receivedTotal} · 已封袋 {a.sealedTotal}</span>
          <button class="btn small secondary" on:click={() => toggle(a.code)}>
            {activeCode === a.code ? '收起操作' : '核对 / 封袋'}
          </button>
        </div>

        {#if activeCode === a.code}
          <AppointmentDetail {a} {categories} {staff} on:changed={load} />
        {/if}
      </div>
    {/each}
  {/if}
</div>

<style>
  .panel.active { border-color: var(--brand); box-shadow: 0 0 0 2px rgba(22, 104, 220, .12); }
</style>
