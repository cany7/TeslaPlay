// SPDX-License-Identifier: GPL-3.0-only
export function createVirtualButtons({ element, send, release, onFailure }) {
  let online = false;
  const buttons = [...element.querySelectorAll('[data-action]')];
  function reset() {
    element.hidden = true;
    element.classList.remove('day');
    buttons.forEach(button => { button.disabled = true; });
  }
  element.addEventListener('pointerdown', event => { release(); event.stopPropagation(); });
  element.addEventListener('click', event => event.stopPropagation());
  buttons.forEach(button => button.addEventListener('click', event => {
    event.stopPropagation();
    if (!online || element.hidden || button.disabled) return;
    release();
    if (!send({ type: 'virtual-button', action: button.dataset.action })) onFailure();
  }));
  reset();
  return {
    connected(value) { online = value; reset(); },
    receive(data) {
      element.hidden = !online || data.displayPreferences?.buttons !== true;
      element.classList.toggle('day', data.displayPreferences?.night === false);
      buttons.forEach(button => { button.disabled = data.sourceId !== 'carplay' || !data.inputReady; });
    },
  };
}
