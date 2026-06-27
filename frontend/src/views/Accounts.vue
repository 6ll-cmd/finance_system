<script setup>
import { apiFetch } from '../api.js'
import { ref, onMounted } from 'vue'
const token=()=>localStorage.getItem('fin_token')||''
const hdr=()=>({Authorization:'Bearer '+token()})
const accts=ref([]);const tn=s=>({asset:'资产',liability:'负债',equity:'权益',income:'收入',expense:'费用'}[s]||s)
onMounted(async()=>{const r=await apiFetch('/api/accounts',{headers:hdr()});accts.value=await r.json()})
</script>

<template>
  <h1>科目表（{{ accts.length }}个科目）</h1>
  <div class="card"><table><thead><tr><th>编号</th><th>名称</th><th>类型</th></tr></thead>
  <tbody><tr v-for="a in accts" :key="a.id"><td class="mono">{{ a.id }}</td><td>{{ '　'.repeat(a.level||0) }}{{ a.name }}</td><td>{{ tn(a.accountType) }}</td></tr></tbody></table></div>
</template>
