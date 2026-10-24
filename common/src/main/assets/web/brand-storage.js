// Keep existing browser pairing and preferences when the application branding changes.
// The origin is unchanged; retain the old entries so the previous app remains usable.
export function migrateBrowserStorage(storage) {
  if (storage.getItem('teslaplay.storageMigrated') === '1') return;
  for (const suffix of ['browserDevice', 'ignoreBrowserZoom', 'videoRenderer', 'canvasVideo', 'virtualButtons']) {
    const current = `teslaplay.${suffix}`;
    if (storage.getItem(current) === null) {
      const previous = storage.getItem(`wheelplay.${suffix}`);
      if (previous !== null) storage.setItem(current, previous);
    }
  }
  storage.setItem('teslaplay.storageMigrated', '1');
}
