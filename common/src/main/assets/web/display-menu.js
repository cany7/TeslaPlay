// SPDX-License-Identifier: GPL-3.0-only
// Size/panel behavior adapted from DiPlay-tesla a8aa630c (GPL-3.0-only).
import { t } from './text.js';

export function presetSize(preset, width, height, scale = 1) {
  if (![width, height, scale].every(Number.isFinite) || !Number.isInteger(width) || !Number.isInteger(height) || width % 2 || height % 2 || width < 320 || height < 320 || ![.5, .75, 1].includes(scale)) return null;
  const cap = { fit: [3840, 2160], '720p': [1280, 720], '1080p': [1920, 1080], '2k': [2560, 1440] }[preset];
  if (!cap || width / height < .5 || width / height > 4 || width > 8192 || height > 8192) return null;
  const fit = Math.min(preset === 'fit' ? 1 : Infinity, cap[0] / width, cap[1] / height);
  const factor = Math.max(fit * scale, 320 / width, 320 / height);
  return { width: Math.max(320, Math.floor(width * factor / 2) * 2), height: Math.max(320, Math.floor(height * factor / 2) * 2) };
}

export function createDisplayMenu({ send, release, viewport }) {
  const $ = id => document.getElementById(id);
  const menu = $('display-menu'), handle = $('menu-handle');
  let saved = null, dirty = false, timer, online = false, current = null;
  function peek() {
    clearTimeout(timer); handle.classList.add('peek');
    timer = setTimeout(() => {
      if (menu.hidden && !handle.matches(':focus-visible')) handle.classList.remove('peek');
    }, 2000);
  }
  function close(focus = true) {
    menu.hidden = true; handle.setAttribute('aria-expanded', 'false');
    if (focus) handle.focus();
    else if (menu.contains(document.activeElement)) document.activeElement.blur();
    peek();
  }
  function open() {
    release(); menu.hidden = false; handle.setAttribute('aria-expanded', 'true');
    clearTimeout(timer); handle.classList.add('peek'); $('menu-close').focus();
  }
  handle.addEventListener('click', event => menu.hidden ? open() : close(event.detail === 0));
  handle.addEventListener('pointerenter', peek); handle.addEventListener('focus', peek);
  handle.addEventListener('blur', peek); $('menu-close').addEventListener('click', event => close(event.detail === 0));
  for (const surface of [menu, handle, $('virtual-buttons')]) {
    surface.addEventListener('pointerdown', event => { release(); event.stopPropagation(); });
    surface.addEventListener('click', event => event.stopPropagation());
  }
  document.addEventListener('keydown', event => {
    if (event.key === 'Escape' && !menu.hidden) { event.preventDefault(); close(); }
    if (event.key === 'Tab' && !menu.hidden) {
      const controls = [...menu.querySelectorAll('button,input,select,a[href],summary')].filter(el => !el.disabled && el.getClientRects().length);
      const first = controls[0], last = controls.at(-1);
      if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last.focus(); }
      else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first.focus(); }
    }
  });
  document.addEventListener('visibilitychange', () => { if (document.hidden) { release(); close(false); } });
  function profile() { return saved?.[$('window-mode').value]; }
  function fill() {
    const p = profile(); if (!p) return;
    $('window-preset').value = p.preset; $('window-scale').value = String(p.scale);
    $('window-width').value = p.width; $('window-height').value = p.height;
    dirty = false; preview();
  }
  function draft() {
    return { mode: $('window-mode').value, preset: $('window-preset').value, scale: Number($('window-scale').value),
      width: Number($('window-width').value), height: Number($('window-height').value) };
  }
  function preview() {
    const p = draft(), size = presetSize(p.preset, p.width, p.height, p.scale);
    $('window-preview').textContent = size ? `${size.width} × ${size.height}` : t('tesla_invalid_size');
    const state = dirty ? t('tesla_unsaved') : !saved?.enabled ? t('tesla_auto') :
      t(current?.sourceState === 'ready' && !saved.pending ? 'tesla_in_use' : 'tesla_pending');
    const output = current?.sourceState === 'ready' ? `${current.width} × ${current.height}` : t('tesla_disconnected');
    $('window-state').textContent = `${state} · ${t('tesla_current')}: ${output}`;
    $('window-save').disabled = !online || !size; $('window-apply').disabled = !online || !size;
  }
  $('window-mode').addEventListener('change', () => { fill(); dirty = $('window-mode').value !== saved?.active; preview(); });
  for (const id of ['window-preset', 'window-scale', 'window-width', 'window-height']) $(id).addEventListener('input', () => { dirty = true; preview(); });
  $('window-use-viewport').addEventListener('click', () => {
    const size = viewport(); if (!size) return;
    $('window-width').value = size.width; $('window-height').value = size.height; dirty = true; preview();
  });
  function save(apply) {
    const p = draft(); if (!presetSize(p.preset, p.width, p.height, p.scale)) return;
    release(); send({ type: 'display-save', ...p, apply }); dirty = false;
  }
  $('window-save').addEventListener('click', () => save(false));
  $('window-apply').addEventListener('click', () => save(true));
  $('window-cancel').addEventListener('click', () => { if (saved) $('window-mode').value = saved.active; fill(); });
  $('show-virtual-buttons').addEventListener('change', () => send({ type: 'virtual-buttons', show: $('show-virtual-buttons').checked }));
  $('virtual-buttons').querySelectorAll('[data-action]').forEach(button => button.addEventListener('click', () => {
    release(); send({ type: 'virtual-button', action: button.dataset.action });
  }));
  peek();
  return {
    connected(value) {
      online = value; document.body.classList.toggle('projecting', value); handle.hidden = !value;
      if (!value) { $('virtual-buttons').hidden = true; close(false); } else peek();
      preview();
    },
    receive(data) {
      if (!data.displayPreferences) return;
      const next = data.displayPreferences, changed = JSON.stringify(saved) !== JSON.stringify(next);
      saved = next; current = data;
      if (changed && !dirty) { $('window-mode').value = saved.active; fill(); }
      $('show-virtual-buttons').checked = saved.buttons;
      $('virtual-buttons').hidden = !online || !saved.buttons;
      $('virtual-buttons').querySelectorAll('button').forEach(button => { button.disabled = data.sourceId !== 'carplay' || !data.inputReady; });
      preview();
      // Honor the previous browser preference on the first upgrade, then use shared Android storage.
      if (!saved.buttonsSet) {
        let previous = null;
        try { previous = localStorage.getItem('diplay-virtual-keys') ?? localStorage.getItem('teslaplay.virtualButtons'); } catch (_) {}
        if (previous !== null) send({ type: 'virtual-buttons', show: previous === 'true' });
      }
    },
  };
}
