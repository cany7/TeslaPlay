import test from 'node:test';
import assert from 'node:assert/strict';
import { migrateBrowserStorage } from '../common/src/main/assets/web/brand-storage.js';

test('branding migration preserves pairing and never overwrites newer browser preferences', () => {
  const credential = JSON.stringify({ id: 'existing-device', secret: 'existing-secret' });
  const values = new Map([
    ['wheelplay.browserDevice', credential],
    ['wheelplay.videoRenderer', 'canvas'],
    ['teslaplay.videoRenderer', 'video'],
  ]);
  const storage = { getItem: key => values.get(key) ?? null, setItem: (key, value) => values.set(key, value) };
  migrateBrowserStorage(storage);
  assert.equal(values.get('teslaplay.browserDevice'), credential);
  assert.equal(values.get('wheelplay.browserDevice'), credential);
  assert.equal(values.get('teslaplay.videoRenderer'), 'video');
  values.set('teslaplay.browserDevice', 'new-pairing');
  migrateBrowserStorage(storage);
  assert.equal(values.get('teslaplay.browserDevice'), 'new-pairing');
  values.delete('teslaplay.browserDevice');
  migrateBrowserStorage(storage);
  assert.equal(values.has('teslaplay.browserDevice'), false, 'revoked credentials must not be restored on reload');
});
