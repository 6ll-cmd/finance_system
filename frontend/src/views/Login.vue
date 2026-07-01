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
const showPassword = ref(false)
const showConfirmPassword = ref(false)

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
  showPassword.value = false
  showConfirmPassword.value = false
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

      <div class="password-field">
        <input
          v-model="password"
          :type="showPassword ? 'text' : 'password'"
          placeholder="密码"
          :autocomplete="isRegister ? 'new-password' : 'current-password'"
          @keyup.enter="submit"
        >
        <button
          type="button"
          class="password-toggle"
          :title="showPassword ? '隐藏密码' : '显示密码'"
          :aria-label="showPassword ? '隐藏密码' : '显示密码'"
          @click="showPassword = !showPassword"
        >
          <svg v-if="!showPassword" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
            <path d="M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7S2 12 2 12z"/>
            <circle cx="12" cy="12" r="3"/>
          </svg>
          <svg v-else width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
            <path d="M17.94 17.94A10.94 10.94 0 0 1 12 19C5.5 19 2 12 2 12a20.7 20.7 0 0 1 5.06-5.94"/>
            <path d="M9.9 4.24A10.5 10.5 0 0 1 12 4.99c6.5 0 10 7.01 10 7.01a20.8 20.8 0 0 1-2.16 3.19"/>
            <path d="M14.12 14.12a3 3 0 0 1-4.24-4.24"/>
            <path d="M3 3l18 18"/>
          </svg>
        </button>
      </div>

      <div v-if="isRegister" class="password-field">
        <input
          v-model="confirmPassword"
          :type="showConfirmPassword ? 'text' : 'password'"
          placeholder="确认密码"
          autocomplete="new-password"
          @keyup.enter="submit"
        >
        <button
          type="button"
          class="password-toggle"
          :title="showConfirmPassword ? '隐藏密码' : '显示密码'"
          :aria-label="showConfirmPassword ? '隐藏密码' : '显示密码'"
          @click="showConfirmPassword = !showConfirmPassword"
        >
          <svg v-if="!showConfirmPassword" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
            <path d="M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7S2 12 2 12z"/>
            <circle cx="12" cy="12" r="3"/>
          </svg>
          <svg v-else width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
            <path d="M17.94 17.94A10.94 10.94 0 0 1 12 19C5.5 19 2 12 2 12a20.7 20.7 0 0 1 5.06-5.94"/>
            <path d="M9.9 4.24A10.5 10.5 0 0 1 12 4.99c6.5 0 10 7.01 10 7.01a20.8 20.8 0 0 1-2.16 3.19"/>
            <path d="M14.12 14.12a3 3 0 0 1-4.24-4.24"/>
            <path d="M3 3l18 18"/>
          </svg>
        </button>
      </div>

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
.password-field{position:relative}
.password-field input{padding-right:42px}
.password-toggle{position:absolute;right:8px;top:50%;transform:translateY(-50%);width:30px;height:30px;border:0;background:transparent;color:var(--muted);display:flex;align-items:center;justify-content:center;cursor:pointer}
.password-toggle:hover{color:var(--accent)}
</style>
