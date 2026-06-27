<script setup>
import { apiFetch } from '../api.js'
import { ref, onMounted } from 'vue'
const stats=ref({total:0,totalAmt:0,month:0,monthAmt:0,pending:0,pendingAmt:0,reimbursed:0})
const recent=ref([])
const token=()=>localStorage.getItem('fin_token')||''
const hdr=()=>({Authorization:'Bearer '+token(),'Content-Type':'application/json'})
const fmt=v=>(Number(v)||0).toFixed(2)
function scn(s){return{pending:'待报销',reimbursed:'已报销',rejected:'已退回'}[s]||s}

onMounted(async()=>{
  const[st,r]=await Promise.all([apiFetch('/api/stats',{headers:hdr()}),apiFetch('/api/invoices?limit=5',{headers:hdr()})])
  stats.value=await st.json()
  const rd=await r.json();recent.value=rd.data||[]
})
</script>

<template>
  <h1>数据看板</h1>
  <div class="stats-grid">
    <div class="stat-tile"><div class="num">{{ stats.totalInvoices||0 }}</div><div class="lbl">发票总数 · ¥{{ fmt(stats.totalAmount) }}</div></div>
    <div class="stat-tile"><div class="num">{{ stats.thisMonth||0 }}</div><div class="lbl">本月新增 · ¥{{ fmt(stats.thisMonthAmount) }}</div></div>
    <div class="stat-tile"><div class="num" style="color:var(--warn)">{{ stats.pendingReimburse||0 }}</div><div class="lbl">待报销 · ¥{{ fmt(stats.pendingAmount) }}</div></div>
    <div class="stat-tile"><div class="num" style="color:var(--success)">{{ stats.reimbursed||0 }}</div><div class="lbl">已报销</div></div>
  </div>
  <div class="card"><h2>最近录入</h2>
    <div v-if="recent.length" v-for="i in recent" :key="i.id" style="padding:10px 0;border-bottom:1px solid var(--border-light);cursor:pointer" @click="$router.push('/invoices/'+i.id)" class="flex">
      <span class="mono grow">{{ i.id }}</span><span class="grow">{{ i.seller }}</span><span class="num">¥{{ fmt(i.totalAmount||i.total_amount) }}</span>
      <span :class="'pill pill-'+(i.status)">{{ scn(i.status) }}</span>
    </div>
    <div v-else class="empty">暂无发票</div>
  </div>
</template>
