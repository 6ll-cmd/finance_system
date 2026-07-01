<script setup>
import { apiFetch } from '../api.js'
import { ref, onMounted, watch } from 'vue'
import BackButton from '../components/BackButton.vue'

const token = () => localStorage.getItem('fin_token') || ''
const hdr = () => ({ Authorization: 'Bearer ' + token() })
const endDate = ref(new Date().toISOString().split('T')[0])
const data = ref({ period: '', notice: '', assets: [], liabilities: [] })
const loading = ref(false)
const error = ref('')

async function load() {
  loading.value = true
  error.value = ''
  try {
    const r = await apiFetch('/api/financial-reports/balance-sheet?endDate=' + endDate.value, { headers: hdr() })
    const body = await r.json().catch(() => ({}))
    if (!r.ok) throw new Error(body.error || '资产负债表加载失败')
    data.value = body
  } catch (e) {
    error.value = e.message || '资产负债表加载失败'
  } finally {
    loading.value = false
  }
}
onMounted(load)
watch(endDate, load)

const fmt = v => (Number(v) || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })

function htmlEscape(value) {
  return String(value ?? '').replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;')
}

function exportExcel() {
  const rows = [
    ['资产负债表', data.value.period || endDate.value, '', '', '', '', '', ''],
    ['资产项目', '行次', '期末余额', '年初余额', '负债和所有者权益项目', '行次', '期末余额', '年初余额']
  ]
  const max = Math.max(data.value.assets.length, data.value.liabilities.length)
  for (let i = 0; i < max; i++) {
    const asset = data.value.assets[i] || {}
    const liability = data.value.liabilities[i] || {}
    rows.push([
      asset.item || '', asset.row || '', fmt(asset.endBalance), fmt(asset.beginBalance),
      liability.item || '', liability.row || '', fmt(liability.endBalance), fmt(liability.beginBalance)
    ])
  }
  const htmlRows = rows.map((row, index) => {
    const cell = index <= 1 ? 'th' : 'td'
    return `<tr>${row.map(value => `<${cell}>${htmlEscape(value)}</${cell}>`).join('')}</tr>`
  }).join('')
  const blob = new Blob([`\ufeff<html><head><meta charset="UTF-8"></head><body><table border="1">${htmlRows}</table></body></html>`], { type: 'application/vnd.ms-excel;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = `资产负债表-${endDate.value}.xls`
  document.body.appendChild(link)
  link.click()
  link.remove()
  URL.revokeObjectURL(url)
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
    <div class="flex report-toolbar">
      <label class="hint">报表日期：</label>
      <input v-model="endDate" type="date" style="max-width:180px;margin-bottom:0">
      <span class="hint">单位：元</span>
      <button class="btn btn-o" @click="exportExcel" :disabled="loading">导出 Excel</button>
    </div>
    <div v-if="data.notice" class="notice-band">{{ data.notice }}</div>
    <div v-if="error" class="message bad">{{ error }}</div>

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
            <tr v-if="!loading && !data.assets.length"><td colspan="4" class="empty">暂无数据</td></tr>
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
            <tr v-if="!loading && !data.liabilities.length"><td colspan="4" class="empty">暂无数据</td></tr>
          </tbody>
        </table>
      </div>
    </div>
  </div>
</template>

<style scoped>
.report-toolbar{margin-bottom:12px;align-items:center}
.notice-band{border:1px solid var(--border);background:var(--bg);border-radius:var(--radius-md);padding:10px 12px;color:var(--muted);font-size:13px;margin-bottom:14px}
.message{margin-bottom:12px;font-size:13px}.message.bad{color:var(--danger)}
.bs-grid{display:grid;grid-template-columns:1fr 1fr;gap:24px}
.bs-side h2{margin-bottom:8px}
.total-row td{font-weight:600;border-top:2px solid var(--border)}
.section-row td{color:var(--muted);font-style:italic}
.muted{color:var(--muted);font-size:12px}
@media(max-width:768px){.bs-grid{grid-template-columns:1fr}}
</style>
