import { t } from './text.js';
// SPDX-License-Identifier: AGPL-3.0-only
// UI fields informed by DiPlay-tesla a8aa630c79387675aba209e825fd0fd3e0e3c6cf,
// browser/src/main/assets/tesla/app.js; data comes from WheelPlay's existing counters.
export function diagnosticRows({ frame, visible, receiver, rtcState, device, receivedAt, now = Date.now() }) {
  const stateKeys = { ready: 'web_diag_ready', starting: 'web_diag_starting', stopped: 'web_diag_stopped',
    recovering: 'web_diag_recovering', disabled: 'web_diag_disabled', error: 'web_diag_error', pending: 'web_diag_pending',
    new: 'web_diag_new', connecting: 'web_diag_connecting', connected: 'web_diag_connected',
    disconnected: 'web_diag_disconnected', failed: 'web_diag_failed', closed: 'web_diag_closed' };
  const stateLabel = value => t(stateKeys[value] || 'web_diag_unknown');
  const unknown = t('web_diag_unknown');
  const fresh = receivedAt > 0 && now - receivedAt <= 6000;
  const state = fresh ? device : null;
  const fps = value => Number.isFinite(value) ? `${value.toFixed(1)} FPS` : unknown;
  const active = visible && fresh;
  return [
    [t('web_diag_service'), state ? state.serviceStarted ? t('web_diag_started') : t('web_diag_stopped') : unknown],
    [t('web_diag_request'), state?.clientRequestedAt ? new Date(state.clientRequestedAt).toLocaleString() : unknown],
    [t('web_diag_connection'), state ? t(state.clientConnected ? 'web_diag_yes' : 'web_diag_no') : unknown],
    [t('web_diag_projection'), active ? t('web_diag_displaying') : stateLabel(state?.sourceState)],
    [t('web_diag_transport'), active && frame ? frame.transport === 'rtc' ? 'WebRTC' : `${frame.mime || 'image'} / WebSocket` : unknown],
    [t('web_diag_dimensions'), active && frame?.width > 0 && frame?.height > 0 ? `${frame.width} × ${frame.height}` : unknown],
    [t('web_diag_decoded'), active && frame?.transport === 'rtc' ? fps(receiver?.decodedFps) : unknown],
    [t('web_diag_presented'), active && frame?.transport === 'rtc' ? fps(receiver?.renderedFps ?? receiver?.presentedFps) : unknown],
    ['WebRTC', stateLabel(rtcState)],
    [t('web_diag_thermal'), state?.thermalStatus ?? unknown],
    [t('web_diag_temperature'), Number.isFinite(state?.socTemperatureC) ? `${state.socTemperatureC} °C` : unknown],
    [t('web_diag_certificate'), stateLabel(state?.tlsState)],
    [t('web_diag_sample'), device?.sampledAt ? `${new Date(device.sampledAt).toLocaleString()}${fresh ? '' : ` (${t('web_diag_stale')})`}` : unknown],
  ];
}

export function createDiagnostics(element) {
  let device = null, receivedAt = 0, latest = {};
  const controller = {
    receive(value) { device = value ?? null; receivedAt = Date.now(); },
    update(value) {
      latest = value;
      if (!element?.open) return;
      const list = element.querySelector('dl');
      list.replaceChildren(...diagnosticRows({ ...value, device, receivedAt }).flatMap(([name, value]) => {
        const term = document.createElement('dt'), detail = document.createElement('dd');
        term.textContent = name; detail.textContent = String(value); return [term, detail];
      }));
    },
  };
  element?.addEventListener('toggle', () => controller.update(latest));
  setInterval(() => { if (element?.open) controller.update(latest); }, 1000);
  return controller;
}
