<script setup>
import { apiFetch } from '../api.js'
import { ref, nextTick } from 'vue'

const props = defineProps({ open: Boolean })
const emit = defineEmits(['update:open'])
const input = ref('')
const loading = ref(false)
const messages = ref([
  { role: 'assistant', content: '你好！我是发票管家 AI 助手。\n\n我可以帮你：\n• 录入发票\n• 查询统计\n• 分析建议\n• 解答财务问题' }
])
const conversation = ref([])

const token = () => localStorage.getItem('fin_token') || ''
const hdr = () => ({ Authorization: 'Bearer ' + token(), 'Content-Type': 'application/json' })

function close(){
  emit('update:open', false)
}

async function send(){
  const text = input.value.trim()
  if(!text || loading.value) return
  input.value = ''
  messages.value.push({ role: 'user', content: text })
  conversation.value.push({ role: 'user', content: text })
  loading.value = true
  await nextTick()
  try{
    const r = await apiFetch('/api/ai/chat', {
      method: 'POST',
      headers: hdr(),
      body: JSON.stringify({ messages: conversation.value })
    })
    const data = await r.json()
    const reply = data.error ? data.error : (data.reply || '收到，已处理。')
    messages.value.push({ role: 'assistant', content: reply })
    conversation.value.push({ role: 'assistant', content: reply })
  }catch(e){
    messages.value.push({ role: 'assistant', content: 'AI 服务连接失败，请确认 AI 配置。' })
  }finally{
    loading.value = false
  }
}
</script>

<template>
  <div class="ai-overlay" :class="{show: props.open}" @click="close"></div>
  <aside class="ai-panel" :class="{open: props.open}">
    <div class="ai-panel-header">
      <span>AI 助手</span>
      <button aria-label="关闭 AI 助手" @click="close">×</button>
    </div>
    <div class="ai-messages">
      <div v-for="(m,i) in messages" :key="i" class="ai-msg" :class="m.role">{{ m.content }}</div>
      <div v-if="loading" class="ai-typing">AI 思考中...</div>
    </div>
    <div class="ai-input-row">
      <input v-model="input" class="input" placeholder="输入消息..." @keydown.enter="send">
      <button class="btn btn-primary btn-sm" :disabled="loading" @click="send">发送</button>
    </div>
  </aside>
</template>
