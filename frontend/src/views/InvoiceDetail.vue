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
const fmt = v => (Number(v) || 0).toFixed(2)
const statusName = s => ({ pending: '待报销', reimbursed: '已报销', rejected: '已退回' }[s] || s)

onMounted(async () => {
  const res = await apiFetch(`/api/invoices/${route.params.id}`, { headers: hdr() })
  inv.value = await res.json()
})

async function change(status) {
  const res = await apiFetch(`/api/invoices/${route.params.id}/status`, {
    method: 'PATCH',
    headers: hdr(),
    body: JSON.stringify({ status })
  })
  if (res.ok) inv.value.status = status
}
</script>

<template>
  <div class="top-bar">
    <div class="flex" style="align-items:center">
      <BackButton to="/invoices" />
      <h1 style="margin:0">{{ inv.id }}</h1>
    </div>
  </div>

  <div class="card">
    <h2>基本信息</h2>
    <div class="detail-grid">
      <div class="k">开票日期</div><div class="v">{{ inv.date }}</div>
      <div class="k">发票类型</div><div class="v">{{ inv.type }}</div>
      <div class="k">发票代码</div><div class="v mono">{{ inv.invoiceCode || '-' }}</div>
      <div class="k">发票号码</div><div class="v mono">{{ inv.invoiceNumber || '-' }}</div>
      <div class="k">类别</div><div class="v">{{ inv.categoryName || inv.category }}</div>
      <div class="k">状态</div><div class="v"><span :class="'pill pill-' + inv.status">{{ statusName(inv.status) }}</span></div>
    </div>

    <h2 class="mt">购买方信息</h2>
    <div class="detail-grid">
      <div class="k">名称</div><div class="v">{{ inv.buyer || '-' }}</div>
      <div class="k">纳税人识别号</div><div class="v">{{ inv.buyerTaxNo || '-' }}</div>
      <div class="k">地址、电话</div><div class="v">{{ inv.buyerAddressPhone || '-' }}</div>
      <div class="k">开户行及账号</div><div class="v">{{ inv.buyerBankAccount || '-' }}</div>
    </div>

    <h2 class="mt">销售方信息</h2>
    <div class="detail-grid">
      <div class="k">名称</div><div class="v">{{ inv.seller || '-' }}</div>
      <div class="k">纳税人识别号</div><div class="v">{{ inv.sellerTaxNo || '-' }}</div>
      <div class="k">地址、电话</div><div class="v">{{ inv.sellerAddressPhone || '-' }}</div>
      <div class="k">开户行及账号</div><div class="v">{{ inv.sellerBankAccount || '-' }}</div>
    </div>

    <h2 class="mt">货物或应税劳务、服务</h2>
    <table>
      <thead>
        <tr><th>项目名称</th><th>规格型号</th><th>单位</th><th>数量</th><th>单价</th><th>金额</th><th>税率</th><th>税额</th></tr>
      </thead>
      <tbody>
        <tr>
          <td>{{ inv.itemName || '-' }}</td>
          <td>{{ inv.itemSpec || '-' }}</td>
          <td>{{ inv.itemUnit || '-' }}</td>
          <td class="num">{{ inv.itemQuantity || '-' }}</td>
          <td class="num">¥{{ fmt(inv.itemUnitPrice) }}</td>
          <td class="num">¥{{ fmt(inv.amount) }}</td>
          <td>{{ inv.taxRate }}%</td>
          <td class="num">¥{{ fmt(inv.taxAmount) }}</td>
        </tr>
      </tbody>
    </table>

    <h2 class="mt">价税合计</h2>
    <div class="detail-grid">
      <div class="k">大写</div><div class="v">{{ inv.totalAmountCn || '-' }}</div>
      <div class="k">小写</div><div class="v num" style="font-weight:590">¥{{ fmt(inv.totalAmount) }}</div>
      <div class="k">备注</div><div class="v">{{ inv.notes || '-' }}</div>
    </div>

    <div class="flex mt">
      <button class="btn" @click="change('reimbursed')">标记已报销</button>
      <button class="btn btn-o" @click="change('rejected')">退回</button>
      <button class="btn btn-o" @click="router.push('/invoices')">返回列表</button>
    </div>
  </div>
</template>
