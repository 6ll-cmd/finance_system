<script setup>
import { apiFetch } from '../api.js'
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import BackButton from '../components/BackButton.vue'

const router = useRouter()
const token = () => localStorage.getItem('fin_token') || ''
const hdr = () => ({ Authorization: 'Bearer ' + token(), 'Content-Type': 'application/json' })

const accts = ref([])
const responsiblePeople = ref([])
const newResponsiblePerson = ref('')
const error = ref('')
const saving = ref(false)
const f = ref({
  date: new Date().toISOString().split('T')[0],
  responsiblePerson: '',
  notes: '',
  debits: [{ account: '5001001', amount: 0, summary: '' }],
  credits: [{ account: '1002001', amount: 0, summary: '' }]
})

const fmt = v => (Number(v) || 0).toFixed(2)
const groupedAccounts = computed(() => {
  const groups = { asset: [], liability: [], equity: [], income: [], expense: [] }
  const labels = { asset: '资产类', liability: '负债类', equity: '权益类', income: '收入类', expense: '费用类' }
  accts.value.filter(a => a.level === 1).forEach(a => {
    if (groups[a.accountType]) groups[a.accountType].push(a)
  })
  return Object.entries(groups)
    .filter(([, items]) => items.length)
    .map(([type, items]) => ({ type, label: labels[type], items }))
})

const totalDebit = computed(() => f.value.debits.reduce((sum, entry) => sum + (+entry.amount || 0), 0))
const totalCredit = computed(() => f.value.credits.reduce((sum, entry) => sum + (+entry.amount || 0), 0))
const balanced = computed(() => Math.abs(totalDebit.value - totalCredit.value) < 0.005 && totalDebit.value > 0)

async function loadResponsiblePeople() {
  const res = await apiFetch('/api/voucher-responsible-people', { headers: hdr() })
  const data = await res.json().catch(() => ({}))
  responsiblePeople.value = data.data || []
}

async function addResponsiblePerson() {
  const name = newResponsiblePerson.value.trim()
  if (!name) return
  error.value = ''
  const res = await apiFetch('/api/voucher-responsible-people', {
    method: 'POST',
    headers: hdr(),
    body: JSON.stringify({ name })
  })
  const data = await res.json().catch(() => ({}))
  if (!res.ok) {
    error.value = data.error || '负责人添加失败'
    return
  }
  const savedName = data.name || name
  if (!responsiblePeople.value.includes(savedName)) responsiblePeople.value.push(savedName)
  responsiblePeople.value.sort((a, b) => a.localeCompare(b, 'zh-CN'))
  f.value.responsiblePerson = savedName
  newResponsiblePerson.value = ''
}

async function submit() {
  error.value = ''
  if (!f.value.date) {
    error.value = '请填写凭证日期'
    return
  }
  if (!balanced.value) {
    error.value = '借贷金额必须相等且大于 0'
    return
  }

  const entries = []
  f.value.debits.forEach(entry => {
    if (+entry.amount > 0) entries.push({ account: entry.account, debitAmount: +entry.amount, creditAmount: 0, summary: entry.summary || '' })
  })
  f.value.credits.forEach(entry => {
    if (+entry.amount > 0) entries.push({ account: entry.account, debitAmount: 0, creditAmount: +entry.amount, summary: entry.summary || '' })
  })
  if (!entries.length) return
  const description = f.value.notes || entries.find(entry => entry.summary)?.summary || '凭证'

  saving.value = true
  try {
    const res = await apiFetch('/api/vouchers', {
      method: 'POST',
      headers: hdr(),
      body: JSON.stringify({
        voucherDate: f.value.date,
        description,
        responsiblePerson: f.value.responsiblePerson,
        notes: f.value.notes,
        entries
      })
    })
    const data = await res.json().catch(() => ({}))
    if (!res.ok) throw new Error(data.error || '凭证保存失败')
    router.push('/vouchers')
  } catch (e) {
    error.value = e.message || '凭证保存失败'
  } finally {
    saving.value = false
  }
}

onMounted(async () => {
  const [accountsRes] = await Promise.all([
    apiFetch('/api/accounts', { headers: hdr() }),
    loadResponsiblePeople()
  ])
  accts.value = await accountsRes.json()
})
</script>

<template>
  <div class="top-bar">
    <div class="flex" style="align-items:center">
      <BackButton to="/vouchers" />
      <h1 style="margin:0">录入凭证</h1>
    </div>
  </div>

  <div class="card" style="max-width:760px">
    <div class="flex">
      <input class="grow" v-model="f.date" type="date">
    </div>
    <div class="flex mt">
      <select class="grow" v-model="f.responsiblePerson">
        <option value="">负责人（可选）</option>
        <option v-for="person in responsiblePeople" :key="person" :value="person">{{ person }}</option>
      </select>
      <input class="grow" v-model="newResponsiblePerson" maxlength="64" placeholder="新增同事名称">
      <button class="btn btn-o" @click="addResponsiblePerson" :disabled="!newResponsiblePerson.trim()">添加同事</button>
    </div>

    <h3>借方</h3>
    <div v-for="(entry, index) in f.debits" :key="'d' + index" class="entry-block">
      <div class="flex mt">
        <select class="grow" v-model="entry.account">
          <optgroup v-for="group in groupedAccounts" :key="group.type" :label="group.label">
            <option v-for="account in group.items" :key="account.id" :value="account.id">{{ account.id }} {{ account.name }}</option>
          </optgroup>
        </select>
        <input v-model.number="entry.amount" type="number" step="0.01" placeholder="金额" style="max-width:140px">
        <input class="grow" v-model="entry.summary" placeholder="摘要">
        <button class="btn btn-s btn-o" @click="f.debits.splice(index, 1)" title="删除此行" v-if="f.debits.length > 1">删除</button>
      </div>
    </div>
    <button class="btn btn-s btn-o mt" @click="f.debits.push({ account: '5001001', amount: 0, summary: '' })">+ 借方</button>

    <h3 class="mt">贷方</h3>
    <div v-for="(entry, index) in f.credits" :key="'c' + index" class="entry-block">
      <div class="flex mt">
        <select class="grow" v-model="entry.account">
          <optgroup v-for="group in groupedAccounts" :key="group.type" :label="group.label">
            <option v-for="account in group.items" :key="account.id" :value="account.id">{{ account.id }} {{ account.name }}</option>
          </optgroup>
        </select>
        <input v-model.number="entry.amount" type="number" step="0.01" placeholder="金额" style="max-width:140px">
        <input class="grow" v-model="entry.summary" placeholder="摘要">
        <button class="btn btn-s btn-o" @click="f.credits.splice(index, 1)" title="删除此行" v-if="f.credits.length > 1">删除</button>
      </div>
    </div>
    <button class="btn btn-s btn-o mt" @click="f.credits.push({ account: '1002001', amount: 0, summary: '' })">+ 贷方</button>

    <div class="mt">
      <textarea class="grow" v-model="f.notes" rows="3" placeholder="备注"></textarea>
    </div>

    <div class="balance-row mt" :class="{ ok: balanced, bad: !balanced && (totalDebit > 0 || totalCredit > 0) }">
      <span>借方合计：¥{{ fmt(totalDebit) }}</span>
      <span>贷方合计：¥{{ fmt(totalCredit) }}</span>
      <span v-if="balanced">已平衡</span>
      <span v-else-if="totalDebit > 0 || totalCredit > 0">差额：¥{{ fmt(Math.abs(totalDebit - totalCredit)) }}</span>
    </div>

    <button class="btn mt" @click="submit" :disabled="!balanced || saving" style="margin-top:16px">
      {{ saving ? '保存中...' : '提交凭证' }}
    </button>
    <div v-if="error" style="margin-top:12px;color:var(--danger);font-size:14px;">{{ error }}</div>
  </div>
</template>

<style scoped>
.entry-block{padding:6px 0;border-bottom:1px dashed var(--border-light)}
.entry-block:last-child{border-bottom:none}
.balance-row{display:flex;gap:18px;align-items:center;font-size:13px;color:var(--muted);padding:8px 0;flex-wrap:wrap}
.balance-row.ok{color:var(--success)}
.balance-row.bad{color:var(--danger)}
</style>
