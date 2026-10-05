<script>
  import { onMount } from 'svelte';
  import { api } from './api.js';
  import StaffAppointments from './components/StaffAppointments.svelte';
  import BagsTrace from './components/BagsTrace.svelte';
  import Resident from './components/Resident.svelte';

  let tab = 'staff';
  let staffList = [];
  let currentUser = 'zhang';

  onMount(async () => {
    staffList = await api.staff();
  });
</script>

<header class="topbar">
  <div>
    <h1>社区药箱 · 过期药回收与分类交接系统</h1>
    <div class="sub">分类封袋 → 保管 → 转交 → 处置单位签收（扫码与通知为本地模拟）</div>
  </div>
  <nav class="tabs">
    <button class:active={tab === 'staff'} on:click={() => (tab = 'staff')}>现场收药/封袋</button>
    <button class:active={tab === 'bags'} on:click={() => (tab = 'bags')}>封袋保管与交接</button>
    <button class:active={tab === 'resident'} on:click={() => (tab = 'resident')}>居民凭证查询</button>
  </nav>
</header>

<main>
  {#if tab !== 'resident'}
    <div class="card" style="padding:10px 16px;">
      <div class="row" style="align-items:center;">
        <div style="flex:0 0 220px;">
          <label for="staffsel">当前登录工作人员（现场操作人）</label>
          <select id="staffsel" bind:value={currentUser}>
            {#each staffList as s}
              <option value={s.username}>{s.displayName}（{s.role === 'DISPOSAL' ? '处置单位' : '社区工作人员'}）</option>
            {/each}
          </select>
        </div>
        <div class="muted">拆袋须由两名不同工作人员复核；移交处置单位需扫描签收码。</div>
      </div>
    </div>
  {/if}

  {#if tab === 'staff'}
    <StaffAppointments {currentUser} />
  {:else if tab === 'bags'}
    <BagsTrace {currentUser} />
  {:else}
    <Resident />
  {/if}
</main>
