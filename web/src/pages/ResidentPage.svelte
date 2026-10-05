<script>
  import { api } from '../api.js';
  import { toast } from '../lib/toast.js';

  let code = '';
  let token = '';
  let receipt = null;
  let notices = [];
  let loading = false;

  async function query() {
    if (!code.trim() || !token.trim()) {
      toast('请输入凭证编号和查询码', 'error');
      return;
    }
    loading = true;
    receipt = null;
    notices = [];
    try {
      receipt = await api.post('/api/resident/receipt', {
        appointmentCode: code.trim(),
        tokenCode: token.trim()
      });
      notices = await api.post('/api/resident/notifications', {
        appointmentCode: code.trim(),
        tokenCode: token.trim()
      });
      toast('已查询到您本人的交回凭证');
    } catch (e) {
      if (e.status === 403) {
        toast('凭证编号或查询码不正确，查询不到任何信息（每位居民只能查询本人凭证）', 'error', 6000);
      } else {
        toast(e.message, 'error');
      }
    }
    loading = false;
  }

  function statusText(s) {
    return { PENDING: '已预约，待现场核对', RECEIVED: '已收件封袋，处置中', COMPLETED: '处置单位已完成签收' }[s] || s;
  }
</script>

<div class="panel" style="max-width:760px;margin:0 auto;">
  <h2>居民交回凭证查询</h2>
  <div class="banner info">
    <div class="banner-head">隐私保护说明</div>
    本页面凭交药时短信中的<strong>凭证编号</strong>与<strong>6 位查询码</strong>查询，
    只能看到<strong>您本人</strong>的交回与处置进度；系统不会展示其他居民的药品明细，
    也不展示工作人员、保管库位、交接过程等内部资料。
  </div>

  <div class="grid two">
    <label>凭证编号
      <input class="mono" bind:value={code} placeholder="如：YY100001" />
    </label>
    <label>查询码（6 位）
      <input class="mono" maxlength="6" bind:value={token} placeholder="如：111111" />
    </label>
  </div>
  <div class="btn-row">
    <button class="btn" on:click={query} disabled={loading}>{loading ? '查询中…' : '查询我的凭证'}</button>
  </div>

  {#if receipt}
    <div class="panel" style="background:#fafbfc;margin-top:1rem;">
      <h3>交回凭证 <span class="mono code-chip">{receipt.appointmentCode}</span></h3>
      <dl class="kv">
        <dt>居民</dt><dd>{receipt.residentName}</dd>
        <dt>当前状态</dt><dd><strong>{statusText(receipt.status)}</strong></dd>
        <dt>预约时间</dt><dd>{receipt.scheduledAt}</dd>
        <dt>收件时间</dt><dd>{receipt.receivedAt || '尚未现场收件'}</dd>
        <dt>数量核对</dt>
        <dd>
          预约 {receipt.plannedTotal} 件 · 实收 {receipt.receivedTotal} 件
          {#if receipt.differenceConfirmed === true}
            <span class="tag ok">差异已经您本人确认</span>
            <div class="muted small">{receipt.differenceNote}</div>
          {/if}
        </dd>
      </dl>

      <h3 style="margin-top:.7rem;">分类封袋与处置进度</h3>
      <table>
        <thead><tr><th>袋号</th><th>药品类别</th><th>数量</th><th>处置签收状态</th></tr></thead>
        <tbody>
          {#each receipt.bags as b}
            <tr>
              <td class="mono small">{b.bagNo}</td>
              <td>{b.categoryLabel}</td>
              <td>{b.qty} {b.unit}</td>
              <td>
                {#if b.signedOff}
                  <span class="tag ok">处置单位已签收 · {b.signedOffAt}</span>
                {:else}
                  <span class="tag warn">回收点保管 / 流转中</span>
                {/if}
              </td>
            </tr>
          {/each}
        </tbody>
      </table>
      {#if receipt.bags.length === 0}<div class="muted">药品尚在现场核对，还未分类封袋。</div>{/if}

      {#if notices.length > 0}
        <h3 style="margin-top:.8rem;">我的通知（本地模拟短信）</h3>
        <div class="timeline">
          {#each notices as n}
            <div class="node">
              <strong>{n.title}</strong>
              <div>{n.content}</div>
              <div class="t">{n.createdAt}</div>
            </div>
          {/each}
        </div>
      {/if}
    </div>
  {/if}
</div>
