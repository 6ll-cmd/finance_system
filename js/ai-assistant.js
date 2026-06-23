/**
 * 发票管家 — AI 助手面板
 * 全局嵌入，侧边栏底部触发
 */
(function() {
  if (document.getElementById('ai-panel')) return;

  var style = document.createElement('style');
  style.textContent = `
    .ai-panel {
      position:fixed;right:0;top:0;width:400px;max-width:90vw;height:100vh;
      background:var(--surface);border-left:1px solid var(--border);
      z-index:9999;display:flex;flex-direction:column;
      transform:translateX(100%);transition:transform 0.25s var(--ease);
      box-shadow:var(--shadow-lg);
    }
    .ai-panel.open { transform:translateX(0); }
    .ai-panel-header {
      display:flex;align-items:center;justify-content:space-between;
      padding:var(--space-md) var(--space-lg);
      border-bottom:1px solid var(--border);
      font-weight:590;
    }
    .ai-panel-header button {
      background:none;border:none;font-size:1.25rem;cursor:pointer;
      color:var(--muted);padding:4px 8px;
    }
    .ai-messages {
      flex:1;overflow-y:auto;padding:var(--space-md);
      display:flex;flex-direction:column;gap:var(--space-sm);
    }
    .ai-msg {
      max-width:85%;padding:10px 14px;border-radius:var(--radius-md);
      font-size:0.875rem;line-height:1.5;white-space:pre-wrap;word-break:break-word;
    }
    .ai-msg.user { align-self:flex-end;background:var(--accent);color:#fff; }
    .ai-msg.assistant { align-self:flex-start;background:var(--bg);border:1px solid var(--border); }
    .ai-msg.system { align-self:center;font-size:0.75rem;color:var(--muted);background:none;border:none; }
    .ai-input-row {
      display:flex;gap:var(--space-sm);padding:var(--space-md);
      border-top:1px solid var(--border);
    }
    .ai-input-row input { flex:1; }
    .ai-input-row button { flex-shrink:0; }
    .ai-typing { color:var(--muted);font-size:0.75rem;padding:4px 14px; }
    .ai-overlay {
      position:fixed;inset:0;background:rgba(0,0,0,0.3);z-index:9998;
      display:none;
    }
    .ai-overlay.show { display:block; }
  `;
  document.head.appendChild(style);

  // 面板 HTML
  var panel = document.createElement('div');
  panel.id = 'ai-panel';
  panel.className = 'ai-panel';
  panel.innerHTML = '<div class="ai-panel-header"><span>🤖 AI 助手</span><button onclick="AIPanel.close()">×</button></div>' +
    '<div class="ai-messages" id="ai-messages"><div class="ai-msg assistant">你好！我是发票管家 AI 助手。\n\n我可以帮你：\n• 录入发票（"帮我录入一张…"）\n• 查询统计（"本月发票情况"）\n• 分析建议\n• 解答财务问题</div></div>' +
    '<div class="ai-input-row"><input type="text" class="input" id="ai-input" placeholder="输入消息…" onkeydown="if(event.key===\'Enter\')AIPanel.send()" /><button class="btn btn-primary btn-sm" onclick="AIPanel.send()">发送</button></div>';
  document.body.appendChild(panel);

  var overlay = document.createElement('div');
  overlay.className = 'ai-overlay';
  overlay.onclick = function() { AIPanel.close(); };
  document.body.appendChild(overlay);

  var conversation = [];

  window.AIPanel = {
    open: function() {
      panel.classList.add('open');
      overlay.classList.add('show');
      document.getElementById('ai-input').focus();
    },
    close: function() {
      panel.classList.remove('open');
      overlay.classList.remove('show');
    },
    send: function() {
      var input = document.getElementById('ai-input');
      var msg = input.value.trim();
      if (!msg) return;
      input.value = '';
      conversation.push({ role: 'user', content: msg });
      AIPanel.addMessage('user', msg);
      AIPanel.addTyping();
      fetch('/api/ai/chat', {
        method: 'POST', headers: {'Content-Type': 'application/json'},
        body: JSON.stringify({ messages: conversation })
      }).then(function(r) { return r.json(); })
        .then(function(res) {
          AIPanel.removeTyping();
          if (res.error) { AIPanel.addMessage('assistant', '⚠️ ' + res.error); return; }
          var reply = res.reply || '收到，已处理。';
          conversation.push({ role: 'assistant', content: reply });
          AIPanel.addMessage('assistant', reply);
          // 如果 AI 创建了发票，刷新首页 iframe
          if (res.action === 'create_invoice') {
            var frame = document.querySelector('iframe[name="content-frame"]');
            if (frame) setTimeout(function() { frame.src = frame.src; }, 500);
          }
        })
        .catch(function() { AIPanel.removeTyping(); AIPanel.addMessage('assistant', '⚠️ AI 服务连接失败，请确认 config.json 已配置 API Key'); });
    },
    addMessage: function(role, text) {
      var div = document.createElement('div');
      div.className = 'ai-msg ' + role;
      div.textContent = text;
      document.getElementById('ai-messages').appendChild(div);
      div.scrollIntoView({ behavior: 'smooth' });
    },
    addTyping: function() {
      var el = document.createElement('div');
      el.className = 'ai-typing';
      el.id = 'ai-typing';
      el.textContent = 'AI 思考中…';
      document.getElementById('ai-messages').appendChild(el);
      el.scrollIntoView({ behavior: 'smooth' });
    },
    removeTyping: function() {
      var el = document.getElementById('ai-typing');
      if (el) el.remove();
    }
  };
})();
