<script setup>
import { apiFetch } from '../api.js'
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import BackButton from '../components/BackButton.vue'

const route = useRoute()
const router = useRouter()
const token = () => localStorage.getItem('fin_token') || ''
const hdr = () => ({ Authorization: 'Bearer ' + token(), 'Content-Type': 'application/json' })
const inv = ref({})
const error = ref('')
const loading = ref(false)

const fmt = v => (Number(v) || 0).toFixed(2)
const statusName = s => ({ pending: '待报销', reimbursed: '已报销', rejected: '已退回' }[s] || s || '-')
const show = v => (v === null || v === undefined || v === '' ? '-' : v)

async function load() {
  loading.value = true
  error.value = ''
  try {
    const res = await apiFetch(`/api/invoices/${route.params.id}`, { headers: hdr() })
    const data = await res.json().catch(() => ({}))
    if (!res.ok) throw new Error(data.error || '发票详情加载失败')
    inv.value = data
  } catch (e) {
    error.value = e.message || '发票详情加载失败'
  } finally {
    loading.value = false
  }
}

async function change(status) {
  error.value = ''
  const res = await apiFetch(`/api/invoices/${route.params.id}/status`, {
    method: 'PATCH',
    headers: hdr(),
    body: JSON.stringify({ status })
  })
  const data = await res.json().catch(() => ({}))
  if (!res.ok) {
    error.value = data.error || '状态变更失败'
    return
  }
  inv.value.status = status
}

async function downloadAttachment() {
  error.value = ''
  const res = await apiFetch(`/api/invoices/${route.params.id}/attachment`, { headers: hdr() })
  if (!res.ok) {
    const data = await res.json().catch(() => ({}))
    error.value = data.error || '附件下载失败'
    return
  }
  const blob = await res.blob()
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = inv.value.attachmentName || 'invoice-attachment'
  document.body.appendChild(link)
  link.click()
  link.remove()
  URL.revokeObjectURL(url)
}

onMounted(load)
</script>

<template>
  <div class="top-bar">
    <div class="flex" style="align-items:center">
      <BackButton to="/invoices" />
      <h1 style="margin:0">{{ inv.invoiceNumber || inv.id || '发票详情' }}</h1>
    </div>
  </div>

  <div v-if="error" class="message bad">{{ error }}</div>
  <div v-if="loading" class="card empty">加载中...</div>

  <div v-else class="detail-page">
    <section class="summary">
      <div>
        <div class="label">开票日期</div>
        <div class="value">{{ show(inv.date) }}</div>
      </div>
      <div>
        <div class="label">发票号码</div>
        <div class="value mono">{{ show(inv.invoiceNumber) }}</div>
      </div>
      <div>
        <div class="label">类别</div>
        <div class="value">{{ show(inv.categoryName || inv.category) }}</div>
      </div>
      <div>
        <div class="label">状态</div>
        <div class="value"><span :class="'pill pill-' + inv.status">{{ statusName(inv.status) }}</span></div>
      </div>
      <div>
        <div class="label">不含税</div>
        <div class="value num">¥{{ fmt(inv.amount) }}</div>
      </div>
      <div>
        <div class="label">税额</div>
        <div class="value num">¥{{ fmt(inv.taxAmount) }}</div>
      </div>
      <div>
        <div class="label">价税合计</div>
        <div class="value num total">¥{{ fmt(inv.totalAmount) }}</div>
      </div>
    </section>

    <section class="card compact">
      <h2>双方信息</h2>
      <div class="party-grid">
        <div>
          <div class="label">购买方</div>
          <div class="value">{{ show(inv.buyer) }}</div>
          <div class="sub mono">{{ show(inv.buyerTaxNo) }}</div>
        </div>
        <div>
          <div class="label">销售方</div>
          <div class="value">{{ show(inv.seller) }}</div>
          <div class="sub mono">{{ show(inv.sellerTaxNo) }}</div>
        </div>
      </div>
    </section>

    <section class="card compact">
      <div class="section-head">
        <h2>发票明细</h2>
        <button v-if="inv.hasAttachment" class="btn btn-s btn-o" @click="downloadAttachment">下载原附件</button>
      </div>
      <table>
        <thead>
          <tr>
            <th>项目名称</th>
            <th>单位</th>
            <th>数量</th>
            <th>单价</th>
            <th>不含税</th>
            <th>税率</th>
            <th>税额</th>
          </tr>
        </thead>
        <tbody>
          <tr>
            <td>{{ show(inv.itemName) }}</td>
            <td>{{ show(inv.itemUnit) }}</td>
            <td class="num">{{ show(inv.itemQuantity) }}</td>
            <td class="num">¥{{ fmt(inv.itemUnitPrice) }}</td>
            <td class="num">¥{{ fmt(inv.amount) }}</td>
            <td>{{ inv.taxRate || 0 }}%</td>
            <td class="num">¥{{ fmt(inv.taxAmount) }}</td>
          </tr>
        </tbody>
      </table>
    </section>

    <section v-if="inv.totalAmountCn || inv.notes" class="card compact">
      <h2>补充信息</h2>
      <div class="detail-grid slim">
        <template v-if="inv.totalAmountCn">
          <div class="k">大写金额</div><div class="v">{{ inv.totalAmountCn }}</div>
        </template>
        <template v-if="inv.notes">
          <div class="k">备注</div><div class="v">{{ inv.notes }}</div>
        </template>
      </div>
    </section>

    <div class="actions">
      <button class="btn" @click="change('reimbursed')">标记已报销</button>
      <button class="btn btn-o" @click="change('rejected')">退回</button>
      <button class="btn btn-o" @click="router.push('/invoices')">返回列表</button>
    </div>
  </div>
</template>

<style scoped>
.detail-page{display:grid;gap:14px}
.summary{
  display:grid;
  grid-template-columns:repeat(7,minmax(0,1fr));
  gap:12px;
  background:#fff;
  border:1px solid var(--border);
  border-radius:8px;
  padding:18px 22px;
  box-shadow:var(--shadow);
}
.compact{padding:18px 22px}
.compact h2{margin:0 0 14px;font-size:18px}
.label,.k{color:var(--muted);font-size:13px}
.value,.v{font-size:15px;line-height:1.7}
.sub{color:var(--muted);font-size:13px;margin-top:3px}
.total{font-weight:700;color:#111827}
.party-grid{display:grid;grid-template-columns:1fr 1fr;gap:24px}
.section-head{display:flex;align-items:center;justify-content:space-between;gap:12px;margin-bottom:12px}
.section-head h2{margin:0}
.slim{grid-template-columns:120px 1fr}
.actions{display:flex;gap:10px;flex-wrap:wrap}
.message{margin-bottom:12px;font-size:13px}
.message.bad{color:var(--danger)}

@media (max-width: 1100px){
  .summary{grid-template-columns:repeat(3,minmax(0,1fr))}
}
@media (max-width: 720px){
  .summary,.party-grid{grid-template-columns:1fr}
  .compact{padding:16px}
  .section-head{align-items:flex-start;flex-direction:column}
}
</style>
