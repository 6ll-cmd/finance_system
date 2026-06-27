<script setup>
import { useRouter } from 'vue-router'
import { ref, onMounted } from 'vue'
import AiAssistant from './components/AiAssistant.vue'
import { logout } from './api.js'

const router = useRouter()
const user = JSON.parse(localStorage.getItem('fin_user') || 'null')
const aiOpen = ref(false)

const navSections = [
  {
    title: '主菜单',
    items: [
      { to: '/', label: '首页概览', icon: '<path d="M3 9l9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"/><polyline points="9 22 9 12 15 12 15 22"/>' }
    ]
  },
  {
    title: '发票管理',
    items: [
      { to: '/invoices', label: '发票列表', icon: '<path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/><line x1="16" y1="13" x2="8" y2="13"/><line x1="16" y1="17" x2="8" y2="17"/>' },
      { to: '/invoices/add', label: '发票录入', icon: '<circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="16"/><line x1="8" y1="12" x2="16" y2="12"/>' }
    ]
  },
  {
    title: '凭证管理',
    items: [
      { to: '/vouchers', label: '凭证列表', icon: '<rect x="3" y="3" width="18" height="18" rx="3"/><line x1="8" y1="3" x2="8" y2="21"/><line x1="8" y1="8" x2="20" y2="8"/><line x1="8" y1="15" x2="14" y2="15"/>' },
      { to: '/vouchers/add', label: '新增凭证', icon: '<rect x="3" y="3" width="18" height="18" rx="3"/><line x1="12" y1="8" x2="12" y2="16"/><line x1="8" y1="12" x2="16" y2="12"/>' },
      { to: '/accounts', label: '科目表', icon: '<path d="M12 20h9"/><path d="M16.5 3.5a2.121 2.121 0 0 1 3 3L7 19l-4 1 1-4L16.5 3.5z"/>' }
    ]
  },
  {
    title: '分析',
    items: [
      { to: '/reports', label: '统计报表', icon: '<line x1="18" y1="20" x2="18" y2="10"/><line x1="12" y1="20" x2="12" y2="4"/><line x1="6" y1="20" x2="6" y2="14"/>' },
      { to: '/settings', label: '系统设置', icon: '<circle cx="12" cy="12" r="3"/><path d="M12 1v2M12 21v2M4.22 4.22l1.42 1.42M18.36 18.36l1.42 1.42M1 12h2M21 12h2M4.22 19.78l1.42-1.42M18.36 5.64l1.42-1.42"/>' }
    ]
  }
]

// logout provided by api.js

function toggleTheme(){
  const current = document.documentElement.getAttribute('data-theme') || 'light'
  const next = current === 'dark' ? 'light' : 'dark'
  document.documentElement.setAttribute('data-theme', next)
  localStorage.setItem('theme', next)
}

onMounted(() => {
  document.documentElement.setAttribute('data-theme', localStorage.getItem('theme') || 'light')
})
</script>

<template>
  <div id="app-layout" class="app-layout">
    <nav class="sidebar" v-if="$route.path!=='/login'">
      <div class="sidebar-logo">
        <div class="logo-icon">
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round">
            <rect x="3" y="3" width="18" height="18" rx="3"/><line x1="8" y1="3" x2="8" y2="21"/><line x1="8" y1="8" x2="20" y2="8"/><line x1="8" y1="12" x2="20" y2="12"/><line x1="8" y1="16" x2="16" y2="16"/>
          </svg>
        </div>
        <div><h2>发票管家</h2></div>
      </div>

      <div class="sidebar-nav">
        <div v-for="section in navSections" :key="section.title" class="nav-section">
          <div class="nav-section-title">{{ section.title }}</div>
          <router-link v-for="item in section.items" :key="item.to" :to="item.to" class="nav-item">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" v-html="item.icon"></svg>
            <span>{{ item.label }}</span>
          </router-link>
        </div>
      </div>

      <div class="sidebar-footer">
        <span class="current-user">{{ user?.username }}</span>
        <button class="ai-trigger-btn" @click="aiOpen=true">AI 助手</button>
        <div class="footer-actions">
          <button class="icon-text-btn" @click="toggleTheme">主题</button>
          <button class="icon-text-btn danger" @click="logout">退出</button>
        </div>
      </div>
    </nav>
    <div :class="$route.path==='/login'?'':'main'" :style="$route.path==='/login'?{flex:1}:{}">
      <router-view />
    </div>
    <AiAssistant v-if="$route.path!=='/login'" v-model:open="aiOpen" />
  </div>
</template>
