<script setup>
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

const route = useRoute()
const router = useRouter()
const username = ref('')
const password = ref('')
const confirmPassword = ref('')
const error = ref('')
const loading = ref(false)

const isRegister = computed(() => route.path === '/register')
const title = computed(() => isRegister.value ? '注册账号' : '登录系统')
const actionText = computed(() => isRegister.value ? '注册并登录' : '登录')

function saveSession(data) {
  localStorage.setItem('fin_user', JSON.stringify({
    id: data.id,
    username: data.username,
    role: data.role || 'user'
  }))
  localStorage.setItem('fin_token', data.token)
  localStorage.setItem('fin_refresh', data.refreshToken || '')
}

async function readError(res, fallback) {
  const raw = await res.text().catch(() => '')
  if (!raw) return fallback
  try {
    const data = JSON.parse(raw)
    return data.error || fallback
  } catch (e) {
    return raw.trim() || fallback
  }
}

function validate() {
  if (!username.value.trim()) return '请输入用户名'
  if (!password.value) return '请输入密码'
  if (isRegister.value) {
    if (username.value.trim().length < 2) return '用户名至少 2 位'
    if (password.value.length < 8) return '密码至少 8 位'
    if (!/[A-Za-z]/.test(password.value) || !/\d/.test(password.value)) return '密码需要同时包含字母和数字'
    if (password.value !== confirmPassword.value) return '两次输入的密码不一致'
  }
  return ''
}

async function submit() {
  error.value = validate()
  if (error.value) return

  loading.value = true
  try {
    const res = await fetch(isRegister.value ? '/api/register' : '/api/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        username: username.value.trim(),
        password: password.value
      })
    })
    if (!res.ok) throw new Error(await readError(res, isRegister.value ? '注册失败' : '登录失败'))
    saveSession(await res.json())
    router.push('/')
  } catch (e) {
    error.value = e.message || (isRegister.value ? '注册失败' : '登录失败')
  } finally {
    loading.value = false
  }
}

function switchMode() {
  error.value = ''
  confirmPassword.value = ''
  router.push(isRegister.value ? '/login' : '/register')
}
</script>

<template>
  <div class="login-overlay">
    <div class="login-card">
      <div class="login-head">
        <h1>发票管家</h1>
        <p>广州共生纪元云科技有限公司</p>
      </div>

      <div class="mode-tabs login-tabs">
        <button class="mode-tab" :class="{ active: !isRegister }" @click="router.push('/login')">登录</button>
        <button class="mode-tab" :class="{ active: isRegister }" @click="router.push('/register')">注册</button>
      </div>

      <h2>{{ title }}</h2>
      <input v-model.trim="username" placeholder="用户名" autocomplete="username" @keyup.enter="submit">
      <input
        v-model="password"
        type="password"
        placeholder="密码"
        :autocomplete="isRegister ? 'new-password' : 'current-password'"
        @keyup.enter="submit"
      >
      <input
        v-if="isRegister"
        v-model="confirmPassword"
        type="password"
        placeholder="确认密码"
        autocomplete="new-password"
        @keyup.enter="submit"
      >

      <button class="btn" :disabled="loading" @click="submit" style="width:100%;justify-content:center">
        {{ loading ? '处理中...' : actionText }}
      </button>

      <button class="icon-text-btn login-switch" @click="switchMode">
        {{ isRegister ? '已有账号，去登录' : '没有账号，去注册' }}
      </button>
      <p v-if="error" class="login-error">{{ error }}</p>
    </div>
  </div>
</template>

<style scoped>
.login-head{text-align:center;margin-bottom:20px}
.login-head h1{margin-bottom:4px;font-size:22px}
.login-head p{color:var(--muted);font-size:13px}
.login-tabs{width:100%;margin-bottom:18px}
.login-tabs .mode-tab{flex:1}
.login-card h2{font-size:17px;margin-bottom:12px}
.login-switch{display:block;margin:12px auto 0}
.login-error{color:var(--danger);font-size:13px;margin-top:8px;text-align:center}
</style>
