<script setup>
import { useRouter } from 'vue-router'

const props = defineProps({
  to: { type: String, default: '' },
  label: { type: String, default: '返回上一级' }
})

const router = useRouter()

function goBack() {
  if (props.to) router.push(props.to)
  else router.back()
}
</script>

<template>
  <button class="back-icon-btn" type="button" :title="label" :aria-label="label" @click="goBack">
    ←
  </button>
</template>

<style scoped>
.back-icon-btn {
  width: 34px;
  height: 34px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border: 1px solid var(--border);
  border-radius: 8px;
  background: var(--surface);
  color: var(--fg);
  font-size: 20px;
  line-height: 1;
  cursor: pointer;
  flex: 0 0 auto;
}
.back-icon-btn:hover {
  border-color: var(--accent);
  color: var(--accent);
  background: var(--accent-light);
}
</style>
