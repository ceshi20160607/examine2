document.addEventListener('click', (event) => {
  const star = event.target.closest('.biz-star');
  if (star) {
    event.stopPropagation();
    star.classList.toggle('on');
    star.textContent = star.classList.contains('on') ? '★' : '☆';
    star.setAttribute('aria-label', star.classList.contains('on') ? '已关注' : '关注');
    return;
  }

  if (event.target.closest('[data-open-detail]')) {
    document.body.classList.add('detail-open');
    document.querySelectorAll('[data-open-detail]').forEach((row) => row.classList.remove('row-active'));
    event.target.closest('[data-open-detail]')?.classList.add('row-active');
    return;
  }

  if (event.target.closest('[data-open-filter]')) {
    document.body.classList.add('filter-open');
    return;
  }

  if (event.target.closest('[data-close-detail]')) {
    document.body.classList.remove('detail-open');
    return;
  }

  if (event.target.closest('[data-close-filter]')) {
    document.body.classList.remove('filter-open');
    return;
  }

  if (event.target.closest('[data-clear-selection]')) {
    document.querySelectorAll('.selection input[type="checkbox"]').forEach((el) => {
      el.checked = false;
    });
    return;
  }

  const viewBtn = event.target.closest('.biz-views button');
  if (viewBtn) {
    viewBtn.closest('.biz-views')?.querySelectorAll('button').forEach((b) => b.classList.remove('active'));
    viewBtn.classList.add('active');
  }

  const tabBtn = event.target.closest('.detail-tabs button, .detail-pane-tabs button');
  if (tabBtn) {
    const group = tabBtn.closest('.detail-tabs, .detail-pane-tabs');
    group?.querySelectorAll('button').forEach((b) => b.classList.remove('active'));
    tabBtn.classList.add('active');
  }
});

document.addEventListener('keydown', (event) => {
  if (event.key === 'Escape') {
    document.body.classList.remove('detail-open', 'filter-open');
  }
});
