import test from 'node:test';
import assert from 'node:assert/strict';
import { presetSize } from '../common/src/main/assets/web/display-menu.js';
import { mapPoint } from '../common/src/main/assets/web/app.js';

test('presets preserve viewport aspect and apply scaling after preset cap', () => {
  assert.deepEqual(presetSize('2k', 1600, 1000), { width: 2304, height: 1440 });
  assert.deepEqual(presetSize('2k', 1600, 1000, .5), { width: 1152, height: 720 });
  assert.deepEqual(presetSize('fit', 1600, 1000), { width: 1600, height: 1000 });
  assert.deepEqual(presetSize('fit', 1600, 1000, .75), { width: 1200, height: 750 });
  assert.deepEqual(presetSize('720p', 600, 1000), { width: 432, height: 720 });
});
test('protocol bounds retain aspect at minimum and reject unusable ratios', () => {
  assert.deepEqual(presetSize('fit', 640, 320, .5), { width: 640, height: 320 });
  assert.equal(presetSize('fit', 1600, 320), null);
  assert.equal(presetSize('fit', 8194, 4000), null);
  assert.equal(presetSize('fit', 1600, 1000, .3), null);
});
test('letterbox areas never generate initial contacts for a saved window size', () => {
  const rect = { left: 0, top: 0, width: 1000, height: 1000 };
  assert.equal(mapPoint(500, 100, rect, 1600, 900), null);
  assert.deepEqual(mapPoint(500, 500, rect, 1600, 900), { x: .5, y: .5 });
});
