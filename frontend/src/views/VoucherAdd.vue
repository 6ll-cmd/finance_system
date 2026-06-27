<script setup>
import { apiFetch } from '../api.js'
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import BackButton from '../components/BackButton.vue'

const router = useRouter()
const token = () => localStorage.getItem('fin_token') || ''
const hdr = () => ({ Authorization: 'Bearer ' + token(), 'Content-Type': 'application/json' })

const accts = ref([])
const error = ref('')
const saving = ref(false)
const f = ref({
  date: new Date().toISOString().split('T')[0],
  desc: '',
  debits: [{ account: '5001001', amount: 0, summary: '', tags: [] }],
  credits: [{ account: '1002001', amount: 0, summary: '', tags: [] }]
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

let tagSeq = 0
function addTag(entry) {
  entry.tags.push({ id: ++tagSeq, text: '' })
}
function delTag(entry, index) {
  entry.tags.splice(index, 1)
}
function summaryWithTags(entry) {
  const tags = entry.tags.map(t => t.text).filter(Boolean).join('、')
  return tags ? (entry.summary ? entry.summary + ' | ' + tags : tags) : (entry.summary || '')
}

async function submit() {
  error.value = ''
  if (!f.value.date || !f.value.desc) {
    error.value = '请填写凭证日期和摘要'
    return
  }
  if (!balanced.value) {
    error.value = '借贷金额必须相等且大于 0'
    return
  }

  const entries = []
  f.value.debits.forEach(entry => {
    if (+entry.amount > 0) entries.push({ account: entry.account, debitAmount: +entry.amount, creditAmount: 0, summary: summaryWithTags(entry) })
  })
  f.value.credits.forEach(entry => {
    if (+entry.amount > 0) entries.push({ account: entry.account, debitAmount: 0, creditAmount: +entry.amount, summary: summaryWithTags(entry) })
  })
  if (!entries.length) return

  saving.value = true
  try {
    const res = await apiFetch('/api/vouchers', {
      method: 'POST',
      headers: hdr(),
      body: JSON.stringify({ voucherDate: f.value.date, description: f.value.desc, entries })
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
  const res = await apiFetch('/api/accounts', { headers: hdr() })
  accts.value = await res.json()
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
      <input class="grow" v-model="f.desc" placeholder="凭证摘要 *">
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
      <div v-if="entry.tags.length" class="tag-list">
        <span v-for="(tag, tagIndex) in entry.tags" :key="tag.id" class="tag-item">
          <input v-model="tag.text" class="tag-input" placeholder="标识内容">
          <button class="tag-del" @click="delTag(entry, tagIndex)" title="删除标识">×</button>
        </span>
      </div>
      <button class="btn btn-s btn-o tag-add-btn" @click="addTag(entry)">+ 添加标识</button>
    </div>
    <button class="btn btn-s btn-o mt" @click="f.debits.push({ account: '5001001', amount: 0, summary: '', tags: [] })">+ 借方</button>

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
      <div v-if="entry.tags.length" class="tag-list">
        <span v-for="(tag, tagIndex) in entry.tags" :key="tag.id" class="tag-item">
          <input v-model="tag.text" class="tag-input" placeholder="标识内容">
          <button class="tag-del" @click="delTag(entry, tagIndex)" title="删除标识">×</button>
        </span>
      </div>
      <button class="btn btn-s btn-o tag-add-btn" @click="addTag(entry)">+ 添加标识</button>
    </div>
    <button class="btn btn-s btn-o mt" @click="f.credits.push({ account: '1002001', amount: 0, summary: '', tags: [] })">+ 贷方</button>

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
.tag-list{display:flex;flex-wrap:wrap;gap:8px;padding:4px 0 0;margin-left:2px}
.tag-item{display:inline-flex;align-items:center;background:var(--accent-light);border:1px solid var(--border);border-radius:99px;padding:0 4px 0 12px;gap:6px}
.tag-input{border:none;background:transparent;padding:6px 2px;margin:0;font-size:13px;width:120px;outline:none}
.tag-input:focus{box-shadow:none}
.tag-del{background:none;border:none;color:var(--muted);cursor:pointer;padding:2px 6px;font-size:13px;line-height:1;border-radius:50%}
.tag-del:hover{color:var(--danger);background:var(--danger-bg)}
.tag-add-btn{margin-top:6px;font-size:12px}
.balance-row{display:flex;gap:18px;align-items:center;font-size:13px;color:var(--muted);padding:8px 0;flex-wrap:wrap}
.balance-row.ok{color:var(--success)}
.balance-row.bad{color:var(--danger)}
</style>
