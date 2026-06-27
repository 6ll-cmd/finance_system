<script setup>
import { apiFetch } from '../api.js'
import { ref, onMounted, watch } from 'vue'
import BackButton from '../components/BackButton.vue'

const token = () => localStorage.getItem('fin_token') || ''
const hdr = () => ({ Authorization: 'Bearer ' + token() })
const endDate = ref(new Date().toISOString().split('T')[0])
const data = ref({ period: '', assets: [], liabilities: [] })
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    const r = await apiFetch('/api/financial-reports/balance-sheet?endDate=' + endDate.value, { headers: hdr() })
    data.value = await r.json()
  } catch (e) {
    console.error(e)
  }
  loading.value = false
}
onMounted(load)
watch(endDate, load)

const fmt = v => {
  const n = Number(v) || 0
  return n.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}
</script>

<template>
  <div class="top-bar">
    <div class="flex" style="align-items:center">
      <BackButton to="/reports" />
      <h1 style="margin:0">资产负债表</h1>
    </div>
  </div>

  <div class="card">
    <div class="flex" style="margin-bottom:12px;align-items:center">
      <label class="hint">报表日期：</label>
      <input v-model="endDate" type="date" style="max-width:180px;margin-bottom:0">
      <span class="hint" v-if="data.period">单位：元</span>
    </div>

    <div class="bs-grid">
      <div class="bs-side">
        <h2>资产</h2>
        <table>
          <thead><tr><th>项目</th><th class="num">行次</th><th class="num">期末余额</th><th class="num">年初余额</th></tr></thead>
          <tbody>
            <tr v-for="row in data.assets" :key="row.row" :class="{'total-row':row.isTotal,'section-row':row.isSection}">
              <td>{{ row.item }}</td>
              <td class="num muted">{{ row.row }}</td>
              <td class="num">{{ fmt(row.endBalance) }}</td>
              <td class="num">{{ fmt(row.beginBalance) }}</td>
            </tr>
          </tbody>
        </table>
      </div>
      <div class="bs-side">
        <h2>负债和所有者权益</h2>
        <table>
          <thead><tr><th>项目</th><th class="num">行次</th><th class="num">期末余额</th><th class="num">年初余额</th></tr></thead>
          <tbody>
            <tr v-for="row in data.liabilities" :key="row.row" :class="{'total-row':row.isTotal,'section-row':row.isSection}">
              <td>{{ row.item }}</td>
              <td class="num muted">{{ row.row }}</td>
              <td class="num">{{ fmt(row.endBalance) }}</td>
              <td class="num">{{ fmt(row.beginBalance) }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  </div>
</template>

<style scoped>
.bs-grid{display:grid;grid-template-columns:1fr 1fr;gap:24px}
.bs-side h2{margin-bottom:8px}
.total-row td{font-weight:600;border-top:2px solid var(--border)}
.section-row td{color:var(--muted);font-style:italic}
.muted{color:var(--muted);font-size:12px}
@media(max-width:768px){.bs-grid{grid-template-columns:1fr}}
</style>
