<script setup>
import { apiFetch } from '../api.js'
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import BackButton from '../components/BackButton.vue'

const route = useRoute()
const router = useRouter()
const token = () => localStorage.getItem('fin_token') || ''
const hdr = () => ({ Authorization: 'Bearer ' + token() })
const v = ref({})
const entries = ref([])
const fmt = x => (Number(x) || 0).toFixed(2)

onMounted(async () => {
  const r = await apiFetch(`/api/vouchers/${route.params.id}`, { headers: hdr() })
  const d = await r.json()
  v.value = d
  entries.value = d.entries || []
})
</script>

<template>
  <div class="top-bar">
    <div class="flex" style="align-items:center">
      <BackButton to="/vouchers" />
      <h1 style="margin:0">{{ v.id }}</h1>
    </div>
  </div>

  <div class="card">
    <div class="detail-grid">
      <div class="k">日期</div><div class="v">{{ v.voucherDate }}</div>
      <div class="k">摘要</div><div class="v">{{ v.description }}</div>
      <div class="k">合计</div><div class="v num">¥{{ fmt(v.totalAmount) }}</div>
    </div>
  </div>

  <div class="card">
    <h2>分录明细</h2>
    <table>
      <thead>
        <tr><th>科目</th><th>借方</th><th>贷方</th><th>摘要</th></tr>
      </thead>
      <tbody>
        <tr v-for="e in entries" :key="e.id">
          <td>{{ e.account }}</td>
          <td class="num">{{ e.debitAmount > 0 ? '¥' + fmt(e.debitAmount) : '-' }}</td>
          <td class="num">{{ e.creditAmount > 0 ? '¥' + fmt(e.creditAmount) : '-' }}</td>
          <td>{{ e.summary }}</td>
        </tr>
      </tbody>
    </table>
  </div>

  <button class="btn btn-o" @click="router.push('/vouchers')">返回列表</button>
</template>
