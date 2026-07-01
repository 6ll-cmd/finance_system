<script setup>
import { apiFetch } from '../api.js'
import { onMounted, ref, watch } from 'vue'

const user = JSON.parse(localStorage.getItem('fin_user') || 'null')
const token = () => localStorage.getItem('fin_token') || ''
const hdr = () => ({ Authorization: 'Bearer ' + token(), 'Content-Type': 'application/json' })

const form = ref({ provider: 'openai', baseUrl: 'https://api.openai.com/v1', model: 'gpt-4o', apiKey: '' })
const config = ref({ configured: false })
const state = ref({ loading: false, message: '', ok: null, protocol: '' })

const providerHelp = {
  openai: {
    name: 'OpenAI / 兼容接口',
    baseUrl: 'https://api.openai.com/v1',
    model: 'gpt-4o',
    hint: '适用于 OpenAI、DeepSeek、通义千问兼容模式等 /chat/completions 接口。'
  },
  anthropic: {
    name: 'Anthropic Claude',
    baseUrl: 'https://api.anthropic.com/v1',
    model: 'claude-3-5-sonnet-latest',
    hint: '适用于 Anthropic Messages API。'
  }
}

watch(() => form.value.provider, provider => {
  const preset = providerHelp[provider] || providerHelp.openai
  if (!form.value.baseUrl || form.value.baseUrl === providerHelp.openai.baseUrl || form.value.baseUrl === providerHelp.anthropic.baseUrl) {
    form.value.baseUrl = preset.baseUrl
  }
  if (!form.value.model || form.value.model === providerHelp.openai.model || form.value.model === providerHelp.anthropic.model) {
    form.value.model = preset.model
  }
})

async function loadConfig() {
  state.value = { loading: true, message: '正在加载 AI 配置...', ok: null, protocol: '' }
  try {
    const r = await apiFetch('/api/ai/config', { headers: hdr() })
    const d = await r.json().catch(() => ({}))
    if (!r.ok) throw new Error(d.error || '加载失败')
    config.value = d
    form.value.provider = d.provider || 'openai'
    form.value.baseUrl = d.baseUrl || providerHelp[form.value.provider]?.baseUrl || providerHelp.openai.baseUrl
    form.value.model = d.model || providerHelp[form.value.provider]?.model || providerHelp.openai.model
    form.value.apiKey = ''
    state.value = {
      loading: false,
      message: d.configured ? '已加载保存的 AI 配置' : '尚未保存 API Key',
      ok: d.configured,
      protocol: ''
    }
  } catch (e) {
    state.value = { loading: false, message: e.message || 'AI 配置加载失败', ok: false, protocol: '' }
  }
}

async function saveConfig() {
  state.value = { loading: true, message: '正在保存...', ok: null, protocol: '' }
  try {
    const r = await apiFetch('/api/ai/config', { method: 'POST', headers: hdr(), body: JSON.stringify(form.value) })
    const d = await r.json().catch(() => ({}))
    if (!r.ok) throw new Error(d.error || '保存失败')
    config.value = d
    form.value.provider = d.provider || form.value.provider
    form.value.baseUrl = d.baseUrl || form.value.baseUrl
    form.value.model = d.model || form.value.model
    form.value.apiKey = ''
    state.value = {
      loading: false,
      message: d.configured ? 'AI 配置已保存' : '配置已保存，请补充 API Key',
      ok: d.configured,
      protocol: ''
    }
  } catch (e) {
    state.value = { loading: false, message: e.message || '保存失败', ok: false, protocol: '' }
  }
}

async function testAi() {
  if (!form.value.apiKey && !config.value.configured) {
    state.value = { loading: false, message: '请先填写或保存 API Key', ok: false, protocol: '' }
    return
  }
  state.value = { loading: true, message: '正在测试连接...', ok: null, protocol: '' }
  try {
    const r = await apiFetch('/api/ai/test', { method: 'POST', headers: hdr(), body: JSON.stringify(form.value) })
    const d = await r.json().catch(() => ({}))
    state.value = {
      loading: false,
      message: d.ok ? `连接正常：${d.reply || '模型已响应'}` : (d.error || '连接失败'),
      ok: Boolean(d.ok),
      protocol: d.protocol || ''
    }
  } catch (e) {
    state.value = { loading: false, message: e.message || '连接失败', ok: false, protocol: '' }
  } finally {
    form.value.apiKey = ''
  }
}

const opSet = ref(false)
const opPw = ref({ current: '', next: '', confirm: '', loading: false, message: '', ok: null })
const reveal = ref({ show: false, revealed: '', loginPw: '', loading: false, message: '', ok: null })

async function loadOpStatus() {
  try {
    const r = await apiFetch('/api/operation-password/status', { headers: hdr() })
    const d = await r.json().catch(() => ({}))
    opSet.value = !!d.set
  } catch (e) {
    opSet.value = false
  }
}

async function saveOperationPassword() {
  if (!opPw.value.current) { opPw.value = { ...opPw.value, message: '请输入登录密码', ok: false }; return }
  if (!opPw.value.next || opPw.value.next.length < 4) { opPw.value = { ...opPw.value, message: '操作密码至少 4 位', ok: false }; return }
  if (opPw.value.next !== opPw.value.confirm) { opPw.value = { ...opPw.value, message: '两次输入不一致', ok: false }; return }
  opPw.value = { ...opPw.value, loading: true, message: '正在保存...', ok: null }
  try {
    const r = await apiFetch('/api/operation-password', {
      method: 'PUT',
      headers: hdr(),
      body: JSON.stringify({ currentPassword: opPw.value.current, newPassword: opPw.value.next })
    })
    const d = await r.json().catch(() => ({}))
    if (!r.ok) throw new Error(d.error || '保存失败')
    opPw.value = { current: '', next: '', confirm: '', loading: false, message: '操作密码已保存', ok: true }
    opSet.value = true
    reveal.value.revealed = ''
  } catch (e) {
    opPw.value = { ...opPw.value, loading: false, message: e.message || '保存失败', ok: false }
  }
}

async function revealOpPassword() {
  if (!reveal.value.loginPw) {
    reveal.value = { ...reveal.value, message: '请输入登录密码以验证身份', ok: false }
    return
  }
  reveal.value = { ...reveal.value, loading: true, message: '正在验证...', ok: null }
  try {
    const r = await apiFetch('/api/operation-password/reveal', {
      method: 'POST',
      headers: hdr(),
      body: JSON.stringify({ loginPassword: reveal.value.loginPw })
    })
    const d = await r.json().catch(() => ({}))
    if (!r.ok) throw new Error(d.error || '验证失败')
    reveal.value = { show: true, revealed: d.password, loginPw: '', loading: false, message: '', ok: null }
  } catch (e) {
    reveal.value = { ...reveal.value, loading: false, message: e.message || '验证失败', ok: false }
  }
}

function resetOpMode() {
  opSet.value = false
  opPw.value = { current: '', next: '', confirm: '', loading: false, message: '请重新设置操作密码', ok: null }
}

onMounted(() => { loadConfig(); loadOpStatus() })
</script>

<template>
  <h1>系统设置</h1>

  <div class="card settings-card">
    <h2>账号</h2>
    <div class="detail-grid">
      <div class="k">当前用户</div>
      <div class="v">{{ user?.username || '-' }}</div>
      <div class="k">登录状态</div>
      <div class="v"><span class="pill pill-reimbursed">已登录</span></div>
    </div>
  </div>

  <div class="card settings-card">
    <h2>AI 配置</h2>
    <div class="detail-grid">
      <div class="k">保存状态</div>
      <div class="v">
        <span :class="['pill', config.configured ? 'pill-reimbursed' : 'pill-pending']">
          {{ config.configured ? '已保存 API Key' : '未保存 API Key' }}
        </span>
      </div>
      <div class="k">协议</div>
      <div class="v">{{ providerHelp[form.provider]?.hint }}</div>
    </div>

    <div class="form-grid ai-grid">
      <div class="form-group">
        <label>模型提供商</label>
        <select class="input" v-model="form.provider">
          <option value="openai">OpenAI / 兼容接口</option>
          <option value="anthropic">Anthropic Claude</option>
        </select>
      </div>
      <div class="form-group">
        <label>Base URL</label>
        <input class="input" v-model="form.baseUrl" placeholder="https://api.openai.com/v1">
      </div>
      <div class="form-group">
        <label>模型</label>
        <input class="input" v-model="form.model" placeholder="gpt-4o">
      </div>
      <div class="form-group full">
        <label>API Key</label>
        <input class="input" v-model="form.apiKey" type="password" autocomplete="off" placeholder="保存后不会在页面回显">
      </div>
    </div>

    <div class="flex">
      <button class="btn" :disabled="state.loading" @click="saveConfig">保存配置</button>
      <button class="btn btn-o" :disabled="state.loading" @click="testAi">测试连接</button>
      <span v-if="state.protocol" class="hint">协议：{{ state.protocol }}</span>
      <span v-if="state.message" :style="{color: state.ok === false ? 'var(--danger)' : 'var(--success)'}">{{ state.message }}</span>
    </div>
  </div>

  <div class="card settings-card">
    <h2>操作密码</h2>
    <p class="setting-note">用于删除凭证/发票、退回发票等敏感操作，请妥善保管。</p>

    <template v-if="opSet">
      <div class="flex op-row">
        <span class="pill pill-reimbursed">已设置</span>
        <input v-if="reveal.revealed" class="input op-secret" type="text" :value="reveal.revealed" readonly>
        <span v-else class="hint">••••••</span>
        <button class="btn btn-s btn-o" @click="reveal.show = !reveal.show; reveal.revealed = ''">
          {{ reveal.show ? '收起' : '查看' }}
        </button>
      </div>

      <template v-if="reveal.show && !reveal.revealed">
        <p class="setting-note">查看操作密码需要重新输入登录密码验证身份。</p>
        <input class="input" v-model="reveal.loginPw" type="password" autocomplete="off" placeholder="登录密码">
        <div class="flex">
          <button class="btn btn-s" @click="revealOpPassword" :disabled="reveal.loading">验证并查看</button>
          <span v-if="reveal.message" :style="{color: reveal.ok === false ? 'var(--danger)' : 'var(--success)'}">{{ reveal.message }}</span>
        </div>
      </template>

      <div class="flex mt">
        <button class="btn btn-o btn-s" @click="resetOpMode">重新设置操作密码</button>
      </div>
    </template>

    <template v-else>
      <input class="input" v-model="opPw.current" type="password" autocomplete="off" placeholder="当前登录密码">
      <input class="input" v-model="opPw.next" type="password" autocomplete="new-password" placeholder="新的操作密码（至少 4 位）">
      <input class="input" v-model="opPw.confirm" type="password" autocomplete="new-password" placeholder="再次输入新操作密码">
      <div class="flex">
        <button class="btn" :disabled="opPw.loading" @click="saveOperationPassword">保存操作密码</button>
        <span v-if="opPw.message" :style="{color: opPw.ok === false ? 'var(--danger)' : 'var(--success)'}">{{ opPw.message }}</span>
      </div>
    </template>
  </div>
</template>

<style scoped>
.settings-card{max-width:780px}
.ai-grid{margin:16px 0}
.setting-note{color:var(--muted);font-size:13px;margin:0 0 12px}
.op-row{align-items:center;gap:12px}
.op-secret{max-width:260px;margin-bottom:0;font-family:ui-monospace,SFMono-Regular,Menlo,monospace}
</style>
