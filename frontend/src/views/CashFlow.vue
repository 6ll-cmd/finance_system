<script setup>
import { apiFetch } from '../api.js'
import { ref, onMounted, watch } from 'vue'
import BackButton from '../components/BackButton.vue'

const token = () => localStorage.getItem('fin_token') || ''
const hdr = () => ({ Authorization: 'Bearer ' + token() })
const today = new Date().toISOString().split('T')[0]
const startDate = ref(today.substring(0, 4) + '-01-01')
const endDate = ref(today)
const data = ref({ period: '', rows: [] })
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    const r = await apiFetch('/api/financial-reports/cash-flow?startDate=' + startDate.value + '&endDate=' + endDate.value, { headers: hdr() })
    data.value = await r.json()
  } catch (e) {
    console.error(e)
  }
  loading.value = false
}
onMounted(load)
watch([startDate, endDate], load)

const fmt = v => {
  const n = Number(v) || 0
  return n.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}
</script>

<template>
  <div class="top-bar">
    <div class="flex" style="align-items:center">
      <BackButton to="/reports" />
      <h1 style="margin:0">现金流量表</h1>
    </div>
  </div>

  <div class="card">
    <div class="flex" style="margin-bottom:12px;align-items:center;gap:8px">
      <label class="hint">期间：</label>
      <input v-model="startDate" type="date" style="max-width:160px;margin-bottom:0">
      <span>至</span>
      <input v-model="endDate" type="date" style="max-width:160px;margin-bottom:0">
      <span class="hint">单位：元</span>
    </div>

    <table>
      <thead>
        <tr><th>项目</th><th class="num">行次</th><th class="num">金额</th><th>公式</th></tr>
      </thead>
      <tbody>
        <tr v-for="row in data.rows" :key="row.row" :class="{'total-row':row.isTotal,'section-row':row.isSection}">
          <td>{{ row.item }}</td>
          <td class="num muted">{{ row.row }}</td>
          <td class="num">{{ fmt(row.amount) }}</td>
          <td class="formula">{{ row.formula }}</td>
        </tr>
        <tr v-if="!data.rows.length"><td colspan="4" class="empty">暂无数据</td></tr>
      </tbody>
    </table>
  </div>
</template>

<style scoped>
.total-row td{font-weight:600;border-top:2px solid var(--border)}
.section-row td{color:var(--muted);font-style:italic}
.muted{color:var(--muted);font-size:12px}
.formula{font-size:12px;color:var(--muted);max-width:320px}
</style>
