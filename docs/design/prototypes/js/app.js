/* unexamine v3 — shared interactions */
(function () {
  'use strict';

  function qs(sel, root) { return (root || document).querySelector(sel); }
  function qsa(sel, root) { return Array.from((root || document).querySelectorAll(sel)); }

  /* ─── Tabs ─── */
  function initTabs() {
    qsa('[data-tabs]').forEach(function (group) {
      var buttons = qsa('[data-tab-btn]', group);
      var panels = qsa('[data-tab-panel]', group);
      buttons.forEach(function (btn) {
        btn.addEventListener('click', function () {
          var id = btn.getAttribute('data-tab-btn');
          buttons.forEach(function (b) { b.classList.toggle('active', b === btn); });
          panels.forEach(function (p) {
            p.classList.toggle('active', p.getAttribute('data-tab-panel') === id);
          });
        });
      });
    });
  }

  /* ─── Sub-tabs ─── */
  function initSubTabs() {
    qsa('[data-subtabs]').forEach(function (group) {
      var buttons = qsa('[data-subtab-btn]', group);
      var panels = qsa('[data-subtab-panel]', group);
      buttons.forEach(function (btn) {
        btn.addEventListener('click', function () {
          var id = btn.getAttribute('data-subtab-btn');
          buttons.forEach(function (b) { b.classList.toggle('active', b === btn); });
          panels.forEach(function (p) {
            p.classList.toggle('active', p.getAttribute('data-subtab-panel') === id);
          });
        });
      });
    });
  }

  /* ─── Modal ─── */
  function openModal(id) {
    var overlay = qs('#' + id);
    if (overlay) overlay.classList.add('open');
  }
  function closeModal(overlay) {
    if (overlay) overlay.classList.remove('open');
  }
  function initModals() {
    qsa('[data-open-modal]').forEach(function (btn) {
      btn.addEventListener('click', function () {
        openModal(btn.getAttribute('data-open-modal'));
      });
    });
    qsa('[data-close-modal]').forEach(function (btn) {
      btn.addEventListener('click', function () {
        closeModal(btn.closest('.modal-overlay'));
      });
    });
    qsa('.modal-overlay').forEach(function (overlay) {
      overlay.addEventListener('click', function (e) {
        if (e.target === overlay) closeModal(overlay);
      });
    });
  }

  /* ─── Drawer ─── */
  function openDrawer(id) {
    var overlay = qs('#' + id);
    if (overlay) overlay.classList.add('open');
  }
  function closeDrawer(overlay) {
    if (overlay) overlay.classList.remove('open');
  }
  function initDrawers() {
    qsa('[data-open-drawer]').forEach(function (btn) {
      btn.addEventListener('click', function () {
        openDrawer(btn.getAttribute('data-open-drawer'));
      });
    });
    qsa('[data-close-drawer]').forEach(function (btn) {
      btn.addEventListener('click', function () {
        closeDrawer(btn.closest('.drawer-overlay'));
      });
    });
    qsa('.drawer-overlay').forEach(function (overlay) {
      overlay.addEventListener('click', function (e) {
        if (e.target === overlay) closeDrawer(overlay);
      });
    });
  }

  /* ─── Toast ─── */
  var toastEl = null;
  function showToast(msg) {
    if (!toastEl) {
      toastEl = document.createElement('div');
      toastEl.className = 'toast';
      document.body.appendChild(toastEl);
    }
    toastEl.textContent = msg;
    toastEl.classList.add('show');
    clearTimeout(showToast._t);
    showToast._t = setTimeout(function () { toastEl.classList.remove('show'); }, 2400);
  }

  /* ─── Form validation ─── */
  function initForms() {
    qsa('[data-validate-form]').forEach(function (form) {
      form.addEventListener('submit', function (e) {
        e.preventDefault();
        var valid = true;
        qsa('.field', form).forEach(function (field) {
          field.classList.remove('has-error');
          var input = qs('input, select, textarea', field);
          if (!input) return;
          var required = input.hasAttribute('required');
          var val = input.value.trim();
          if (required && !val) {
            field.classList.add('has-error');
            valid = false;
          }
          if (input.type === 'email' && val && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(val)) {
            field.classList.add('has-error');
            valid = false;
          }
        });
        if (valid) {
          var msg = form.getAttribute('data-success') || '保存成功';
          showToast(msg);
          var redirect = form.getAttribute('data-redirect');
          if (redirect) setTimeout(function () { window.location.href = redirect; }, 800);
        }
      });
    });
  }

  /* ─── Table filter / search / sort ─── */
  function initTableTools() {
    qsa('[data-table]').forEach(function (tableWrap) {
      var table = qs('table', tableWrap);
      if (!table) return;
      var tbody = qs('tbody', table);
      if (!tbody) return;
      var rows = qsa('tr', tbody);
      var searchInput = qs('[data-table-search]', tableWrap);
      var filterSelect = qs('[data-table-filter]', tableWrap);
      var sortSelect = qs('[data-table-sort]', tableWrap);

      function applyFilters() {
        var q = searchInput ? searchInput.value.trim().toLowerCase() : '';
        var filterVal = filterSelect ? filterSelect.value : 'all';
        rows.forEach(function (row) {
          var text = row.textContent.toLowerCase();
          var status = row.getAttribute('data-status') || '';
          var matchSearch = !q || text.indexOf(q) !== -1;
          var matchFilter = filterVal === 'all' || status === filterVal;
          row.style.display = matchSearch && matchFilter ? '' : 'none';
        });
      }

      if (searchInput) searchInput.addEventListener('input', applyFilters);
      if (filterSelect) filterSelect.addEventListener('change', applyFilters);

      if (sortSelect) {
        sortSelect.addEventListener('change', function () {
          var key = sortSelect.value;
          var sorted = rows.slice().sort(function (a, b) {
            var av = a.getAttribute('data-sort-' + key) || a.cells[0].textContent;
            var bv = b.getAttribute('data-sort-' + key) || b.cells[0].textContent;
            return av.localeCompare(bv, 'zh-CN');
          });
          sorted.forEach(function (r) { tbody.appendChild(r); });
          rows = qsa('tr', tbody);
          applyFilters();
        });
      }
    });
  }

  /* ─── Runtime module group nav ─── */
  function initRuntimeNav() {
    qsa('[data-module-group]').forEach(function (btn) {
      btn.addEventListener('click', function () {
        qsa('[data-module-group]').forEach(function (b) { b.classList.remove('active'); });
        btn.classList.add('active');
        var group = btn.getAttribute('data-module-group');
        qsa('[data-sidebar-group]').forEach(function (sidebar) {
          sidebar.style.display = sidebar.getAttribute('data-sidebar-group') === group ? '' : 'none';
        });
      });
    });
  }

  /* ─── Export button ─── */
  function initExport() {
    qsa('[data-export]').forEach(function (btn) {
      btn.addEventListener('click', function () {
        showToast('正在导出当前列表…');
      });
    });
  }

  document.addEventListener('DOMContentLoaded', function () {
    initTabs();
    initSubTabs();
    initModals();
    initDrawers();
    initForms();
    initTableTools();
    initRuntimeNav();
    initExport();
  });

  window.unexamine = { openModal: openModal, openDrawer: openDrawer, showToast: showToast };
})();
