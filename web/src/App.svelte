<script>
  import { onMount } from 'svelte';
  import { api } from './api.js';
  import { toasts } from './lib/toast.js';
  import AppointmentsPage from './pages/AppointmentsPage.svelte';
  import BagsPage from './pages/BagsPage.svelte';
  import HandoversPage from './pages/HandoversPage.svelte';
  import ResidentPage from './pages/ResidentPage.svelte';

  let tab = 'appointments';
  let categories = [];
  let staff = [];

  const tabs = [
    { key: 'appointments', label: '收件核对 · 分类封袋' },
    { key: 'bags', label: '封袋管理 · 袋号追溯' },
    { key: 'handovers', label: '保管交接 · 处置签收' },
    { key: 'resident', label: '居民凭证查询' }
  ];

  onMount(async () => {
    try {
      [categories, staff] = await Promise.all([
        api.get('/api/categories'),
        api.get('/api/staff')
      ]);
    } catch (e) {
      // 错误由具体页面 toast
    }
  });
</script>

<header class="app-header">
  <div>
    <h1>社区药箱 · 过期药回收与分类交接系统</h1>
    <div class="sub">核对预约 → 差异居民确认 → 分类封袋 → 保管交接 → 处置单位签收（扫码 / 通知均为本地模拟）</div>
  </div>
  <nav class="tabs">
    {#each tabs as t}
      <button class={tab === t.key ? 'active' : ''} on:click={() => (tab = t.key)}>
        {t.label}
      </button>
    {/each}
  </nav>
</header>

<main>
  {#if tab === 'appointments'}
    <AppointmentsPage {categories} {staff} />
  {:else if tab === 'bags'}
    <BagsPage {categories} {staff} />
  {:else if tab === 'handovers'}
    <HandoversPage {categories} {staff} />
  {:else}
    <ResidentPage />
  {/if}
</main>

<div class="toast">
  {#each $toasts as t (t.id)}
    <div class="msg {t.kind}">{t.message}</div>
  {/each}
</div>
