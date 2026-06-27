<script setup>
import { apiFetch } from '../api.js'
import { onMounted, ref } from 'vue'

const user = JSON.parse(localStorage.getItem('fin_user') || 'null')
const token = () => localStorage.getItem('fin_token') || ''
const hdr = () => ({ Authorization: 'Bearer ' + token(), 'Content-Type': 'application/json' })
const form = ref({ provider: 'openai', baseUrl: '', model: 'gpt-4o', apiKey: '' })
const config = ref({ configured: false })
const state = ref({ loading: false, message: '', ok: null })

async function loadConfig() {
  state.value = { loading: true, message: '正在加载 AI 配置...', ok: null }
  try {
    const r = await apiFetch('/api/ai/config', { headers: hdr() })
    const d = await r.json()
    if (!r.ok) throw new Error(d.error || '加载失败')
    config.value = d
    form.value.provider = d.provider || 'openai'
    form.value.baseUrl = d.baseUrl || ''
    form.value.model = d.model || 'gpt-4o'
    form.value.apiKey = ''
    state.value = { loading: false, message: d.configured ? '已加载保存的 AI 配置' : '尚未保存 API Key', ok: d.configured }
  } catch (e) {
    state.value = { loading: false, message: e.message || 'AI 配置加载失败', ok: false }
  }
}

async function saveConfig() {
  state.value = { loading: true, message: '正在保存...', ok: null }
  try {
    const r = await apiFetch('/api/ai/config', { method: 'POST', headers: hdr(), body: JSON.stringify(form.value) })
    const d = await r.json()
    if (!r.ok) throw new Error(d.error || '保存失败')
    config.value = d
    form.value.provider = d.provider || form.value.provider
    form.value.baseUrl = d.baseUrl || form.value.baseUrl
    form.value.model = d.model || form.value.model
    form.value.apiKey = ''
    state.value = { loading: false, message: d.configured ? 'AI 配置已保存' : '配置已保存，请补充 API Key', ok: d.configured }
  } catch (e) {
    state.value = { loading: false, message: e.message || '保存失败', ok: false }
  }
}

async function testAi() {
  if (!form.value.apiKey && !config.value.configured) {
    state.value = { loading: false, message: '请先填写或保存 API Key', ok: false }
    return
  }
  state.value = { loading: true, message: '测试中...', ok: null }
  try {
    const r = await apiFetch('/api/ai/test', { method: 'POST', headers: hdr(), body: JSON.stringify(form.value) })
    const d = await r.json()
    state.value = { loading: false, message: d.ok || d.success ? '连接正常' : (d.error || '连接失败'), ok: Boolean(d.ok || d.success) }
  } catch (e) {
    state.value = { loading: false, message: '连接失败', ok: false }
  } finally {
    form.value.apiKey = ''
  }
}

// operation password: two modes (not set / already set)
const opSet = ref(false)
const opPw = ref({ current: '', next: '', confirm: '', loading: false, message: '', ok: null })
const reveal = ref({ showEye: false, revealed: '', loginPw: '', loading: false, message: '', ok: null })

async function loadOpStatus() {
  try {
    const r = await apiFetch('/api/operation-password/status', { headers: hdr() })
    const d = await r.json()
    opSet.value = !!d.set
  } catch (e) { /* ignore */ }
}

async function saveOperationPassword() {
  if (!opPw.value.current) { opPw.value = { ...opPw.value, message: '请输入登录密码', ok: false }; return }
  if (!opPw.value.next || opPw.value.next.length < 4) { opPw.value = { ...opPw.value, message: '操作密码至少 4 位', ok: false }; return }
  if (opPw.value.next !== opPw.value.confirm) { opPw.value = { ...opPw.value, message: '两次输入不一致', ok: false }; return }
  opPw.value = { ...opPw.value, loading: true, message: '正在保存...', ok: null }
  try {
    const r = await apiFetch('/api/operation-password', {
      method: 'PUT', headers: hdr(),
      body: JSON.stringify({ currentPassword: opPw.value.current, newPassword: opPw.value.next })
    })
    const d = await r.json()
    if (!r.ok) throw new Error(d.error || '保存失败')
    opPw.value = { current: '', next: '', confirm: '', loading: false, message: '操作密码已保存', ok: true }
    opSet.value = true
    reveal.value.revealed = ''
  } catch (e) {
    opPw.value = { ...opPw.value, loading: false, message: e.message || '保存失败', ok: false }
  }
}

async function revealOpPassword() {
  if (!reveal.value.loginPw) { reveal.value = { ...reveal.value, message: '请输入登录密码以验证身份', ok: false }; return }
  reveal.value = { ...reveal.value, loading: true, message: '正在验证...', ok: null }
  try {
    const r = await apiFetch('/api/operation-password/reveal', {
      method: 'POST', headers: hdr(),
      body: JSON.stringify({ loginPassword: reveal.value.loginPw })
    })
    const d = await r.json()
    if (!r.ok) throw new Error(d.error || '验证失败')
    reveal.value = { showEye: true, revealed: d.password, loginPw: '', loading: false, message: '', ok: null }
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
  <div class="card" style="max-width:720px">
    <h2>账号</h2>
    <div class="detail-grid">
      <div class="k">当前用户</div>
      <div class="v">{{ user?.username || '-' }}</div>
      <div class="k">登录状态</div>
      <div class="v"><span class="pill pill-reimbursed">已登录</span></div>
    </div>
  </div>

  <div class="card" style="max-width:720px">
    <h2>AI 配置</h2>
    <div class="detail-grid">
      <div class="k">保存状态</div>
      <div class="v">
        <span :class="['pill', config.configured ? 'pill-reimbursed' : 'pill-pending']">
          {{ config.configured ? '已保存' : '未保存 API Key' }}
        </span>
      </div>
    </div>
    <div class="flex">
      <select class="grow" v-model="form.provider">
        <option value="openai">OpenAI / 兼容接口</option>
        <option value="anthropic">Anthropic Claude</option>
      </select>
      <input class="grow" v-model="form.baseUrl" placeholder="Base URL">
      <input class="grow" v-model="form.model" placeholder="模型">
    </div>
    <input v-model="form.apiKey" type="password" autocomplete="off" placeholder="API Key（保存后不会在页面回显）">
    <div class="flex">
      <button class="btn" :disabled="state.loading" @click="saveConfig">保存配置</button>
      <button class="btn btn-o" :disabled="state.loading" @click="testAi">测试连接</button>
      <span v-if="state.message" :style="{color: state.ok === false ? 'var(--danger)' : 'var(--success)'}">{{ state.message }}</span>
    </div>
  </div>

  <div class="card" style="max-width:720px">
    <h2>操作密码</h2>
    <p style="color:var(--text-muted,#888);font-size:13px;margin:0 0 12px">
      用于删除凭证/发票、修改已过账凭证、作废发票等敏感操作。请妥善保管。
    </p>

    <!-- 已设置模式 -->
    <template v-if="opSet">
      <div class="flex" style="align-items:center;gap:12px;margin-bottom:12px">
        <span class="pill pill-reimbursed">已设置</span>
        <div style="flex:1;display:flex;align-items:center;gap:8px">
          <input v-if="reveal.showEye" :type="'text'" :value="reveal.revealed" readonly style="flex:1;font-family:monospace">
          <span v-else style="color:var(--text-muted,#888);font-size:13px">••••••</span>
        </div>
        <button class="btn btn-s" @click="reveal.showEye = !reveal.showEye; reveal.revealed=''" :disabled="reveal.loading">
          <svg v-if="!reveal.showEye" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/><circle cx="12" cy="12" r="3"/></svg><svg v-else width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19m-6.72-1.07a3 3 0 1 1-4.24-4.24"/><line x1="1" y1="1" x2="23" y2="23"/></svg>
        </button>
      </div>

      <!-- 小眼睛验证区 -->
      <template v-if="reveal.showEye && !reveal.revealed">
        <p style="color:var(--text-muted,#888);font-size:13px;margin:0 0 8px">查看操作密码需重新输入登录密码验证身份</p>
        <input v-model="reveal.loginPw" type="password" autocomplete="off" placeholder="登录密码">
        <div class="flex" style="margin-top:8px">
          <button class="btn btn-s" @click="revealOpPassword" :disabled="reveal.loading">验证并查看</button>
          <span v-if="reveal.message" :style="{color: reveal.ok === false ? 'var(--danger)' : 'var(--success)'}">{{ reveal.message }}</span>
        </div>
      </template>

      <div class="flex" style="margin-top:12px">
        <button class="btn btn-o btn-s" @click="resetOpMode">重新设置操作密码</button>
      </div>
    </template>

    <!-- 未设置 / 重新设置模式 -->
    <template v-else>
      <input v-model="opPw.current" type="password" autocomplete="off" placeholder="当前登录密码（用于授权修改）">
      <input v-model="opPw.next" type="password" autocomplete="new-password" placeholder="新的操作密码（至少 4 位）">
      <input v-model="opPw.confirm" type="password" autocomplete="new-password" placeholder="再次输入新操作密码">
      <div class="flex">
        <button class="btn" :disabled="opPw.loading" @click="saveOperationPassword">保存操作密码</button>
        <span v-if="opPw.message" :style="{color: opPw.ok === false ? 'var(--danger)' : 'var(--success)'}">{{ opPw.message }}</span>
      </div>
    </template>
  </div>
</template>