<script setup>
import { apiFetch } from '../api.js'
import { ref, onMounted, watch } from 'vue'
import BackButton from '../components/BackButton.vue'

const token = () => localStorage.getItem('fin_token') || ''
const hdr = () => ({ Authorization: 'Bearer ' + token() })
const today = new Date().toISOString().split('T')[0]
const startDate = ref(today.substring(0, 4) + '-01-01')
const endDate = ref(today)
const data = ref({ period: '', notice: '', rows: [] })
const loading = ref(false)
const error = ref('')

async function load() {
  loading.value = true
  error.value = ''
  try {
    const r = await apiFetch('/api/financial-reports/income-statement?startDate=' + startDate.value + '&endDate=' + endDate.value, { headers: hdr() })
    const body = await r.json().catch(() => ({}))
    if (!r.ok) throw new Error(body.error || '利润表加载失败')
    data.value = body
  } catch (e) {
    error.value = e.message || '利润表加载失败'
  } finally {
    loading.value = false
  }
}
onMounted(load)
watch([startDate, endDate], load)

const fmt = v => (Number(v) || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })

function htmlEscape(value) {
  return String(value ?? '').replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;')
}

function exportExcel() {
  const rows = [
    ['利润表', data.value.period || `${startDate.value}~${endDate.value}`, '', ''],
    ['项目', '行次', '本年累计金额', '公式'],
    ...data.value.rows.map(row => [row.item, row.row, fmt(row.amount), row.formula || ''])
  ]
  const htmlRows = rows.map((row, index) => {
    const cell = index <= 1 ? 'th' : 'td'
    return `<tr>${row.map(value => `<${cell}>${htmlEscape(value)}</${cell}>`).join('')}</tr>`
  }).join('')
  const blob = new Blob([`\ufeff<html><head><meta charset="UTF-8"></head><body><table border="1">${htmlRows}</table></body></html>`], { type: 'application/vnd.ms-excel;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = `利润表-${startDate.value}-${endDate.value}.xls`
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
      <h1 style="margin:0">利润表</h1>
    </div>
  </div>

  <div class="card">
    <div class="flex report-toolbar">
      <label class="hint">期间：</label>
      <input v-model="startDate" type="date" style="max-width:160px;margin-bottom:0">
      <span>至</span>
      <input v-model="endDate" type="date" style="max-width:160px;margin-bottom:0">
      <span class="hint">单位：元</span>
      <button class="btn btn-o" @click="exportExcel" :disabled="loading">导出 Excel</button>
    </div>
    <div v-if="data.notice" class="notice-band">{{ data.notice }}</div>
    <div v-if="error" class="message bad">{{ error }}</div>

    <table>
      <thead><tr><th>项目</th><th class="num">行次</th><th class="num">本年累计金额</th><th>公式</th></tr></thead>
      <tbody>
        <tr v-for="row in data.rows" :key="row.row" :class="{'total-row':row.isTotal,'section-row':row.isSection}">
          <td>{{ row.item }}</td>
          <td class="num muted">{{ row.row }}</td>
          <td class="num">{{ fmt(row.amount) }}</td>
          <td class="formula">{{ row.formula }}</td>
        </tr>
        <tr v-if="!loading && !data.rows.length"><td colspan="4" class="empty">暂无数据</td></tr>
      </tbody>
    </table>
  </div>
</template>

<style scoped>
.report-toolbar{margin-bottom:12px;align-items:center;gap:8px}
.notice-band{border:1px solid var(--border);background:var(--bg);border-radius:var(--radius-md);padding:10px 12px;color:var(--muted);font-size:13px;margin-bottom:14px}
.message{margin-bottom:12px;font-size:13px}.message.bad{color:var(--danger)}
.total-row td{font-weight:600;border-top:2px solid var(--border)}
.section-row td{color:var(--muted);font-style:italic}
.muted{color:var(--muted);font-size:12px}
.formula{font-size:12px;color:var(--muted);max-width:280px}
</style>
