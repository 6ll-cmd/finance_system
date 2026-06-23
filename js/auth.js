/**
 * 发票管家 — 认证守卫
 * 所有页面引入此脚本，自动检查登录状态
 */
(function() {
  var TOKEN = localStorage.getItem('token');
  var USERNAME = localStorage.getItem('username');

  // 未登录 → 跳转登录页
  if (!TOKEN) {
    window.location.replace('/login.html');
    return;
  }

  // 验证 token 有效性
  fetch('/api/me', { headers: { 'Authorization': 'Bearer ' + TOKEN } })
    .then(function(r) {
      if (!r.ok) { logout(); return; }
      // 显示当前用户
      if (USERNAME) {
        var el = document.getElementById('current-user');
        if (el) { el.textContent = USERNAME; el.style.display = ''; }
      }
    })
    .catch(function() {});

  // 拦截所有 fetch 请求，自动注入 Authorization
  var origFetch = window.fetch;
  window.fetch = function(url, opts) {
    opts = opts || {};
    opts.headers = opts.headers || {};
    // 只对 API 请求注入 token
    if (typeof url === 'string' && url.startsWith('/api/') && !url.startsWith('/api/login') && !url.startsWith('/api/register')) {
      opts.headers['Authorization'] = 'Bearer ' + TOKEN;
    }
    return origFetch(url, opts).then(function(r) {
      if (r.status === 401) logout();
      return r;
    });
  };

  function logout() {
    localStorage.removeItem('token');
    localStorage.removeItem('username');
    window.location.replace('/login.html');
  }

  // 暴露退出登录
  window.logout = logout;

  // ── 暗色主题 ──
  var savedTheme = localStorage.getItem('theme') || 'light';
  document.documentElement.setAttribute('data-theme', savedTheme);
  window.toggleTheme = function() {
    var current = document.documentElement.getAttribute('data-theme') || 'light';
    var next = current === 'dark' ? 'light' : 'dark';
    document.documentElement.setAttribute('data-theme', next);
    localStorage.setItem('theme', next);
  };

  // 退出系统（关闭服务）
  window.shutdown = function() {
    showModal('确定要退出并关闭发票管家服务吗？', function() {
      fetch('/api/shutdown', { method: 'POST' })
        .then(function() { window.close(); })
        .catch(function() { window.close(); });
    });
  };

  // ── 自定义模态框（替换原生 confirm/alert）──
  window.showModal = function(msg, onConfirm) {
    var overlay = document.createElement('div');
    overlay.style.cssText = 'position:fixed;inset:0;background:rgba(0,0,0,0.35);z-index:99999;display:flex;align-items:center;justify-content:center;';
    overlay.innerHTML = '<div style="background:var(--surface);border:1px solid var(--border);border-radius:var(--radius-xl);padding:var(--space-xl) var(--space-2xl);max-width:380px;box-shadow:var(--shadow-lg);text-align:center;">' +
      '<p style="font-size:0.9375rem;margin-bottom:var(--space-lg);color:var(--fg);">' + msg + '</p>' +
      '<div style="display:flex;gap:var(--space-md);justify-content:center;">' +
      '<button id="modal-cancel" style="padding:8px 24px;border:1px solid var(--border);border-radius:var(--radius-md);background:var(--surface);color:var(--fg);cursor:pointer;font-size:0.875rem;">取消</button>' +
      '<button id="modal-confirm" style="padding:8px 24px;border:none;border-radius:var(--radius-md);background:var(--danger);color:#fff;cursor:pointer;font-size:0.875rem;">确认</button>' +
      '</div></div>';
    document.body.appendChild(overlay);
    overlay.querySelector('#modal-cancel').onclick = function() { overlay.remove(); };
    overlay.querySelector('#modal-confirm').onclick = function() { overlay.remove(); if(onConfirm) onConfirm(); };
    overlay.onclick = function(e) { if(e.target===overlay) overlay.remove(); };
  };

  window.showToast = function(msg, type) {
    var t = document.createElement('div');
    var bg = type==='error'?'var(--danger-bg)':type==='warn'?'var(--warn-bg)':'var(--success-bg)';
    var fg = type==='error'?'var(--danger)':type==='warn'?'var(--warn)':'var(--success)';
    t.style.cssText = 'position:fixed;top:20px;left:50%;transform:translateX(-50%);z-index:99999;background:'+bg+';color:'+fg+';padding:10px 24px;border-radius:var(--radius-full);font-size:0.875rem;box-shadow:var(--shadow-md);transition:opacity 0.3s;';
    t.textContent = msg;
    document.body.appendChild(t);
    setTimeout(function() { t.style.opacity='0'; setTimeout(function(){t.remove()},300); }, 3000);
  };
  // ── 全局覆盖原生 alert/confirm，统一项目风格 ──
  var _origAlert = window.alert;
  window.alert = function(msg) { showToast(msg, 'info'); };
  window.confirm = function(msg) { showModal(msg); return false; };
})();
