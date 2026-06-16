document.addEventListener('click', (event) => {
  const trigger = event.target.closest('[data-open-drawer]');
  if (trigger) document.body.classList.add('drawer-visible');
});