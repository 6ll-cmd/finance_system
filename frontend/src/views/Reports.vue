<script setup>
import { apiFetch } from '../api.js'
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'

const router = useRouter()
const token = () => localStorage.getItem('fin_token') || ''
const hdr = () => ({ Authorization: 'Bearer ' + token() })
const report = ref({
  monthly: [],
  categories: [],
  statusCounts: { pending: 0, reimbursed: 0, rejected: 0 },
  voucherSummary: {
    total: 0,
    totalAmount: 0,
    postedAmount: 0,
    statusCounts: { draft: 0, posted: 0, cancelled: 0 },
    statusAmounts: { draft: 0, posted: 0, cancelled: 0 }
  }
})
const loading = ref(false)
const error = ref('')
const notice = ref('三大报表基于已过账凭证自动计算，属于简化会计口径，正式申报或审计请人工复核。')

const finReports = [
  { key: 'balance', name: '资产负债表', desc: '反映某一日期的资产、负债和所有者权益', icon: 'BS', path: '/reports/balance-sheet' },
  { key: 'income', name: '利润表', desc: '反映一段期间内的经营成果', icon: 'IS', path: '/reports/income-statement' },
  { key: 'cash', name: '现金流量表', desc: '简化估算现金净增加和活动分类', icon: 'CF', path: '/reports/cash-flow' }
]

const money = value => (Number(value) || 0).toLocaleString('zh-CN', {
  minimumFractionDigits: 2,
  maximumFractionDigits: 2
})

function htmlEscape(value) {
  return String(value ?? '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
}

function downloadExcel(sheets, filename) {
  const html = '\ufeff<html><head><meta charset="UTF-8"></head><body>' + sheets.map(sheet => {
    const rows = sheet.rows.map((row, index) => {
      const cell = index === 0 ? 'th' : 'td'
      return `<tr>${row.map(value => `<${cell}>${htmlEscape(value)}</${cell}>`).join('')}</tr>`
    }).join('')
    return `<h3>${htmlEscape(sheet.name)}</h3><table border="1">${rows}</table>`
  }).join('<br>') + '</body></html>'
  const blob = new Blob([html], { type: 'application/vnd.ms-excel;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  document.body.appendChild(link)
  link.click()
  link.remove()
  URL.revokeObjectURL(url)
}

function exportExcel() {
  const voucher = report.value.voucherSummary
  downloadExcel([
    {
      name: '凭证统计',
      rows: [
        ['项目', '数量', '金额'],
        ['凭证总数', voucher.total || 0, money(voucher.totalAmount)],
        ['草稿', voucher.statusCounts.draft || 0, money(voucher.statusAmounts.draft)],
        ['已过账', voucher.statusCounts.posted || 0, money(voucher.postedAmount)],
        ['已作废', voucher.statusCounts.cancelled || 0, money(voucher.statusAmounts.cancelled)]
      ]
    },
    {
      name: '发票月度趋势',
      rows: [
        ['月份', '数量', '不含税', '税额', '含税'],
        ...report.value.monthly.map(row => [row.month, row.count, money(row.amount), money(row.taxAmount), money(row.totalAmount)])
      ]
    },
    {
      name: '发票类别分布',
      rows: [
        ['类别', '数量', '金额'],
        ...report.value.categories.map(row => [row.name, row.count, money(row.totalAmount)])
      ]
    },
    {
      name: '发票状态分布',
      rows: [
        ['状态', '数量'],
        ['待报销', report.value.statusCounts.pending || 0],
        ['已报销', report.value.statusCounts.reimbursed || 0],
        ['已退回', report.value.statusCounts.rejected || 0]
      ]
    }
  ], `报表中心-${new Date().toISOString().slice(0, 10)}.xls`)
}

const pick = (row, ...keys) => {
  for (const key of keys) {
    if (row && row[key] !== undefined && row[key] !== null) return row[key]
  }
  return 0
}

function normalizeReport(data) {
  const monthly = (data.monthly || []).map(row => ({
    ...row,
    amount: pick(row, 'amount'),
    taxAmount: pick(row, 'taxAmount', 'taxamount', 'tax_amount'),
    totalAmount: pick(row, 'totalAmount', 'totalamount', 'total_amount')
  }))
  const categories = (data.categories || []).map(row => ({
    ...row,
    totalAmount: pick(row, 'totalAmount', 'totalamount', 'total_amount')
  }))
  return { ...data, monthly, categories }
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    const [reportsRes, metaRes] = await Promise.all([
      apiFetch('/api/reports', { headers: hdr() }),
      apiFetch('/api/financial-reports/meta', { headers: hdr() })
    ])
    const raw = await reportsRes.json().catch(() => ({}))
    if (!reportsRes.ok) throw new Error(raw.error || '报表数据加载失败')
    const meta = await metaRes.json().catch(() => ({}))
    if (meta.notice) notice.value = meta.notice
    const data = normalizeReport(raw)
    report.value = {
      ...report.value,
      ...data,
      voucherSummary: {
        ...report.value.voucherSummary,
        ...(data.voucherSummary || {}),
        statusCounts: {
          ...report.value.voucherSummary.statusCounts,
          ...(data.voucherSummary?.statusCounts || {})
        },
        statusAmounts: {
          ...report.value.voucherSummary.statusAmounts,
          ...(data.voucherSummary?.statusAmounts || {})
        }
      }
    }
  } catch (e) {
    error.value = e.message || '报表数据加载失败'
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="top-bar">
    <div>
      <h1 style="margin-bottom:4px">报表中心</h1>
      <div class="hint">发票统计按发票列表全量数据；三大财务报表按已过账凭证取数。</div>
    </div>
    <div class="flex">
      <button class="btn btn-o" @click="exportExcel">导出 Excel</button>
      <button class="btn btn-o" @click="load" :disabled="loading">刷新</button>
    </div>
  </div>

  <div class="notice-band">{{ notice }}</div>
  <div v-if="error" class="card" style="color:var(--danger)">{{ error }}</div>

  <h2 style="margin-bottom:10px">财务三大报表</h2>
  <div class="stats-grid" style="margin-bottom:24px">
    <div v-for="fr in finReports" :key="fr.key" class="report-tile card" @click="router.push(fr.path)">
      <div class="report-icon">{{ fr.icon }}</div>
      <div>
        <div class="report-name">{{ fr.name }}</div>
        <div class="report-desc">{{ fr.desc }}</div>
      </div>
    </div>
  </div>

  <h2 style="margin-bottom:10px">凭证统计</h2>
  <div class="stats-grid">
    <div class="stat-tile">
      <div class="num">{{ report.voucherSummary.total || 0 }}</div>
      <div class="lbl">凭证总数 · ¥{{ money(report.voucherSummary.totalAmount) }}</div>
    </div>
    <div class="stat-tile">
      <div class="num">{{ report.voucherSummary.statusCounts.draft || 0 }}</div>
      <div class="lbl">草稿 · ¥{{ money(report.voucherSummary.statusAmounts.draft) }}</div>
    </div>
    <div class="stat-tile">
      <div class="num" style="color:var(--success)">{{ report.voucherSummary.statusCounts.posted || 0 }}</div>
      <div class="lbl">已过账 · ¥{{ money(report.voucherSummary.postedAmount) }}</div>
    </div>
    <div class="stat-tile">
      <div class="num" style="color:var(--danger)">{{ report.voucherSummary.statusCounts.cancelled || 0 }}</div>
      <div class="lbl">已作废 · ¥{{ money(report.voucherSummary.statusAmounts.cancelled) }}</div>
    </div>
  </div>

  <h2 style="margin:24px 0 10px">发票统计</h2>
  <div class="card">
    <h3>月度趋势</h3>
    <table>
      <thead>
        <tr><th>月份</th><th class="num">数量</th><th class="num">不含税</th><th class="num">税额</th><th class="num">含税</th></tr>
      </thead>
      <tbody>
        <tr v-for="m in report.monthly" :key="m.month">
          <td>{{ m.month }}</td>
          <td class="num">{{ m.count }}</td>
          <td class="num">¥{{ money(m.amount) }}</td>
          <td class="num">¥{{ money(m.taxAmount) }}</td>
          <td class="num">¥{{ money(m.totalAmount) }}</td>
        </tr>
        <tr v-if="!report.monthly.length"><td colspan="5" class="empty">暂无数据</td></tr>
      </tbody>
    </table>
  </div>

  <div class="card">
    <h3>类别分布</h3>
    <table>
      <thead><tr><th>类别</th><th class="num">数量</th><th class="num">金额</th></tr></thead>
      <tbody>
        <tr v-for="c in report.categories" :key="c.category">
          <td>{{ c.name }}</td>
          <td class="num">{{ c.count }}</td>
          <td class="num">¥{{ money(c.totalAmount) }}</td>
        </tr>
      </tbody>
    </table>
  </div>

  <div class="card">
    <h3>发票状态分布</h3>
    <div class="stats-grid">
      <div class="stat-tile"><div class="num">{{ report.statusCounts.pending }}</div><div class="lbl">待报销</div></div>
      <div class="stat-tile"><div class="num" style="color:var(--success)">{{ report.statusCounts.reimbursed }}</div><div class="lbl">已报销</div></div>
      <div class="stat-tile"><div class="num" style="color:var(--danger)">{{ report.statusCounts.rejected }}</div><div class="lbl">已退回</div></div>
    </div>
  </div>
</template>

<style scoped>
.notice-band{border:1px solid var(--border);background:var(--bg);border-radius:var(--radius-md);padding:10px 12px;color:var(--muted);font-size:13px;margin-bottom:16px}
.report-tile{display:flex;align-items:center;gap:14px;cursor:pointer;transition:all .15s;padding:18px 22px}
.report-tile:hover{border-color:var(--accent);transform:translateY(-2px);box-shadow:0 4px 12px rgba(91,95,199,.15)}
.report-icon{width:44px;height:44px;background:var(--accent);color:var(--on-accent);border-radius:var(--radius-md);display:grid;place-items:center;font-weight:700;font-size:14px;flex-shrink:0}
.report-name{font-size:16px;font-weight:590}
.report-desc{font-size:12px;color:var(--muted);margin-top:2px}
</style>
