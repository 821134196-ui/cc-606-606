<script>
  import { onMount } from 'svelte';
  import { api } from '../api.js';
  import { toast } from '../lib/toast.js';
  import CategoryBadge from '../lib/CategoryBadge.svelte';
  import HandlingBanners from '../lib/HandlingBanners.svelte';
  import BagTraceModal from './BagTraceModal.svelte';

  export let categories = [];
  export let staff = [];

  let bags = [];
  let filter = '';
  let traceBagNo = null;

  async function load() {
    try {
      const path = filter ? `/api/bags?category=${encodeURIComponent(filter)}` : '/api/bags';
      bags = await api.get(path);
    } catch (e) {
      toast(e.message, 'error');
    }
  }
  onMount(load);

  function statusTag(s) {
    if (s === 'SEALED') return ['tag ok', '已封袋·冻结'];
    if (s === 'OPENED') return ['tag warn', '双人拆袋中'];
    return ['tag plain', '处置已签收'];
  }
</script>

<HandlingBanners {categories} />

<div class="panel">
  <div class="panel-title">
    <h2>分类封袋清单</h2>
    <span class="spacer"></span>
    <label style="margin:0;width:200px;">按类别筛选
      <select bind:value={filter} on:change={load}>
        <option value="">全部类别</option>
        {#each categories as c}
          <option value={c.code}>{c.label}</option>
        {/each}
      </select>
    </label>
    <button class="btn secondary small" on:click={load}>刷新</button>
  </div>

  <table>
    <thead>
      <tr>
        <th>袋号（扫码用）</th><th>类别</th><th>状态</th><th>数量</th>
        <th>保管位置</th><th>封袋人 / 复核人</th><th>封袋时间</th><th style="width:90px;">追溯</th>
      </tr>
    </thead>
    <tbody>
      {#each bags as b}
        <tr>
          <td class="mono">{b.bagNo}</td>
          <td><CategoryBadge category={b.category} label={b.categoryLabel} dangerous={b.dangerous} cold={b.cold} /></td>
          <td><span class={statusTag(b.status)[0]}>{statusTag(b.status)[1]}</span></td>
          <td>{b.totalQty} {b.unit}{#if b.sealTemperature != null}<div class="small muted">封袋 {b.sealTemperature}℃</div>{/if}</td>
          <td class="mono small">{b.storageLocation}</td>
          <td class="small">
            {b.sealedByName}{#if b.witnessName}<br /><span class="muted">复核：{b.witnessName}</span>{/if}
          </td>
          <td class="small muted">{b.sealedAt}</td>
          <td><button class="btn small secondary" on:click={() => (traceBagNo = b.bagNo)}>袋号追溯</button></td>
        </tr>
      {/each}
    </tbody>
  </table>
  {#if bags.length === 0}<div class="empty">暂无封袋</div>{/if}
</div>

{#if traceBagNo}
  <BagTraceModal bagNo={traceBagNo} {staff} on:close={() => (traceBagNo = null)} on:changed={load} />
{/if}
