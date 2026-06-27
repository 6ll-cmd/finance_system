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

const finReports = [
  { key: 'balance', name: '资产负债表', desc: '取已过账凭证，反映某一日期财务状况', icon: 'BS', path: '/reports/balance-sheet' },
  { key: 'income', name: '利润表', desc: '取已过账凭证，反映一定期间经营成果', icon: 'IS', path: '/reports/income-statement' },
  { key: 'cash', name: '现金流量表', desc: '取已过账凭证，反映一定期间现金流转', icon: 'CF', path: '/reports/cash-flow' }
]

const money = value => (Number(value) || 0).toLocaleString('zh-CN', {
  minimumFractionDigits: 2,
  maximumFractionDigits: 2
})

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
    const r = await apiFetch('/api/reports', { headers: hdr() })
    const raw = await r.json().catch(() => ({}))
    if (!r.ok) throw new Error(raw.error || '报表数据加载失败')
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
    <button class="btn btn-o" @click="load" :disabled="loading">刷新</button>
  </div>

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
      <thead>
        <tr><th>类别</th><th class="num">数量</th><th class="num">金额</th></tr>
      </thead>
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
.report-tile{display:flex;align-items:center;gap:14px;cursor:pointer;transition:all .15s;padding:18px 22px}
.report-tile:hover{border-color:var(--accent);transform:translateY(-2px);box-shadow:0 4px 12px rgba(91,95,199,.15)}
.report-icon{width:44px;height:44px;background:var(--accent);color:var(--on-accent);border-radius:var(--radius-md);display:grid;place-items:center;font-weight:700;font-size:14px;flex-shrink:0}
.report-name{font-size:16px;font-weight:590}
.report-desc{font-size:12px;color:var(--muted);margin-top:2px}
</style>
