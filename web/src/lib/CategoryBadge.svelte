<script>
  // 类别徽标：危险药品 / 冷藏药品始终带明确文字与符号，颜色仅作辅助（不只靠颜色）
  export let category;
  export let label;
  export let dangerous;
  export let cold;
</script>

{#if dangerous}
  <span class="tag danger" title={category}>▲ 危险药品</span>
{:else if cold}
  <span class="tag cold" title={category}>❄ 需冷藏 2-8℃</span>
{:else}
  <span class="tag plain" title={category}>{label}</span>
{/if}
