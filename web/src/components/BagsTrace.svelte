<script>
  import { onMount } from 'svelte';
  import { api } from '../api.js';
  import Badge from './Badge.svelte';

  export let currentUser;

  let bags = [];
  let staffList = [];
  let trace = null;
  let error = '';
  let msg = '';
  let filter = '';

  // 拆袋双人复核表单
  let rf = { operator: '', reviewer: '', reason: '' };
  // 冷链登记
  let cf = { action: 'CHECK', temperature: 5.0, fridgeNo: '', note: '' };
  // 内部转交
  let tf = { toUser: '', location: '常温货架B', coldTemp: 5.0 };
  // 移交处置单位（扫码模拟）
  let hf = { disposalUser: 'wang', scanCode: '', coldTemp: 5.0, location: '回收点出库口' };
  // 签收
  let sf = { scanCode: '' };
  // 重新封袋
  let rs = { location: '', fridgeTemp: 5.0 };

  let inbox = [];

  $: rf.operator = currentUser;
  $: cf.action = trace && trace.refrigerated ? cf.action : 'CHECK';
  $: otherStaff = staffList.filter(s => s.username !== currentUser && s.role === 'STAFF');

  onMount(async () => {
    staffList = await api.staff();
    await reload();
  });

  async function reload() {
    bags = await api.bags(filter);
    if (trace) await showTrace(trace.bagNo);
    await loadInbox();
  }
  async function loadInbox() {
    try { inbox = await api.staffInbox(currentUser); } catch (e) { inbox = []; }
  }

  async function showTrace(bagNo) {
    trace = await api.trace(bagNo);
    error = ''; msg = '';
    rs.location = trace.storageLocation;
    cf.fridgeNo = trace.storageLocation;
  }

  function fail(e) { error = e.message; msg = ''; }
  function good(m) { msg = m; error = ''; }

  function simulateScan(prefix) {
    return prefix + '-' + Math.random().toString(36).slice(2, 8).toUpperCase();
  }

  async function reopen() {
    if (!rf.reviewer) return fail(new Error('请选择复核人（须与操作人不同）'));
    if (!rf.reason.trim()) return fail(new Error('拆袋必须填写原因'));
    try {
      await api.reopen(trace.bagNo, rf);
      good('拆袋已记录（双人复核），现在可调整内容并重新封袋');
      rf = { operator: currentUser, reviewer: '', reason: '' };
      await reload();
    } catch (e) { fail(e); }
  }

  async function reseal() {
    try {
      const bag = await api.reseal(trace.bagNo, currentUser, rs.location,
        trace.refrigerated ? Number(rs.fridgeTemp) : undefined);
      good('已重新封袋，新袋号：' + bag.bagNo);
      await showTrace(bag.bagNo);
      bags = await api.bags(filter);
    } catch (e) { fail(e); }
  }

  async function coldLog() {
    try {
      const r = await api.coldLog(trace.bagNo, {
        action: cf.action, temperature: Number(cf.temperature),
        fridgeNo: cf.fridgeNo, operator: currentUser, note: cf.note
      });
      good(r.alarm ? '温度越限已产生告警通知！' : '冷链温度已登记');
      await reload();
    } catch (e) { fail(e); }
  }

  async function transfer() {
    try {
      await api.transfer(trace.bagNo, {
        fromUser: currentUser, toUser: tf.toUser,
        location: tf.location,
        coldTemp: trace.refrigerated ? Number(tf.coldTemp) : undefined,
        scanCode: simulateScan('RCV')
      });
      good('内部转交已记录，接收人已收到模拟通知');
      await reload();
    } catch (e) { fail(e); }
  }

  function genHandoverScan() {
    hf.scanCode = simulateScan('DISP');
  }

  async function handover() {
    if (!hf.scanCode) return fail(new Error('请先模拟扫描处置单位签收码'));
    try {
      await api.handover(trace.bagNo, {
        fromUser: currentUser, disposalUser: hf.disposalUser,
        scanCode: hf.scanCode,
        coldTemp: trace.refrigerated ? Number(hf.coldTemp) : undefined,
        location: hf.location
      });
      good('已移交处置单位，待对方扫码签收');
      await reload();
    } catch (e) { fail(e); }
  }

  async function sign() {
    const last = trace.transfers?.find(t => t.type === 'HANDOVER')
      ?? trace.transfers?.at(-1);
    const disposalUser = last?.toStaffUsername || 'wang';
    try {
      await api.sign(trace.bagNo, { disposalUser, scanCode: sf.scanCode });
      good('处置单位签收完成，交接闭环');
      sf.scanCode = '';
      await reload();
    } catch (e) { fail(e); }
  }
</script>

<div class="grid cols2">
  <div>
    <div class="card">
      <h2>封袋列表 / 保管位置</h2>
      <div class="row" style="margin-bottom:10px;">
        <div style="flex:0 0 200px;">
          <label>按状态筛选</label>
          <select bind:value={filter}>
            <option value="">全部</option>
            <option value="SEALED">已封袋在库</option>
            <option value="REOPENED">已拆袋待处理</option>
            <option value="HANDED_OVER">已移交待签收</option>
            <option value="SIGNED">已签收</option>
          </select>
        </div>
        <div style="flex:0 0 auto;padding-bottom:2px;"><button class="btn ghost" on:click={reload}>刷新</button></div>
      </div>
      <table>
        <thead><tr><th>袋号</th><th>类别</th><th>件数</th><th>保管位置</th><th>状态</th></tr></thead>
        <tbody>
          {#each bags as b}
            <tr class="clickable" class:on={trace && trace.bagNo === b.bagNo} on:click={() => showTrace(b.bagNo)}>
              <td><b>{b.bagNo}</b></td>
              <td><Badge category={b} small /></td>
              <td>{b.quantity}</td>
              <td>{b.storageLocation}</td>
              <td><span class="pill">{b.statusLabel}</span></td>
            </tr>
          {/each}
          {#if bags.length === 0}<tr><td colspan="5" class="muted">暂无封袋</td></tr>{/if}
        </tbody>
      </table>
    </div>

    <div class="card">
      <h2>模拟通知（{currentUser} 的站内消息）</h2>
      {#if inbox.length === 0}<p class="muted">暂无通知</p>{/if}
      {#each inbox as n}
        <div class="sticky-note" style="margin-bottom:6px;">{n.content}<div class="muted">{n.createdAt}</div></div>
      {/each}
    </div>
  </div>

  {#if trace}
  <div>
    {#if error}<div class="alert danger">⛔ {error}</div>{/if}
    {#if msg}<div class="alert ok">✅ {msg}</div>{/if}

    <div class="card">
      <h2>袋号追溯：{trace.bagNo}
        <span class="pill">{trace.statusLabel}</span>
      </h2>
      <dl class="kv">
        <dt>类别</dt><dd><Badge category={trace} /> {trace.categoryLabel}</dd>
        <dt>数量</dt><dd>{trace.quantity} 件</dd>
        <dt>封袋人</dt><dd>{trace.sealedBy}</dd>
        <dt>保管位置</dt><dd>{trace.storageLocation}（冷藏柜/货架）</dd>
        <dt>封条码（模拟扫码）</dt><dd><span class="scanbox">{trace.sealCode}</span></dd>
        <dt>封袋时间</dt><dd>{trace.sealedAt}</dd>
      </dl>
      {#if trace.hazardous}
        <div class="alert danger"><b>☣ 危险药品：</b>单独存放、防泄漏双层袋；交接须双人核对，禁止混放。</div>
      {/if}
      {#if trace.refrigerated}
        <div class="alert cold"><b>❄ 冷藏药品：</b>全程 2~8℃，下方每次交接均须测温，沿袋号可查。</div>
      {/if}
    </div>

    {#if trace.status === 'SEALED'}
    <div class="card">
      <h2>内部保管转交（记录接收人）</h2>
      <div class="row">
        <div style="flex:2;">
          <label>接收人（其他工作人员）</label>
          <select bind:value={tf.toUser}>
            <option value="">请选择</option>
            {#each otherStaff as s}<option value={s.username}>{s.displayName}</option>{/each}
          </select>
        </div>
        <div style="flex:2;"><label>新保管位置</label><input bind:value={tf.location} /></div>
        {#if trace.refrigerated}
          <div style="flex:0 0 110px;"><label>交接温度℃</label><input type="number" step="0.1" bind:value={tf.coldTemp} /></div>
        {/if}
        <div style="flex:0 0 auto;padding-bottom:2px;"><button class="btn" on:click={transfer}>扫码转交</button></div>
      </div>
    </div>

    <div class="card">
      <h2>移交处置单位</h2>
      <div class="row">
        <div style="flex:2;">
          <label>处置单位接收人</label>
          <select bind:value={hf.disposalUser}>
            {#each staffList.filter(s => s.role === 'DISPOSAL') as s}
              <option value={s.username}>{s.displayName}</option>
            {/each}
          </select>
        </div>
        {#if trace.refrigerated}
          <div style="flex:0 0 110px;"><label>交接温度℃</label><input type="number" step="0.1" bind:value={hf.coldTemp} /></div>
        {/if}
        <div style="flex:0 0 auto;padding-bottom:2px;"><button class="btn ghost" on:click={genHandoverScan}>模拟扫描签收码</button></div>
      </div>
      <div style="margin-top:8px;">签收码：<span class="scanbox">{hf.scanCode || '（尚未扫描）'}</span></div>
      <div style="margin-top:8px;"><button class="btn" on:click={handover}>移交处置单位</button></div>
    </div>

    <div class="card">
      <h2>拆袋（须两人复核并留记录）</h2>
      <div class="alert warn">封袋后内容冻结。确需拆袋必须由 <b>两名不同工作人员</b> 操作+复核，写明原因，系统留痕。</div>
      <div class="grid cols2">
        <div>
          <label>操作人（当前登录）</label>
          <input value={currentUser} disabled />
        </div>
        <div>
          <label>复核人（必须不同）</label>
          <select bind:value={rf.reviewer}>
            <option value="">请选择复核人</option>
            {#each otherStaff as s}<option value={s.username}>{s.displayName}</option>{/each}
          </select>
        </div>
      </div>
      <div style="margin-top:8px;"><label>拆袋原因</label><textarea bind:value={rf.reason} placeholder="如：袋内有一件分类错误，需改按危险药品重封" /></div>
      <div style="margin-top:8px;"><button class="btn danger" on:click={reopen}>双人复核后拆袋</button></div>
    </div>
    {/if}

    {#if trace.status === 'REOPENED'}
    <div class="card">
      <h2>拆袋后处理（调整后重新封袋）</h2>
      <div class="alert warn">该袋已拆袋，原药品已解除冻结；重新封袋将生成<b>新袋号与新封条码</b>，拆袋记录永久保留。</div>
      <div class="row">
        <div style="flex:2;"><label>保管位置</label><input bind:value={rs.location} /></div>
        {#if trace.refrigerated}
          <div style="flex:0 0 120px;"><label>入柜温度℃</label><input type="number" step="0.1" bind:value={rs.fridgeTemp} /></div>
        {/if}
        <div style="flex:0 0 auto;padding-bottom:2px;"><button class="btn" on:click={reseal}>扫码重新封袋</button></div>
      </div>
    </div>
    {/if}

    {#if trace.status === 'HANDED_OVER'}
    <div class="card">
      <h2>处置单位签收</h2>
      <div class="alert cold">切换到处置单位账户后，输入移交时扫描的签收码完成签收（模拟）。</div>
      <div class="row">
        <div style="flex:2;"><label>签收码（与移交码一致）</label><input bind:value={sf.scanCode} placeholder="扫描/粘贴移交签收码" /></div>
        <div style="flex:0 0 auto;padding-bottom:2px;"><button class="btn" on:click={sign}>确认签收</button></div>
      </div>
    </div>
    {/if}

    {#if trace.refrigerated && trace.status !== 'SIGNED'}
    <div class="card">
      <h2>冷链温度登记（沿袋号 {trace.bagNo} 追查）</h2>
      <div class="row">
        <div style="flex:0 0 120px;">
          <label>环节</label>
          <select bind:value={cf.action}>
            <option value="IN">入柜</option>
            <option value="CHECK">巡检</option>
            <option value="OUT">取出</option>
          </select>
        </div>
        <div style="flex:0 0 110px;"><label>温度℃（2~8）</label><input type="number" step="0.1" bind:value={cf.temperature} /></div>
        <div style="flex:1;"><label>冷藏柜编号</label><input bind:value={cf.fridgeNo} /></div>
      </div>
      <div style="margin-top:8px;"><label>备注</label><input bind:value={cf.note} /></div>
      <div style="margin-top:8px;"><button class="btn" on:click={coldLog}>登记温度</button></div>
    </div>
    {/if}

    <div class="card">
      <h2>交接链 / 冷链 / 拆袋记录</h2>
      <h3>转交记录（每次转交的接收人）</h3>
      <table>
        <thead><tr><th>时间</th><th>类型</th><th>交出人</th><th>接收人</th><th>位置</th><th>冷链温度</th><th>签收码</th></tr></thead>
        <tbody>
          {#each trace.transfers as t}
            <tr>
              <td>{t.createdAt}</td><td>{t.typeLabel}</td><td>{t.fromStaff}</td><td><b>{t.toStaff}</b></td>
              <td>{t.location || '—'}</td>
              <td>
                {#if t.coldTemp != null}
                  <span class="badge {t.coldTemp < 2 || t.coldTemp > 8 ? 'danger' : 'cold'}">
                    {t.coldTemp < 2 || t.coldTemp > 8 ? '⚠' : '❄'} {t.coldTemp}℃
                  </span>
                {:else}—{/if}
              </td>
              <td class="scanbox">{t.scanCode || '—'}</td>
            </tr>
          {/each}
          {#if trace.transfers.length === 0}<tr><td colspan="7" class="muted">尚无转交记录</td></tr>{/if}
        </tbody>
      </table>

      {#if trace.refrigerated}
      <h3>冷链温度轨迹</h3>
      <table>
        <thead><tr><th>时间</th><th>环节</th><th>温度</th><th>冷藏柜</th><th>登记人</th><th>说明</th></tr></thead>
        <tbody>
          {#each trace.coldLogs as l}
            <tr>
              <td>{l.createdAt}</td>
              <td>{l.action === 'IN' ? '入柜' : l.action === 'OUT' ? '取出' : '巡检'}</td>
              <td><span class="badge {l.alarm ? 'danger' : 'cold'}">{l.alarm ? '⚠' : '❄'} {l.temperature}℃</span></td>
              <td>{l.fridgeNo}</td><td>{l.operator}</td><td>{l.note}</td>
            </tr>
          {/each}
          {#if trace.coldLogs.length === 0}<tr><td colspan="6" class="muted">暂无冷链记录</td></tr>{/if}
        </tbody>
      </table>
      {/if}

      {#if trace.reopenRecords.length}
      <h3>双人拆袋记录</h3>
      <table>
        <thead><tr><th>时间</th><th>操作人</th><th>复核人</th><th>原因</th></tr></thead>
        <tbody>
          {#each trace.reopenRecords as r}
            <tr><td>{r.createdAt}</td><td>{r.operator}</td><td><b>{r.reviewer}</b></td><td>{r.reason}</td></tr>
          {/each}
        </tbody>
      </table>
      {/if}

      <h3>袋内药品明细（仅工作人员可见）</h3>
      <table>
        <thead><tr><th>名称</th><th>类别</th><th>数量</th></tr></thead>
        <tbody>
          {#each trace.contents as d}
            <tr><td>{d.name}</td><td>{d.category}</td><td>{d.quantity}</td></tr>
          {/each}
          {#if trace.contents.length === 0}<tr><td colspan="3" class="muted">拆袋后内容在重新封袋前不归属任何袋</td></tr>{/if}
        </tbody>
      </table>
    </div>
  </div>
  {/if}
</div>

<style>
  tr.on { background: #eef7f7; }
</style>
