<script setup>
import { apiFetch } from '../api.js'
import { computed, ref, onMounted } from 'vue'
import OpPasswordDialog from '../components/OpPasswordDialog.vue'

const token = () => localStorage.getItem('fin_token') || ''
const hdr = () => ({ Authorization: 'Bearer ' + token(), 'Content-Type': 'application/json' })
const vouchers = ref([])
const loading = ref(false)
const error = ref('')
const statusFilter = ref('all')

const currentUser = computed(() => {
  const user = JSON.parse(localStorage.getItem('fin_user') || 'null')
  return user?.username || 'Win'
})

const ACCOUNT_NAMES = {
  '1002001': '银行存款',
  '1002002': '库存现金',
  '1002003': '应收账款',
  '1002004': '预付账款',
  '1002005': '其他应收款',
  '2002001': '应付账款',
  '2002002': '预收账款',
  '2002003': '应付职工薪酬',
  '2002004': '应交税费',
  '2002005': '其他应付款',
  '3001001': '实收资本',
  '3001002': '未分配利润',
  '3001005': '本年利润',
  '4001001': '主营业务收入',
  '4001002': '其他业务收入',
  '4001003': '营业外收入',
  '5001001': '办公费',
  '5001002': '差旅费',
  '5001003': '招待费',
  '5001004': '水电费',
  '5001005': '物业费',
  '5001006': '租赁费',
  '5001007': '技术服务费',
  '5001008': '物流费',
  '5001009': '折旧费',
  '5001010': '职工薪酬',
  '5001011': '主营业务成本',
  '5001012': '其他业务成本'
}

const fmt = value => (Number(value) || 0).toLocaleString('zh-CN', {
  minimumFractionDigits: 2,
  maximumFractionDigits: 2
})
const statusName = status => ({ draft: '草稿', posted: '已过账', cancelled: '已作废' }[status] || status)
const accountName = entry => ACCOUNT_NAMES[entry.account] || entry.accountName || '-'

const flatRows = computed(() => vouchers.value.flatMap(voucher => {
  const entries = voucher.entries?.length ? voucher.entries : [{ account: '', accountName: '', debitAmount: 0, creditAmount: 0, summary: '' }]
  return entries.map((entry, index) => ({
    key: `${voucher.id}-${index}`,
    voucher,
    entry,
    first: index === 0,
    span: entries.length
  }))
}))

const exportHeaders = ['日期', '凭证号', '摘要', '科目编码', '科目名称', '借方金额', '贷方金额', '制单人', '审核人', '附件数', '备注']
const exportRows = computed(() => flatRows.value.map(row => [
  row.voucher.voucherDate || '',
  row.voucher.id || '',
  row.entry.summary || row.voucher.description || '',
  row.entry.account || '',
  accountName(row.entry),
  Number(row.entry.debitAmount) ? fmt(row.entry.debitAmount) : '',
  Number(row.entry.creditAmount) ? fmt(row.entry.creditAmount) : '',
  row.first ? currentUser.value : '',
  '',
  row.first ? '0' : '',
  row.first ? (row.voucher.notes || '') : ''
]))

function csvEscape(value) {
  const text = String(value ?? '')
  return /[",\r\n]/.test(text) ? `"${text.replace(/"/g, '""')}"` : text
}

function htmlEscape(value) {
  return String(value ?? '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
}

function downloadBlob(content, filename, type) {
  const blob = new Blob([content], { type })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  document.body.appendChild(link)
  link.click()
  link.remove()
  URL.revokeObjectURL(url)
}

function exportCsv() {
  const rows = [exportHeaders, ...exportRows.value]
  const csv = '\ufeff' + rows.map(row => row.map(csvEscape).join(',')).join('\r\n')
  downloadBlob(csv, `凭证列表-${new Date().toISOString().slice(0, 10)}.csv`, 'text/csv;charset=utf-8')
}

function exportExcel() {
  const rows = [exportHeaders, ...exportRows.value]
  const htmlRows = rows.map((row, index) => {
    const cell = index === 0 ? 'th' : 'td'
    return `<tr>${row.map(value => `<${cell}>${htmlEscape(value)}</${cell}>`).join('')}</tr>`
  }).join('')
  const html = `\ufeff<html><head><meta charset="UTF-8"></head><body><table border="1">${htmlRows}</table></body></html>`
  downloadBlob(html, `凭证列表-${new Date().toISOString().slice(0, 10)}.xls`, 'application/vnd.ms-excel;charset=utf-8')
}

async function reload() {
  loading.value = true
  error.value = ''
  try {
    const params = new URLSearchParams()
    params.set('page', '1')
    params.set('size', '200')
    if (statusFilter.value !== 'all') params.set('status', statusFilter.value)
    const res = await apiFetch('/api/vouchers?' + params.toString(), { headers: hdr() })
    const data = await res.json().catch(() => ({}))
    if (!res.ok) throw new Error(data.error || '凭证列表加载失败')
    vouchers.value = data.data || []
  } catch (e) {
    error.value = e.message || '凭证列表加载失败'
  } finally {
    loading.value = false
  }
}
onMounted(reload)

const pwOpen = ref(false)
const pwTitle = ref('')
const pendingAction = ref(null)

function requireOpPassword(fn, title) {
  pendingAction.value = fn
  pwTitle.value = title || '请输入操作密码'
  pwOpen.value = true
}
async function onPwConfirm(operationPassword) {
  const fn = pendingAction.value
  pwOpen.value = false
  pendingAction.value = null
  if (fn) await fn(operationPassword)
}
async function doChange(id, status, operationPassword) {
  const res = await apiFetch('/api/vouchers/' + id + '/status', {
    method: 'PATCH',
    headers: hdr(),
    body: JSON.stringify({ status, operationPassword })
  })
  const data = await res.json().catch(() => ({}))
  if (!res.ok) {
    error.value = data.error || '凭证状态变更失败'
    return
  }
  await reload()
}
async function doDelete(id, operationPassword) {
  const res = await apiFetch('/api/vouchers/' + id, {
    method: 'DELETE',
    headers: hdr(),
    body: JSON.stringify({ operationPassword })
  })
  const data = await res.json().catch(() => ({}))
  if (!res.ok) {
    error.value = data.error || '凭证删除失败'
    return
  }
  await reload()
}
function change(id, status) {
  requireOpPassword(password => doChange(id, status, password), '过账/状态变更需要操作密码')
}
function del(id) {
  requireOpPassword(password => doDelete(id, password), '删除凭证需要操作密码')
}
</script>

<template>
  <div class="top-bar">
    <div>
      <h1 style="margin-bottom:4px">凭证列表</h1>
      <div class="hint">按分录展开，方便核对科目、借贷金额和摘要。</div>
    </div>
    <div class="flex toolbar-actions">
      <select v-model="statusFilter" @change="reload" style="width:120px;margin-bottom:0">
        <option value="all">全部</option>
        <option value="draft">草稿</option>
        <option value="posted">已过账</option>
        <option value="cancelled">已作废</option>
      </select>
      <button class="btn btn-o" @click="exportCsv" :disabled="!flatRows.length">导出 CSV</button>
      <button class="btn btn-o" @click="exportExcel" :disabled="!flatRows.length">导出 Excel</button>
      <button class="btn btn-o" @click="$router.push('/vouchers/add')">新增凭证</button>
    </div>
  </div>

  <div class="ledger-shell">
    <table class="ledger-table">
      <thead>
        <tr>
          <th>日期</th>
          <th>凭证号</th>
          <th>摘要</th>
          <th>科目编码</th>
          <th>科目名称</th>
          <th class="num-col">借方金额</th>
          <th class="num-col">贷方金额</th>
          <th>制单人</th>
          <th>审核人</th>
          <th>附件数</th>
          <th>备注</th>
          <th>操作</th>
        </tr>
      </thead>
      <tbody>
        <tr v-if="loading">
          <td colspan="12" class="empty">加载中...</td>
        </tr>
        <tr v-else-if="error">
          <td colspan="12" class="empty" style="color:var(--danger)">{{ error }}</td>
        </tr>
        <tr v-else-if="!flatRows.length">
          <td colspan="12" class="empty">暂无凭证</td>
        </tr>
        <tr v-for="row in flatRows" v-else :key="row.key">
          <td v-if="row.first" :rowspan="row.span" class="date-cell">{{ row.voucher.voucherDate }}</td>
          <td v-if="row.first" :rowspan="row.span" class="mono voucher-cell" @click="$router.push('/vouchers/' + row.voucher.id)">
            {{ row.voucher.id }}
            <span :class="'pill pill-' + row.voucher.status">{{ statusName(row.voucher.status) }}</span>
          </td>
          <td class="summary-cell">{{ row.entry.summary || row.voucher.description }}</td>
          <td class="mono">{{ row.entry.account || '-' }}</td>
          <td>{{ accountName(row.entry) }}</td>
          <td class="num num-col">{{ Number(row.entry.debitAmount) ? fmt(row.entry.debitAmount) : '' }}</td>
          <td class="num num-col">{{ Number(row.entry.creditAmount) ? fmt(row.entry.creditAmount) : '' }}</td>
          <td v-if="row.first" :rowspan="row.span">{{ currentUser }}</td>
          <td v-if="row.first" :rowspan="row.span"></td>
          <td v-if="row.first" :rowspan="row.span" class="num">0</td>
          <td v-if="row.first" :rowspan="row.span">{{ row.voucher.notes || '' }}</td>
          <td v-if="row.first" :rowspan="row.span" class="actions-cell">
            <button class="btn btn-s" @click="change(row.voucher.id, 'posted')" :disabled="row.voucher.status === 'posted'">过账</button>
            <button class="btn btn-s btn-o" @click="$router.push('/vouchers/' + row.voucher.id)">详情</button>
            <button class="btn btn-s btn-o" @click="del(row.voucher.id)">删除</button>
          </td>
        </tr>
      </tbody>
    </table>
  </div>
  <OpPasswordDialog v-model="pwOpen" :title="pwTitle" @confirm="onPwConfirm" />
</template>

<style scoped>
.toolbar-actions {
  flex-wrap: wrap;
  justify-content: flex-end;
}
.ledger-shell {
  overflow: auto;
  border: 1px solid #8b8b8b;
  background: var(--surface);
}
.ledger-table {
  min-width: 1120px;
  border-collapse: collapse;
  font-size: 13px;
}
.ledger-table th,
.ledger-table td {
  border: 1px solid #8b8b8b;
  padding: 7px 8px;
  vertical-align: middle;
  white-space: nowrap;
}
.ledger-table th {
  background: #f1f1f1;
  color: #111;
  text-align: center;
  font-size: 13px;
  font-weight: 600;
  letter-spacing: 0;
  text-transform: none;
}
.ledger-table tr:hover td {
  background: #f8fbff;
}
.voucher-cell {
  color: var(--accent);
  cursor: pointer;
}
.voucher-cell .pill {
  display: block;
  margin-top: 4px;
  width: fit-content;
}
.summary-cell {
  min-width: 180px;
  max-width: 280px;
  white-space: normal;
  line-height: 1.35;
}
.date-cell {
  font-variant-numeric: tabular-nums;
}
.num-col {
  text-align: right;
  min-width: 104px;
}
.actions-cell {
  min-width: 150px;
}
.actions-cell .btn {
  margin: 2px;
}
</style>
