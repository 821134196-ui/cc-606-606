<script>
  import { api } from '../api.js';
  import Badge from './Badge.svelte';

  let apptNo = '';
  let queryCode = '';
  let receipt = null;
  let notes = [];
  let error = '';

  async function query() {
    error = ''; receipt = null; notes = [];
    if (!apptNo.trim() || !queryCode.trim()) {
      error = '请输入交回单号和本人查询码';
      return;
    }
    try {
      receipt = await api.receipt(apptNo.trim(), queryCode.trim());
      notes = await api.residentInbox(apptNo.trim(), queryCode.trim());
    } catch (e) {
      error = e.message;
    }
  }

  const CAT_LABEL = {
    COMMON_SOLID: '普通固体（片剂/胶囊/粉剂）',
    COMMON_LIQUID: '普通液体（糖浆/口服液/注射液）',
    ANTIBIOTIC: '抗菌药及特殊管理药品',
    HAZARDOUS: '危险药品（细胞毒/易制毒/含汞等）',
    CHINESE_MED: '中成药/中药饮片',
    REFRIGERATED: '需冷藏药品（胰岛素/活菌制剂等）',
    OTHER: '其他过期药品/耗材'
  };
  const CAT_HAZ = { HAZARDOUS: true };
  const CAT_COLD = { REFRIGERATED: true };
</script>

<div style="max-width:760px;margin:0 auto;">
  <div class="card">
    <h2>居民交回凭证查询</h2>
    <p class="muted">
      请输入您交回时取得的<b>交回单号</b>与<b>本人查询码</b>。
      系统仅展示您本人单据的汇总凭证；药品具体明细、工作人员交接与保管资料不对居民开放，也无法查看他人信息。
    </p>
    <div class="grid cols2">
      <div><label>交回单号</label><input bind:value={apptNo} placeholder="如 A-DEMO-0001" /></div>
      <div><label>本人查询码</label><input bind:value={queryCode} placeholder="如 RC1001" /></div>
    </div>
    <div style="margin-top:10px;"><button class="btn" on:click={query}>查询我的凭证</button></div>
    <div class="sticky-note" style="margin-top:12px;">
      演示账号：周岚 <b>A-DEMO-0001 / RC1001</b>；吴磊 <b>A-DEMO-0002 / RC1002</b>。
      用吴磊的查询码查周岚的单号将被拒绝。
    </div>
    {#if error}<div class="alert danger" style="margin-top:12px;">⛔ {error}</div>{/if}
  </div>

  {#if receipt}
    <div class="card">
      <h2>交回凭证：{receipt.apptNo}</h2>
      <dl class="kv">
        <dt>居民</dt><dd>{receipt.residentName}（姓名已脱敏）</dd>
        <dt>预约申报</dt><dd>{receipt.declaredQty} 件</dd>
        <dt>现场实收</dt><dd>{receipt.receivedQty ?? '—'} 件</dd>
        {#if receipt.differenceNote}<dt>数量核对</dt><dd>{receipt.differenceNote}</dd>{/if}
        <dt>当前进度</dt><dd>
          {#if receipt.status === 'COMPLETED'}已完成全部封袋交接
          {:else if receipt.status === 'CHECKED'}已核对，封袋处理中
          {:else}待处理{/if}
        </dd>
      </dl>

      <h3>分类封袋汇总（仅类别与数量，不含药品名称）</h3>
      <table>
        <thead><tr><th>袋号</th><th>类别</th><th>数量</th><th>处置签收</th></tr></thead>
        <tbody>
          {#each receipt.bags as b}
            <tr>
              <td><b>{b.bagNo}</b></td>
              <td>
                <Badge category={{
                  hazardous: CAT_HAZ[b.category],
                  refrigerated: CAT_COLD[b.category],
                  label: CAT_LABEL[b.category]
                }} small />
                {CAT_LABEL[b.category]}
              </td>
              <td>{b.quantity} 件</td>
              <td>
                {#if b.disposalSigned}
                  <span class="badge ok">✓ 处置单位已签收</span>
                {:else}
                  <span class="pill">处理中</span>
                {/if}
              </td>
            </tr>
          {/each}
          {#if receipt.bags.length === 0}
            <tr><td colspan="4" class="muted">药品尚未完成封袋，请稍后查询。</td></tr>
          {/if}
        </tbody>
      </table>
      <p class="muted" style="margin-top:10px;">🔒 {receipt.notice}</p>
    </div>

    {#if notes.length}
    <div class="card">
      <h2>我的通知</h2>
      {#each notes as n}
        <div class="sticky-note" style="margin-bottom:6px;">{n.content}<div class="muted">{n.createdAt}</div></div>
      {/each}
    </div>
    {/if}
  {/if}
</div>
