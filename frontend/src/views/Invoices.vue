<script setup>
import { apiFetch } from '../api.js'
import { computed, ref, onMounted } from 'vue'
import OpPasswordDialog from '../components/OpPasswordDialog.vue'

const token = () => localStorage.getItem('fin_token') || ''
const hdr = () => ({ Authorization: 'Bearer ' + token(), 'Content-Type': 'application/json' })
const invoices = ref([])
const search = ref('')
const filter = ref('all')
const selected = ref([])
const loading = ref(false)
const error = ref('')
const actionMessage = ref('')

const fmt = value => (Number(value) || 0).toLocaleString('zh-CN', {
  minimumFractionDigits: 2,
  maximumFractionDigits: 2
})
const statusName = status => ({ pending: '待报销', reimbursed: '已报销', rejected: '已退回' }[status] || status)
const allSelected = computed(() => invoices.value.length > 0 && selected.value.length === invoices.value.length)

async function load() {
  loading.value = true
  error.value = ''
  try {
    const params = new URLSearchParams()
    params.set('search', search.value || '')
    params.set('status', filter.value)
    params.set('limit', '200')
    const r = await apiFetch('/api/invoices?' + params.toString(), { headers: hdr() })
    const data = await r.json().catch(() => ({}))
    if (!r.ok) throw new Error(data.error || '发票列表加载失败')
    invoices.value = data.data || []
    selected.value = selected.value.filter(id => invoices.value.some(inv => inv.id === id))
  } catch (e) {
    error.value = e.message || '发票列表加载失败'
  } finally {
    loading.value = false
  }
}

function toggleAll(checked) {
  selected.value = checked ? invoices.value.map(i => i.id) : []
}

async function doBatch(status, operationPassword = null) {
  if (!selected.value.length) {
    actionMessage.value = '请先勾选要处理的发票'
    return
  }
  error.value = ''
  actionMessage.value = ''
  const r = await apiFetch('/api/invoices/batch-status', {
    method: 'PATCH',
    headers: hdr(),
    body: JSON.stringify({ ids: selected.value, status, operationPassword })
  })
  const data = await r.json().catch(() => ({}))
  if (!r.ok) {
    error.value = data.error || '批量操作失败'
    return
  }
  actionMessage.value = `已处理 ${data.updated ?? selected.value.length} 张发票`
  selected.value = []
  await load()
}

function batch(status) {
  if (status === 'rejected') {
    requireOpPassword(password => doBatch(status, password), '批量退回发票需要操作密码')
    return
  }
  doBatch(status)
}

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
  error.value = ''
  const r = await apiFetch('/api/invoices/' + id + '/status', {
    method: 'PATCH',
    headers: hdr(),
    body: JSON.stringify({ status, operationPassword })
  })
  const data = await r.json().catch(() => ({}))
  if (!r.ok) {
    error.value = data.error || '状态变更失败'
    return
  }
  await load()
}

async function doDelete(id, operationPassword) {
  error.value = ''
  const r = await apiFetch('/api/invoices/' + id, {
    method: 'DELETE',
    headers: hdr(),
    body: JSON.stringify({ operationPassword })
  })
  const data = await r.json().catch(() => ({}))
  if (!r.ok) {
    error.value = data.error || '删除失败'
    return
  }
  await load()
}

function change(id, status) {
  if (status === 'rejected') {
    requireOpPassword(op => doChange(id, status, op), '退回发票需要操作密码')
  } else {
    doChange(id, status, null)
  }
}

function del(id) {
  requireOpPassword(op => doDelete(id, op), '删除发票需要操作密码')
}

async function exportCSV() {
  const r = await apiFetch('/api/invoices/export', { headers: hdr() })
  if (!r.ok) {
    error.value = '导出失败'
    return
  }
  const blob = await r.blob()
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = 'invoices.csv'
  document.body.appendChild(link)
  link.click()
  link.remove()
  URL.revokeObjectURL(url)
}

onMounted(load)
</script>

<template>
  <h1>发票管理</h1>
  <div class="card">
    <div class="flex">
      <input class="grow" v-model="search" @input="load" placeholder="搜索发票号、销售方、购买方...">
      <select v-model="filter" @change="load">
        <option value="all">全部</option>
        <option value="pending">待报销</option>
        <option value="reimbursed">已报销</option>
        <option value="rejected">已退回</option>
      </select>
    </div>
    <div class="flex mt">
      <button class="btn btn-s" @click="batch('reimbursed')" :disabled="!selected.length">批量报销</button>
      <button class="btn btn-s btn-o" @click="batch('rejected')" :disabled="!selected.length">批量退回</button>
      <button class="btn btn-s btn-o" @click="exportCSV">导出 CSV</button>
    </div>
    <div v-if="actionMessage" class="message ok">{{ actionMessage }}</div>
    <div v-if="error" class="message bad">{{ error }}</div>
  </div>

  <div class="card">
    <table>
      <thead>
        <tr>
          <th style="width:30px">
            <input type="checkbox" :checked="allSelected" @change="toggleAll($event.target.checked)" style="width:auto;margin:0">
          </th>
          <th>发票号</th>
          <th>日期</th>
          <th>销售方</th>
          <th>购买方</th>
          <th>金额</th>
          <th>状态</th>
          <th>操作</th>
        </tr>
      </thead>
      <tbody>
        <tr v-if="loading">
          <td colspan="8" class="empty">加载中...</td>
        </tr>
        <tr v-else v-for="invoice in invoices" :key="invoice.id">
          <td><input type="checkbox" :value="invoice.id" v-model="selected" style="width:auto;margin:0"></td>
          <td class="mono" style="color:var(--accent);cursor:pointer" @click="$router.push('/invoices/' + invoice.id)">{{ invoice.id }}</td>
          <td>{{ invoice.date }}</td>
          <td>{{ invoice.seller }}</td>
          <td>{{ invoice.buyer || '-' }}</td>
          <td class="num">¥{{ fmt(invoice.totalAmount || invoice.total_amount) }}</td>
          <td><span :class="'pill pill-' + invoice.status">{{ statusName(invoice.status) }}</span></td>
          <td>
            <button class="btn btn-s" @click="change(invoice.id, 'reimbursed')">报销</button>
            <button class="btn btn-s btn-o" @click="change(invoice.id, 'rejected')">退回</button>
            <button class="btn btn-s btn-o" @click="del(invoice.id)">删除</button>
          </td>
        </tr>
        <tr v-if="!loading && !invoices.length">
          <td colspan="8" class="empty">暂无发票</td>
        </tr>
      </tbody>
    </table>
  </div>
  <OpPasswordDialog v-model="pwOpen" :title="pwTitle" @confirm="onPwConfirm" />
</template>

<style scoped>
.message {
  margin-top: 10px;
  font-size: 13px;
}
.message.ok {
  color: var(--success);
}
.message.bad {
  color: var(--danger);
}
</style>
