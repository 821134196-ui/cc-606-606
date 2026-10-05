<script>
  // 现场处理要求横幅：文字 + 符号，不只靠颜色
  export let categories = [];
  $: dangerous = categories.filter((c) => c.dangerous);
  $: cold = categories.filter((c) => c.cold);
</script>

{#if dangerous.length > 0}
  <div class="banner danger" role="alert">
    <div class="banner-head">▲ 危险药品处理要求（必须执行，不以颜色判断）</div>
    <ul>
      {#each dangerous as c}
        <li><strong>{c.label}：</strong>{c.handlingTip}</li>
      {/each}
    </ul>
  </div>
{/if}
{#if cold.length > 0}
  <div class="banner cold" role="status">
    <div class="banner-head">❄ 需冷藏药品处理要求（全程冷链 2-8℃）</div>
    <ul>
      {#each cold as c}
        <li><strong>{c.label}：</strong>{c.handlingTip}</li>
      {/each}
    </ul>
  </div>
{/if}

<style>
  ul { margin: .2rem 0 0; padding-left: 1.2rem; }
  li { margin: .15rem 0; }
</style>
