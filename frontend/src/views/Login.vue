<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
const router = useRouter()
const username = ref('')
const password = ref('')
const error = ref('')

async function login(){
  if(!username.value||!password.value){error.value='请填写用户名和密码';return}
  if(password.value.length<8){error.value='密码至少8位';return}
  const body=JSON.stringify({username:username.value,password:password.value})
  let r=await fetch('/api/login',{method:'POST',headers:{'Content-Type':'application/json'},body})
  if(!r.ok){
    let r2=await fetch('/api/register',{method:'POST',headers:{'Content-Type':'application/json'},body})
    if(!r2.ok){error.value='注册失败';return}
    const d=await r2.json()
    localStorage.setItem('fin_user',JSON.stringify({id:d.id,username:d.username,role:'user'}))
    localStorage.setItem('fin_token',d.token)
    localStorage.setItem('fin_refresh',d.refreshToken||'')
  }else{
    const d=await r.json()
    localStorage.setItem('fin_user',JSON.stringify({id:d.id,username:d.username,role:'user'}))
    localStorage.setItem('fin_token',d.token)
    localStorage.setItem('fin_refresh',d.refreshToken||'')
  }
  router.push('/')
}
</script>

<template>
  <div class="login-overlay">
    <div class="login-card">
      <div style="text-align:center;margin-bottom:20px">
        <h1 style="margin-bottom:4px;font-size:22px">发票管家</h1>
        <p style="color:var(--muted);font-size:13px">广州共生纪元云科技有限公司</p>
      </div>
      <input v-model="username" placeholder="用户名" autocomplete="username" @keyup.enter="login">
      <input v-model="password" type="password" placeholder="密码" autocomplete="current-password" @keyup.enter="login">
      <button class="btn" @click="login" style="width:100%;justify-content:center">登录 / 注册</button>
      <p v-if="error" style="color:var(--danger);font-size:13px;margin-top:8px;text-align:center">{{ error }}</p>
    </div>
  </div>
</template>
