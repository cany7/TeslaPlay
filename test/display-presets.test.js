import test from 'node:test';
import assert from 'node:assert/strict';
import { mapPoint } from '../common/src/main/assets/web/app.js';

test('letterbox areas never generate initial contacts for a saved window size', () => {
  const rect = { left: 0, top: 0, width: 1000, height: 1000 };
  assert.equal(mapPoint(500, 100, rect, 1600, 900), null);
  assert.deepEqual(mapPoint(500, 500, rect, 1600, 900), { x: .5, y: .5 });
});
