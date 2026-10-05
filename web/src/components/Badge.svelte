<script>
  // 分类徽标：危险/冷藏用"图标+文字"双重标识，不仅依赖颜色
  export let category; // {hazardous, refrigerated, categoryLabel, label}
  export let small = false;
  let label = category?.categoryLabel || category?.label || category || '';
  $: kind = category?.hazardous ? 'danger' : category?.refrigerated ? 'cold' : 'normal';
  $: icon = category?.hazardous ? '☣ 危险药品' : category?.refrigerated ? '❄ 需冷藏' : '普通药品';
</script>

<span class="badge {kind}" class:small title={label}>
  <span aria-hidden="true">{category?.hazardous ? '☣' : category?.refrigerated ? '❄' : '○'}</span>
  {category?.hazardous ? '危险药品' : category?.refrigerated ? '需冷藏' : '普通药品'}
</span>

<style>
  .small { font-size: 11.5px; padding: 1px 7px; }
</style>
