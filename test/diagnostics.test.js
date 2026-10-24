import test from 'node:test';
import assert from 'node:assert/strict';
import { diagnosticRows } from '../common/src/main/assets/web/diagnostics.js';
import { resourceStrings } from '../scripts/resource-strings.mjs';
globalThis.teslaplayMessages = resourceStrings('values-zh-rCN');
const fields = value => Object.fromEntries(diagnosticRows(value));

test('diagnostics report received size and measured rates, never configured rates', () => {
  const rows = fields({ now: 4000, receivedAt: 3000, device: { sampledAt: 3000, thermalStatus: 2 },
    visible: true, frame: { transport: 'rtc', width: 1920, height: 1080 }, receiver: { decodedFps: 28.5, presentedFps: 27.2 }, rtcState: 'connected' });
  assert.equal(rows['收到尺寸'], '1920 × 1080');
  assert.equal(rows['解码帧率'], '28.5 FPS');
  assert.equal(rows['显示帧率'], '27.2 FPS');
  assert.equal(rows['系统热状态'], 2);
  assert.equal(rows['SoC 温度'], '未知');
});

test('unavailable and expired counters stay unknown instead of showing old frames', () => {
  const rows = fields({ now: 10000, receivedAt: 1000, device: { sampledAt: 1000, thermalStatus: 0 },
    visible: true, frame: { transport: 'rtc', width: 1920, height: 1080 }, receiver: { decodedFps: 60 } });
  assert.equal(rows['收到尺寸'], '未知');
  assert.equal(rows['解码帧率'], '未知');
  assert.equal(rows['系统热状态'], '未知');
  assert.match(rows['设备采样时间'], /未更新/);
});

test('image fallback reports its real transport without inherited WebRTC FPS', () => {
  const rows = fields({ now: 1000, receivedAt: 1000, device: {}, visible: true,
    frame: { transport: 'image', mime: 'image/webp', width: 1280, height: 720 }, receiver: { decodedFps: 60 } });
  assert.equal(rows['实际传输'], 'image/webp / WebSocket');
  assert.equal(rows['解码帧率'], '未知');
});
