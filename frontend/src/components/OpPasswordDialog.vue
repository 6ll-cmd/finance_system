<script setup>
import { ref, watch } from 'vue'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  title: { type: String, default: '操作确认' },
  hint: { type: String, default: '请输入操作密码' }
})
const emit = defineEmits(['update:modelValue', 'confirm', 'cancel'])

const password = ref('')
const error = ref('')

watch(() => props.modelValue, open => {
  if (open) {
    password.value = ''
    error.value = ''
  }
})

function submit() {
  if (!password.value) {
    error.value = '请输入操作密码'
    return
  }
  emit('confirm', password.value)
}

function close() {
  emit('update:modelValue', false)
  emit('cancel')
}
</script>

<template>
  <div v-if="modelValue" class="op-pw-overlay" @click.self="close">
    <div class="op-pw-card">
      <h3>{{ title }}</h3>
      <p class="op-pw-hint">{{ hint }}</p>
      <input
        type="password"
        v-model="password"
        placeholder="操作密码"
        autocomplete="off"
        autofocus
        @keyup.enter="submit"
      >
      <div v-if="error" class="op-pw-error">{{ error }}</div>
      <div class="op-pw-actions">
        <button class="btn btn-o" type="button" @click="close">取消</button>
        <button class="btn" type="button" @click="submit">确认</button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.op-pw-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.45);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
}
.op-pw-card {
  background: var(--surface, #fff);
  border: 1px solid var(--border, #ddd);
  border-radius: 8px;
  padding: 24px;
  width: 360px;
  max-width: 90vw;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.2);
}
.op-pw-card h3 {
  margin: 0 0 8px;
  font-size: 16px;
}
.op-pw-hint {
  margin: 0 0 16px;
  color: var(--muted, #888);
  font-size: 13px;
}
.op-pw-card input {
  width: 100%;
  box-sizing: border-box;
  padding: 8px 10px;
  margin-bottom: 8px;
  border: 1px solid var(--border, #ddd);
  border-radius: 4px;
  font-size: 14px;
}
.op-pw-error {
  color: var(--danger, #e53935);
  font-size: 12px;
  margin-bottom: 8px;
}
.op-pw-actions {
  display: flex;
  gap: 8px;
  justify-content: flex-end;
}
</style>
