<script setup>
import { apiFetch } from '../api.js'
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import BackButton from '../components/BackButton.vue'
const router=useRouter()
const token=()=>localStorage.getItem('fin_token')||''
const hdr=()=>({Authorization:'Bearer '+token(),'Content-Type':'application/json'})
const mode=ref('manual')
const f=ref({
  seller:'',
  buyer:'广州共生纪元云科技有限公司',
  buyerTaxNo:'',
  buyerAddressPhone:'',
  buyerBankAccount:'',
  sellerTaxNo:'',
  sellerAddressPhone:'',
  sellerBankAccount:'',
  date:new Date().toISOString().split('T')[0],
  itemName:'',
  itemSpec:'',
  itemUnit:'',
  itemQuantity:1,
  itemUnitPrice:0,
  amount:0,
  taxRate:6,
  cat:'service',
  type:'增值税电子普通发票',
  code:'',
  num:'',
  notes:''
})
const ocr=ref({loading:false,fileName:'',message:'',ok:null})
const preview=ref('')
const error=ref('')
const saving=ref(false)
const voucher=ref({loading:false,message:'',ok:null,id:''})
let pdfJsLoading=null
const fmt=v=>(Number(v)||0).toFixed(2)
const taxAmount=computed(()=>Math.round((Number(f.value.amount)||0)*(Number(f.value.taxRate)||0))/100)
const totalAmount=computed(()=>Math.round(((Number(f.value.amount)||0)+taxAmount.value)*100)/100)
const totalAmountCn=computed(()=>moneyToChinese(totalAmount.value))

function moneyToChinese(value){
  const n=Number(value)||0
  if(!n)return '零元整'
  const fraction=['角','分']
  const digit=['零','壹','贰','叁','肆','伍','陆','柒','捌','玖']
  const unit=[['元','万','亿'],['','拾','佰','仟']]
  const head=n<0?'负':''
  let num=Math.abs(n)
  let s=''
  for(let i=0;i<fraction.length;i++){
    s+=(digit[Math.floor(num*10*Math.pow(10,i))%10]+fraction[i]).replace(/零./,'')
  }
  s=s||'整'
  num=Math.floor(num)
  for(let i=0;i<unit[0].length&&num>0;i++){
    let p=''
    for(let j=0;j<unit[1].length&&num>0;j++){
      p=digit[num%10]+unit[1][j]+p
      num=Math.floor(num/10)
    }
    s=p.replace(/(零.)*零$/,'').replace(/^$/,'零')+unit[0][i]+s
  }
  return head+s.replace(/(零.)*零元/,'元').replace(/(零.)+/g,'零').replace(/^整$/,'零元整')
}

function syncAmountFromLine(){
  const qty=Number(f.value.itemQuantity)||0
  const price=Number(f.value.itemUnitPrice)||0
  if(qty>0&&price>=0)f.value.amount=Math.round(qty*price*100)/100
}

async function submit(){
  error.value=''
  if(!f.value.seller||!f.value.amount||!f.value.date)return
  saving.value=true
  try{
    const r=await apiFetch('/api/invoices',{method:'POST',headers:hdr(),body:JSON.stringify({
      seller:f.value.seller,
      buyer:f.value.buyer,
      buyerTaxNo:f.value.buyerTaxNo,
      buyerAddressPhone:f.value.buyerAddressPhone,
      buyerBankAccount:f.value.buyerBankAccount,
      sellerTaxNo:f.value.sellerTaxNo,
      sellerAddressPhone:f.value.sellerAddressPhone,
      sellerBankAccount:f.value.sellerBankAccount,
      itemName:f.value.itemName,
      itemSpec:f.value.itemSpec,
      itemUnit:f.value.itemUnit,
      itemQuantity:Number(f.value.itemQuantity)||0,
      itemUnitPrice:Number(f.value.itemUnitPrice)||0,
      amount:Number(f.value.amount),
      taxRate:Number(f.value.taxRate),
      taxAmount:taxAmount.value,
      totalAmount:totalAmount.value,
      totalAmountCn:totalAmountCn.value,
      category:f.value.cat,
      type:f.value.type,
      invoiceCode:f.value.code,
      invoiceNumber:f.value.num,
      notes:f.value.notes,
      date:f.value.date
    })})
    const data=await r.json().catch(()=>({}))
    if(!r.ok)throw new Error(data.error||'录入失败，请重新登录后再试')
    router.push('/invoices')
  }catch(e){
    error.value=e.message||'录入失败，请稍后重试'
  }finally{
    saving.value=false
  }
}

function readAsDataUrl(file){
  return new Promise((resolve,reject)=>{
    const reader=new FileReader()
    reader.onload=()=>resolve(reader.result)
    reader.onerror=reject
    reader.readAsDataURL(file)
  })
}

async function loadPdfJs(){
  if(!pdfJsLoading){
    pdfJsLoading=Promise.all([
      import('pdfjs-dist/build/pdf'),
      import('pdfjs-dist/build/pdf.worker.min.js?url')
    ]).then(([pdfjs,worker])=>{
      pdfjs.GlobalWorkerOptions.workerSrc=worker.default
      return pdfjs
    }).catch(err=>{
      pdfJsLoading=null
      throw new Error(err.message||'PDF 预览组件加载失败，请重新构建前端后再试。')
    })
  }
  return pdfJsLoading
}

async function renderPdfFirstPage(file){
  ocr.value={loading:true,fileName:file.name,message:'正在解析 PDF 首页...',ok:null}
  const pdfjs=await loadPdfJs()
  const arrayBuffer=await file.arrayBuffer()
  const pdf=await pdfjs.getDocument({data:arrayBuffer}).promise
  if(!pdf.numPages)throw new Error('PDF 文件没有可识别页面')
  const page=await pdf.getPage(1)
  const viewport=page.getViewport({scale:2})
  const canvas=document.createElement('canvas')
  const ctx=canvas.getContext('2d')
  if(!ctx)throw new Error('浏览器无法创建 PDF 预览画布')
  canvas.width=Math.floor(viewport.width)
  canvas.height=Math.floor(viewport.height)
  await page.render({canvasContext:ctx,viewport}).promise
  return canvas.toDataURL('image/png')
}

async function fileToOcrImage(file){
  const isPdf=file.type==='application/pdf'||file.name.toLowerCase().endsWith('.pdf')
  if(isPdf)return renderPdfFirstPage(file)
  if(!file.type.startsWith('image/'))throw new Error('请选择图片或 PDF 文件')
  return readAsDataUrl(file)
}

async function extractPdfText(file){
  const pdfjs=await loadPdfJs()
  const arrayBuffer=await file.arrayBuffer()
  const pdf=await pdfjs.getDocument({data:arrayBuffer}).promise
  let textContent=''
  for(let pi=1;pi<=Math.min(pdf.numPages,2);pi++){
    const page=await pdf.getPage(pi)
    const tc=await page.getTextContent()
    const items=tc.items
      .map(it=>({str:(it.str||'').trim(),x:it.transform?it.transform[4]:0,y:it.transform?it.transform[5]:0}))
      .filter(it=>it.str)
      .sort((a,b)=>Math.abs(b.y-a.y)>3?b.y-a.y:a.x-b.x)
    const rows=[]
    for(const item of items){
      let row=rows.find(r=>Math.abs(r.y-item.y)<=3)
      if(!row){
        row={y:item.y,items:[]}
        rows.push(row)
      }
      row.items.push(item)
    }
    textContent+=rows
      .map(row=>row.items.sort((a,b)=>a.x-b.x).map(item=>item.str).join(' '))
      .join('\n')+'\n'
  }
  return textContent
}

function applyOcr(data){
  f.value.type=data.type||f.value.type
  f.value.num=data.number||data.invoiceNumber||data.invoice_number||f.value.num
  f.value.date=data.date||data.invoiceDate||data.invoice_date||f.value.date
  f.value.seller=data.seller||f.value.seller
  f.value.buyer=data.buyer||f.value.buyer
  f.value.buyerTaxNo=data.buyerTaxNo||data.buyer_tax_no||f.value.buyerTaxNo
  f.value.buyerAddressPhone=data.buyerAddressPhone||data.buyer_address_phone||f.value.buyerAddressPhone
  f.value.buyerBankAccount=data.buyerBankAccount||data.buyer_bank_account||f.value.buyerBankAccount
  f.value.sellerTaxNo=data.sellerTaxNo||data.seller_tax_no||f.value.sellerTaxNo
  f.value.sellerAddressPhone=data.sellerAddressPhone||data.seller_address_phone||f.value.sellerAddressPhone
  f.value.sellerBankAccount=data.sellerBankAccount||data.seller_bank_account||f.value.sellerBankAccount
  f.value.itemName=data.itemName||data.item_name||f.value.itemName
  f.value.itemSpec=data.itemSpec||data.item_spec||f.value.itemSpec
  f.value.itemUnit=data.itemUnit||data.item_unit||f.value.itemUnit
  f.value.itemQuantity=Number(data.itemQuantity||data.item_quantity)||f.value.itemQuantity
  f.value.itemUnitPrice=Number(data.itemUnitPrice||data.item_unit_price)||f.value.itemUnitPrice
  f.value.amount=Number(data.amount)||f.value.amount
  f.value.taxRate=Number(data.taxRate||data.tax_rate)||f.value.taxRate
  if(data.category)f.value.cat=data.category
  f.value.notes = f.value.notes || 'AI 识别录入'
}

async function createVoucherDraft(){
  error.value=''
  voucher.value={loading:true,message:'正在生成凭证草稿...',ok:null,id:''}
  try{
    const r=await apiFetch('/api/vouchers/from-invoice',{method:'POST',headers:hdr(),body:JSON.stringify({
      date:f.value.date,
      seller:f.value.seller,
      type:f.value.type,
      category:f.value.cat,
      amount:Number(f.value.amount),
      taxAmount:taxAmount.value,
      totalAmount:totalAmount.value,
      invoiceNumber:f.value.num,
      invoiceCode:f.value.code,
      notes:f.value.notes
    })})
    const data=await r.json().catch(()=>({}))
    if(!r.ok)throw new Error(data.error||'生成凭证失败')
    voucher.value={loading:false,message:`已生成凭证草稿 ${data.id}`,ok:true,id:data.id}
    router.push(`/vouchers/${data.id}`)
  }catch(e){
    voucher.value={loading:false,message:e.message||'生成凭证失败',ok:false,id:''}
  }
}

async function recognize(e){
  const file=e.target.files&&e.target.files[0]
  if(!file)return
  ocr.value={loading:true,fileName:file.name,message:'正在处理...',ok:null}
  preview.value=''
  voucher.value={loading:false,message:'',ok:null,id:''}
  try{
    const isPdf=file.type==='application/pdf'||file.name.toLowerCase().endsWith('.pdf')
    if(isPdf){
      ocr.value={loading:true,fileName:file.name,message:'正在提取 PDF 文本...',ok:null}
      const textContent=await extractPdfText(file)
      ocr.value={loading:true,fileName:file.name,message:'正在解析字段...',ok:null}
      const r=await apiFetch('/api/ocr/parse-text',{method:'POST',headers:hdr(),body:JSON.stringify({text:textContent})})
      const data=await r.json()
      if(!r.ok)throw new Error(data.error||'解析失败')
      // still render preview image
      try{preview.value=await renderPdfFirstPage(file)}catch(e){}
      applyOcr(data)
      ocr.value={loading:false,fileName:file.name,message:'已回填识别结果',ok:true}
      return
    }
    const image=await fileToOcrImage(file)
    preview.value=image
    ocr.value={loading:true,fileName:file.name,message:'AI 识别中...',ok:null}
    const r=await apiFetch('/api/ocr/recognize',{method:'POST',headers:hdr(),body:JSON.stringify({image})})
    const data=await r.json()
    if(!r.ok)throw new Error(data.error||'识别失败')
    applyOcr(data)
    ocr.value={loading:false,fileName:file.name,message:'已回填识别结果',ok:true}
  }catch(err){
    ocr.value={loading:false,fileName:file.name,message:err.message||'识别失败',ok:false}
  }finally{
    e.target.value=''
  }
}
</script>

<template>
  <div class="top-bar">
    <BackButton to="/invoices" />
    <div class="breadcrumb">
      <a @click.prevent="router.push('/')" href="/">发票管家</a>
      <span style="color:var(--border);">›</span>
      <a @click.prevent="router.push('/invoices')" href="/invoices">发票列表</a>
      <span style="color:var(--border);">›</span>
      <span>发票录入</span>
    </div>
  </div>

  <h1>发票录入</h1>

  <div class="mode-tabs">
    <button class="mode-tab" :class="{active:mode==='manual'}" @click="mode='manual'">手动录入</button>
    <button class="mode-tab" :class="{active:mode==='upload'}" @click="mode='upload'">图片/PDF 上传</button>
  </div>

  <div v-if="mode==='manual'" class="form-card">
    <div class="form-grid">
      <div class="form-group">
        <label>发票类型</label>
        <select class="input" v-model="f.type">
          <option value="增值税专用发票">增值税专用发票</option>
          <option value="增值税电子普通发票">增值税电子普通发票</option>
          <option value="增值税电子专用发票">增值税电子专用发票</option>
          <option value="普通发票">普通发票</option>
        </select>
      </div>
      <div class="form-group">
        <label>发票代码</label>
        <input class="input" v-model="f.code" maxlength="12" placeholder="12位发票代码">
      </div>
      <div class="form-group">
        <label>发票号码</label>
        <input class="input" v-model="f.num" maxlength="12" placeholder="8-12位发票号码">
      </div>
      <div class="form-group">
        <label>开票日期</label>
        <input class="input" v-model="f.date" type="date">
      </div>
      <div class="form-group">
        <label>销售方</label>
        <input class="input" v-model="f.seller" placeholder="销售方企业全称">
      </div>
      <div class="form-group">
        <label>购买方</label>
        <input class="input" v-model="f.buyer" placeholder="默认：广州共生纪元云科技有限公司">
      </div>
      <div class="form-group">
        <label>购买方纳税人识别号</label>
        <input class="input" v-model="f.buyerTaxNo" placeholder="统一社会信用代码">
      </div>
      <div class="form-group">
        <label>销售方纳税人识别号</label>
        <input class="input" v-model="f.sellerTaxNo" placeholder="统一社会信用代码">
      </div>
      <div class="form-group">
        <label>购买方地址、电话</label>
        <input class="input" v-model="f.buyerAddressPhone" placeholder="地址 电话">
      </div>
      <div class="form-group">
        <label>销售方地址、电话</label>
        <input class="input" v-model="f.sellerAddressPhone" placeholder="地址 电话">
      </div>
      <div class="form-group">
        <label>购买方开户行及账号</label>
        <input class="input" v-model="f.buyerBankAccount" placeholder="开户行 账号">
      </div>
      <div class="form-group">
        <label>销售方开户行及账号</label>
        <input class="input" v-model="f.sellerBankAccount" placeholder="开户行 账号">
      </div>
      <div class="form-group">
        <label>发票类别</label>
        <select class="input" v-model="f.cat">
          <option value="service">技术服务</option>
          <option value="travel">差旅交通</option>
          <option value="catering">餐饮招待</option>
          <option value="office">办公用品</option>
          <option value="utility">水电物业</option>
          <option value="logistics">物流快递</option>
          <option value="rental">房租租赁</option>
          <option value="other">其他</option>
        </select>
      </div>
      <div class="form-group">
        <label>项目名称</label>
        <input class="input" v-model="f.itemName" placeholder="例如：*修理修配劳务*修理修配服务">
      </div>
      <div class="form-group">
        <label>规格型号</label>
        <input class="input" v-model="f.itemSpec" placeholder="可选">
      </div>
      <div class="form-group">
        <label>单位</label>
        <input class="input" v-model="f.itemUnit" placeholder="项、次、件等">
      </div>
      <div class="form-group">
        <label>数量</label>
        <input class="input" v-model.number="f.itemQuantity" type="number" step="0.000001" min="0" @input="syncAmountFromLine">
      </div>
      <div class="form-group">
        <label>单价（不含税）</label>
        <input class="input" v-model.number="f.itemUnitPrice" type="number" step="0.000001" min="0" @input="syncAmountFromLine">
      </div>
      <div class="form-group">
        <label>不含税金额（元）</label>
        <input class="input" v-model.number="f.amount" type="number" step="0.01" min="0" placeholder="0.00">
      </div>
      <div class="form-group">
        <label>税率（%）</label>
        <select class="input" v-model.number="f.taxRate">
          <option :value="0">0%</option>
          <option :value="3">3%</option>
          <option :value="6">6%</option>
          <option :value="9">9%</option>
          <option :value="13">13%</option>
        </select>
      </div>
      <div class="form-group">
        <label>备注</label>
        <input class="input" v-model="f.notes" placeholder="用途说明、关联项目等">
      </div>
    </div>

    <div class="card" style="margin-top:var(--space-lg);background:var(--bg);box-shadow:none;">
      <div style="display:flex;justify-content:space-between;align-items:center;">
        <span class="text-xs">含税总额预览</span>
        <span style="font-family:ui-monospace,SFMono-Regular,Menlo,monospace;font-size:20px;font-weight:600;">¥{{ fmt(totalAmount) }}</span>
      </div>
      <div class="text-xs" style="margin-top:8px;">价税合计（大写）：{{ totalAmountCn }}</div>
    </div>

    <div class="flex mt">
      <button class="btn btn-primary btn-lg" :disabled="saving" @click="submit">确认录入</button>
      <button class="btn btn-o btn-lg" @click="router.push('/invoices')">取消</button>
    </div>
    <div v-if="error" style="margin-top:var(--space-md);color:var(--danger);font-size:14px;">{{ error }}</div>
  </div>

  <div v-else class="form-card">
    <label class="ocr-zone">
      <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="var(--accent)" stroke-width="1.5" style="opacity:.65;">
        <rect x="3" y="3" width="18" height="18" rx="3"/><circle cx="8.5" cy="8.5" r="1.5"/><polyline points="21 15 16 10 5 21"/>
      </svg>
      <strong>{{ ocr.fileName || '点击上传发票图片或 PDF' }}</strong>
      <span class="hint">支持 JPG、PNG、PDF；PDF 会自动渲染首页用于识别</span>
      <input type="file" class="hidden" accept="image/*,.pdf" @change="recognize">
    </label>
    <div v-if="ocr.message" style="margin-top:var(--space-md);text-align:center;font-size:14px;" :style="{color: ocr.ok === false ? 'var(--danger)' : (ocr.ok ? 'var(--success)' : 'var(--accent)')}">{{ ocr.message }}</div>
    <img v-if="preview" :src="preview" alt="发票预览" style="max-width:100%;max-height:300px;border-radius:var(--radius-md);display:block;margin:var(--space-md) auto 0;">

    <div v-if="ocr.ok" style="margin-top:var(--space-lg);">
      <h2>AI 识别结果 <span class="hint">（可手动修正）</span></h2>
      <div class="ocr-result">
        <div class="field-row"><span class="field-label">发票类型</span><span class="field-value"><input class="input" v-model="f.type"></span></div>
        <div class="field-row"><span class="field-label">发票号码</span><span class="field-value"><input class="input" v-model="f.num"></span></div>
        <div class="field-row"><span class="field-label">开票日期</span><span class="field-value"><input class="input" type="date" v-model="f.date"></span></div>
        <div class="field-row"><span class="field-label">销售方</span><span class="field-value"><input class="input" v-model="f.seller"></span></div>
        <div class="field-row"><span class="field-label">购买方</span><span class="field-value"><input class="input" v-model="f.buyer"></span></div>
        <div class="field-row"><span class="field-label">项目名称</span><span class="field-value"><input class="input" v-model="f.itemName"></span></div>
        <div class="field-row"><span class="field-label">不含税金额</span><span class="field-value"><input class="input" type="number" step="0.01" v-model.number="f.amount"></span></div>
        <div class="field-row"><span class="field-label">税率（%）</span><span class="field-value"><input class="input" type="number" step="1" v-model.number="f.taxRate"></span></div>
        <div class="field-row"><span class="field-label">备注</span><span class="field-value"><input class="input" v-model="f.notes"></span></div>
      </div>
      <div class="flex mt">
        <button class="btn btn-primary" :disabled="saving" @click="submit">确认并录入</button>
        <button class="btn btn-o" :disabled="voucher.loading" @click="createVoucherDraft">生成凭证草稿</button>
      </div>
      <div v-if="voucher.message" style="margin-top:var(--space-md);font-size:14px;" :style="{color: voucher.ok === false ? 'var(--danger)' : (voucher.ok ? 'var(--success)' : 'var(--accent)')}">{{ voucher.message }}</div>
      <div v-if="error" style="margin-top:var(--space-md);color:var(--danger);font-size:14px;">{{ error }}</div>
    </div>
  </div>
</template>


