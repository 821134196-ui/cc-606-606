<script>
  import { onMount } from 'svelte';
  import { api } from '../api.js';
  import Badge from './Badge.svelte';

  export let currentUser;

  let list = [];
  let selected = null;
  let categories = [];
  let error = '';
  let msg = '';

  // 新建预约
  let nf = { residentName: '', phone: '', queryCode: '', declaredQty: 3 };
  // 核对实收
  let receivedQty = 0;
  // 差异确认
  let diffCode = '';
  let diffNote = '';
  // 登记药品
  let df = { name: '', category: 'COMMON_SOLID', quantity: 1, safetyConfirmed: false };
  // 封袋
  let sf = { category: '', storageLocation: '常温货架A', fridgeTemp: 5.0 };

  async function refresh() {
    list = await api.appointments();
    if (selected) await open(selected.id);
  }
  onMount(refresh);

  async function open(id) {
    error = ''; msg = '';
    selected = await api.appointment(id);
    receivedQty = selected.receivedQty ?? 0;
  }

  function flash(e) {
    error = e.message;
    msg = '';
  }
  function ok(m) {
    msg = m;
    error = '';
  }

  async function createAppt() {
    try {
      const a = await api.createAppointment(nf);
      nf = { residentName: '', phone: '', queryCode: '', declaredQty: 3 };
      await refresh();
      await open(a.id);
      ok('预约单已创建：' + a.apptNo);
    } catch (e) { flash(e); }
  }

  async function doCheck() {
    try {
      await api.check(selected.id, receivedQty);
      await refresh();
      ok(receivedQty === selected.declaredQty ? '核对一致，可登记封袋' : '数量存在差异，须请居民本人确认');
    } catch (e) { flash(e); }
  }

  async function doConfirmDiff() {
    try {
      await api.confirmDifference(selected.id, diffCode, diffNote);
      diffCode = ''; diffNote = '';
      await refresh();
      ok('居民已确认数量差异');
    } catch (e) { flash(e); }
  }

  async function addDrug() {
    try {
      await api.addDrug(selected.id, df);
      df = { name: '', category: df.category, quantity: 1, safetyConfirmed: false };
      await refresh();
      ok('药品已登记');
    } catch (e) { flash(e); }
  }

  $: catMeta = categories.find(c => c.code === df.category);
  $: sealingCat = categories.find(c => c.code === sf.category);
  $: pendingByCategory = pendingGroups(selected);
  $: canSeal = selected && selected.status !== 'SCHEDULED';

  function pendingGroups(a) {
    if (!a) return [];
    const map = new Map();
    for (const d of a.drugs.filter(x => !x.bagNo)) {
      const g = map.get(d.category) || { code: d.category, label: d.categoryLabel, hazardous: d.hazardous, refrigerated: d.refrigerated, qty: 0 };
      g.qty += d.quantity;
      map.set(d.category, g);
    }
    return [...map.values()];
  }

  async function sealCat(code, refrigerated, hazardous) {
    try {
      const body = { category: code, storageLocation: sf.storageLocation };
      if (refrigerated) body.fridgeTemp = Number(sf.fridgeTemp);
      const bag = await api.seal(selected.id, currentUser, body);
      await refresh();
      ok(`已封袋 ${bag.bagNo}（${bag.quantity} 件）${hazardous ? '，危险药品须单独交接' : refrigerated ? '，已登记入柜温度' : ''}`);
    } catch (e) { flash(e); }
  }

  async function complete() {
    try {
      await api.complete(selected.id);
      await refresh();
      ok('该居民交回流程已完成');
    } catch (e) { flash(e); }
  }
</script>

<div class="grid cols2">
  <div>
    <div class="card">
      <h2>① 新建交回预约</h2>
      <div class="grid cols2">
        <div><label>居民姓名</label><input bind:value={nf.residentName} placeholder="如：周岚" /></div>
        <div><label>手机号</label><input bind:value={nf.phone} placeholder="11 位手机号" /></div>
        <div><label>居民查询码（居民端凭证，可留空自动生成）</label><input bind:value={nf.queryCode} placeholder="如 RC2026" /></div>
        <div><label>预约申报数量（件）</label><input type="number" min="1" bind:value={nf.declaredQty} /></div>
      </div>
      <div style="margin-top:10px;"><button class="btn" on:click={createAppt}>登记预约</button></div>
    </div>

    <div class="card">
      <h2>② 预约单列表（点击核对）</h2>
      <table>
        <thead><tr><th>交回单号</th><th>居民</th><th>申报/实收</th><th>状态</th></tr></thead>
        <tbody>
          {#each list as a}
            <tr class="clickable" class:on={selected && selected.id === a.id} on:click={() => open(a.id)}>
              <td>{a.apptNo}</td>
              <td>{a.residentName}</td>
              <td>{a.declaredQty} / {a.receivedQty ?? '—'}</td>
              <td>
                {#if a.status === 'SCHEDULED' && a.receivedQty != null && !a.differenceConfirmed}
                  <span class="pill" class:error>待居民确认差异</span>
                {/if}
                <span class="pill">{a.statusLabel}</span>
              </td>
            </tr>
          {/each}
        </tbody>
      </table>
    </div>
  </div>

  {#if selected}
  <div>
    {#if error}<div class="alert danger">⛔ {error}</div>{/if}
    {#if msg}<div class="alert ok">✅ {msg}</div>{/if}

    <div class="card">
      <h2>③ 现场核对：{selected.apptNo}</h2>
      <dl class="kv">
        <dt>居民</dt><dd>{selected.resident.name}（{selected.resident.phone}）　居民查询码：<b>{selected.resident.queryCode}</b></dd>
        <dt>预约申报</dt><dd>{selected.declaredQty} 件</dd>
        <dt>实收</dt><dd>{selected.receivedQty ?? '尚未核对'}</dd>
        {#if selected.differenceNote}<dt>差异</dt><dd>{selected.differenceNote}</dd>{/if}
      </dl>

      {#if selected.receivedQty == null || (selected.receivedQty !== selected.declaredQty && !selected.differenceConfirmed)}
        <div class="row" style="margin-top:10px;">
          <div style="flex:0 0 160px;">
            <label>现场实收数量</label>
            <input type="number" min="0" bind:value={receivedQty} />
          </div>
          <div style="flex:0 0 auto; padding-bottom:2px;">
            <button class="btn" on:click={doCheck}>核对数量</button>
          </div>
        </div>
      {/if}

      {#if selected.receivedQty != null && selected.receivedQty !== selected.declaredQty && !selected.differenceConfirmed}
        <div class="alert warn" style="margin-top:12px;">
          ⚠ 数量差异：申报 {selected.declaredQty} 件，实收 {selected.receivedQty} 件（差 {selected.receivedQty - selected.declaredQty} 件）。
          <b>必须请居民本人输入查询码确认后，才能登记与封袋。</b>
        </div>
        <div class="grid cols2">
          <div><label>居民本人查询码</label><input bind:value={diffCode} placeholder="请居民输入，工作人员不可代确认" /></div>
          <div><label>居民备注（可选）</label><input bind:value={diffNote} placeholder="如：有两盒已自行丢弃" /></div>
        </div>
        <div style="margin-top:8px;"><button class="btn" on:click={doConfirmDiff}>请居民确认差异</button></div>
      {/if}
    </div>

    {#if selected.status !== 'SCHEDULED'}
    <div class="card">
      <h2>④ 按类别登记实收药品</h2>
      <div class="alert danger">
        <b>☣ 危险药品处理要求：</b>佩戴手套口罩 → 防泄漏双层袋单独封装 → 登记时逐项勾选防护确认 → 交接双人核对，严禁与其他药品混放。
      </div>
      <div class="alert cold">
        <b>❄ 需冷藏药品处理要求：</b>全程 2~8℃；封袋须立即入冷藏柜并测温登记；每次转交测温并沿袋号记录；取出后 30 分钟内完成交接。
      </div>
      <div class="row">
        <div style="flex:2;"><label>药品名称（仅工作人员可见）</label><input bind:value={df.name} placeholder="如：过期胰岛素注射液" /></div>
        <div style="flex:2;">
          <label>药品类别</label>
          <select bind:value={df.category}>
            {#each categories as c}<option value={c.code}>{c.label}</option>{/each}
          </select>
        </div>
        <div style="flex:0 0 90px;"><label>数量</label><input type="number" min="1" bind:value={df.quantity} /></div>
      </div>
      {#if catMeta}
        <div class="sticky-note" style="margin-top:8px;">
          {catMeta.hazardous ? '☣' : catMeta.refrigerated ? '❄' : 'ℹ'} {catMeta.handling}
        </div>
      {/if}
      {#if catMeta && catMeta.hazardous}
        <label style="display:flex;align-items:center;gap:8px;color:var(--danger-ink);font-weight:700;margin-top:8px;">
          <input type="checkbox" style="width:auto;" bind:checked={df.safetyConfirmed} />
          我已按危险药品要求完成防护与双层封装确认
        </label>
      {/if}
      <div style="margin-top:8px;"><button class="btn" on:click={addDrug}>登记药品</button></div>
    </div>

    <div class="card">
      <h2>⑤ 分类封袋（封袋后禁止追加/改动）</h2>
      <table>
        <thead><tr><th>药品名称</th><th>类别</th><th>数量</th><th>袋号/操作</th></tr></thead>
        <tbody>
          {#each selected.drugs as d}
            <tr>
              <td>{d.name}</td>
              <td><Badge category={d} small /> {d.categoryLabel}</td>
              <td>{d.quantity}</td>
              <td>
                {#if d.bagNo}
                  <span class="pill">{d.bagNo} 🔒</span>
                {:else}
                  <span class="muted">待封袋</span>
                {/if}
              </td>
            </tr>
          {/each}
        </tbody>
      </table>

      {#if pendingByCategory.length}
        <h3>待封袋分组（同类别一次性封入一袋）</h3>
        <div class="row">
          <div style="flex:2;">
            <label>保管位置（冷藏请填冷藏柜编号）</label>
            <input bind:value={sf.storageLocation} placeholder="如：常温货架A / 冷藏柜2号" />
          </div>
          <div style="flex:0 0 120px;">
            <label>冷藏入柜温度℃</label>
            <input type="number" step="0.1" bind:value={sf.fridgeTemp} />
          </div>
        </div>
        {#each pendingByCategory as g}
          <div class="alert {g.hazardous ? 'danger' : g.refrigerated ? 'cold' : 'ok'}" style="display:flex;align-items:center;gap:10px;flex-wrap:wrap;">
            <Badge category={g} />
            <b>{g.label}</b> 待封 {g.qty} 件
            <span style="flex:1"></span>
            <button class="btn" on:click={() => sealCat(g.code, g.refrigerated, g.hazardous)}>
              扫码封袋（{g.refrigerated ? '入柜测温 ' + sf.fridgeTemp + '℃' : '常温'}）
            </button>
          </div>
        {/each}
      {/if}

      {#if selected.drugs.length && selected.unsealedCount === 0}
        <div style="margin-top:12px;">
          <button class="btn ghost" on:click={complete}>完成该居民交接（全部已封袋）</button>
        </div>
      {/if}
    </div>
    {/if}
  </div>
  {/if}
</div>

<style>
  tr.on { background: #eef7f7; }
</style>
