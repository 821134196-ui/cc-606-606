import { writable } from 'svelte/store';

// 全局轻提示（成功/失败），失败信息直接显示后端返回的中文规则说明
export const toasts = writable([]);

let seq = 0;
export function toast(message, kind = 'ok', timeout = 4200) {
  const id = ++seq;
  toasts.update((list) => [...list, { id, message, kind }]);
  setTimeout(() => {
    toasts.update((list) => list.filter((t) => t.id !== id));
  }, timeout);
}
