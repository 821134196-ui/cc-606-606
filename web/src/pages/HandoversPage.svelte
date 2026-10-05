<script>
  import { onMount } from 'svelte';
  import { api } from '../api.js';
  import { toast } from '../lib/toast.js';
  import CategoryBadge from '../lib/CategoryBadge.svelte';

  export let categories = [];
  export let staff = [];

  let bags = [];
  let handovers = [];

  // 发起交接表单
  let form = {
    bagNo: '',
    fromEmployeeNo: 'W001',
    toEmployeeNo: 'W002',
    toLocation: '',
    temperature: null,
    coldChainDevice: '',
    handoverType: 'INTERNAL'
  };
  // 扫码签收
  let acceptNo = 'W002';
  let acceptTemp = null;

  async function loadBags() {
    bags = await api.get('/api/bags');
  }
  async function loadHandovers() {
    handovers = await api.get('/api/handovers');
  }
  async function loadAll() {
    try {
      await Promise.all([loadBags(), loadHandovers()]);
    } catch (e) {
      toast(e.message, 'error');
    }
  }
  onMount(loadAll);

  $: selectedBag = bags.find((b) => b.bagNo === form.bagNo) || null;
  $: toStaff = staff.find((s) => s.employeeNo === form.toEmployeeNo) || null;
  $: isToDisposal = form.handoverType === 'TO_DISPOSAL';

  function onBagChange() {
    if (!selectedBag) {
      return;
    }
    if (selectedBag.cold) {
      form.toLocation = 'COLD-冷藏柜-02';
      form.temperature = 5;
      form.coldChainDevice = '冷藏周转箱 CC-02';
    } else {
      form.toLocation = form.handoverType === 'TO_DISPOSAL' ? 'DISPOSAL-暂存间-01' : 'A-常温柜-04';
      form.temperature = null;
      form.coldChainDevice = '';
    }
  }

  function onTypeChange() {
    if (form.handoverType === 'TO_DISPOSAL') {
      form.toEmployeeNo = disposalStaff[0]?.employeeNo || form.toEmployeeNo;
      if (selectedBag && !selectedBag.cold) form.toLocation = 'DISPOSAL-暂存间-01';
    } else {
      form.toEmployeeNo = 'W002';
      if (selectedBag && !selectedBag.cold) form.toLocation = 'A-常温柜-04';
    }
  }

  async function createHandover() {
    try {
      const body = { ...form };
      if (selectedBag && !selectedBag.cold) {
        delete body.temperature;
        delete body.coldChainDevice;
      } else if (body.temperature !== null && body.temperature !== '') {
        body.temperature = Number(body.temperature);
      }
      const h = await api.post('/api/handovers', body);
      toast(`已发起交接 #${h.id}，已向接收人 ${form.toEmployeeNo} 发送本地模拟扫码提醒，待其签收`);
      await loadAll();
    } catch (e) {
      toast(e.message, 'error', 6000);
    }
  }

  async function accept(h, accepted) {
    try {
      const body = { employeeNo: acceptNo, accepted };
      if (accepted && h.cold) body.temperature = acceptTemp === null || acceptTemp === '' ? null : Number(acceptTemp);
      if (!accepted) body.reason = '接收人现场拒收';
      await api.put(`/api/handovers/${h.id}/accept`, body);
      toast(accepted ? '扫码签收成功' : '已记录拒收');
      await loadAll();
    } catch (e) {
      toast(e.message, 'error', 6000);
    }
  }

  const siteStaff = staff.filter((s) => s.role === 'SITE');
  const disposalStaff = staff.filter((s) => s.role === 'DISPOSAL');
</script>

<div class="grid two">
  <!-- 发起交接 -->
  <div class="panel">
    <div class="panel-title"><h2>发起保管转交</h2></div>

    <div class="banner cold">
      <div class="banner-head">❄ 冷藏药品交接强制要求</div>
      必须测量并记录交出温度（2-8℃）、登记冷藏周转箱等冷链设备、接收位置为冷链库位；
      温度超区间系统将拒绝转出。
    </div>

    <label>选择封袋（仅列出未签收封袋）</label>
    <select bind:value={form.bagNo} on:change={onBagChange}>
      <option value="">请选择袋号</option>
      {#each bags.filter((b) => b.status !== 'SIGNED_OFF') as b}
        <option value={b.bagNo}>
          {b.bagNo} · {b.categoryLabel}{b.cold ? '（❄冷藏）' : ''}{b.dangerous ? '（▲危险）' : ''} · {b.storageLocation}
        </option>
      {/each}
    </select>

    {#if selectedBag}
      <div class="banner {selectedBag.dangerous ? 'danger' : selectedBag.cold ? 'cold' : 'info'}">
        {selectedBag.handlingTip}
      </div>
    {/if}

    <div class="grid two">
      <label>交接类型
        <select bind:value={form.handoverType} on:change={onTypeChange}>
          <option value="INTERNAL">回收点内部保管转交</option>
          <option value="TO_DISPOSAL">送交处置单位</option>
        </select>
      </label>
      <label>交出人工号
        <select bind:value={form.fromEmployeeNo}>
          {#each siteStaff as s}<option value={s.employeeNo}>{s.employeeNo} · {s.name}</option>{/each}
        </select>
      </label>
      <label>{isToDisposal ? '处置单位接收人' : '接收工作人员'}
        <select bind:value={form.toEmployeeNo}>
          {#if isToDisposal}
            {#each disposalStaff as s}<option value={s.employeeNo}>{s.employeeNo} · {s.name}（{s.organization}）</option>{/each}
          {:else}
            {#each siteStaff as s}<option value={s.employeeNo}>{s.employeeNo} · {s.name}</option>{/each}
          {/if}
        </select>
      </label>
      <label>接收保管位置 / 库位
        <input bind:value={form.toLocation} placeholder={selectedBag?.cold ? 'COLD-冷藏柜-02' : 'A-常温柜-04'} />
      </label>
    </div>

    {#if selectedBag?.cold}
      <div class="grid two">
        <label>交出温度（℃，2-8℃）<input type="number" step="0.1" min="2" max="8" bind:value={form.temperature} /></label>
        <label>冷链设备 <input bind:value={form.coldChainDevice} placeholder="冷藏周转箱 CC-02" /></label>
      </div>
    {/if}

    <div class="btn-row">
      <button class="btn" on:click={createHandover}>发起转交（向接收人发送模拟扫码提醒）</button>
    </div>
  </div>

  <!-- 扫码签收 -->
  <div class="panel">
    <div class="panel-title">
      <h2>接收人扫码签收（本地模拟）</h2>
      <span class="spacer"></span>
      <label style="margin:0;width:180px;">当前扫码人工号
        <select bind:value={acceptNo}>
          {#each staff as s}<option value={s.employeeNo}>{s.employeeNo} · {s.name}</option>{/each}
        </select>
      </label>
    </div>

    <h3>待签收交接单</h3>
    {#if handovers.filter((h) => h.status === 'PENDING').length === 0}
      <div class="empty">暂无待签收交接单</div>
    {:else}
      <table>
        <thead><tr><th>袋号</th><th>类别</th><th>交出人</th><th>温度</th><th>操作</th></tr></thead>
        <tbody>
          {#each handovers.filter((h) => h.status === 'PENDING') as h}
            <tr>
              <td class="mono small">{h.bagNo}</td>
              <td>{#if h.cold}<span class="tag cold">❄冷藏</span>{:else}<span class="tag plain">普通</span>{/if}</td>
              <td class="small">{h.fromStaffName}<div class="muted">{h.fromLocation}</div></td>
              <td>
                {#if h.cold}
                  <div>❄ {h.transferTemperature}℃</div>
                  <label class="small muted">复测℃
                    <input style="width:80px;" type="number" step="0.1" min="2" max="8" bind:value={acceptTemp} placeholder="如 5.0" />
                  </label>
                {:else}—{/if}
              </td>
              <td>
                <button class="btn small" on:click={() => accept(h, true)}>扫码签收</button>
                <button class="btn small ghost" style="margin-top:.3rem;" on:click={() => accept(h, false)}>拒收</button>
              </td>
            </tr>
          {/each}
        </tbody>
      </table>
    {/if}
  </div>
</div>

<!-- 交接链记录 -->
<div class="panel">
  <div class="panel-title"><h2>交接记录（冷藏温度 / 冷链设备 / 接收人全程可查）</h2>
  <span class="spacer"></span><button class="btn secondary small" on:click={loadAll}>刷新</button></div>
  <table>
    <thead>
      <tr><th>#</th><th>袋号</th><th>类型</th><th>状态</th><th>交出人 → 接收人</th><th>位置</th><th>冷链</th><th>时间</th></tr>
    </thead>
    <tbody>
      {#each handovers as h}
        <tr>
          <td>{h.id}</td>
          <td class="mono small">{h.bagNo}{#if h.cold}<div><span class="tag cold">❄冷藏</span></div>{/if}</td>
          <td class="small">{h.handoverType === 'TO_DISPOSAL' ? '送处置单位' : '内部转交'}</td>
          <td>
            {#if h.status === 'PENDING'}<span class="tag warn">待签收</span>
            {:else if h.status === 'ACCEPTED'}<span class="tag ok">已签收</span>
            {:else}<span class="tag danger">已拒收</span>{/if}
          </td>
          <td class="small">{h.fromStaffName} → <strong>{h.toStaffName}</strong></td>
          <td class="small mono">{h.fromLocation} → {h.toLocation}</td>
          <td class="small">{#if h.cold}{h.transferTemperature}℃ · {h.coldChainDevice}{:else}—{/if}</td>
          <td class="small muted">转 {h.transferredAt}{#if h.acceptedAt}<br />收 {h.acceptedAt}{/if}</td>
        </tr>
      {/each}
    </tbody>
  </table>
  {#if handovers.length === 0}<div class="empty">暂无交接记录</div>{/if}
</div>
