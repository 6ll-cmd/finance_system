# 发票管家 — 发票自动管理系统

> 苏式AI美学 · 桌面应用 · 多账号 · AI 识别

## 项目概述

发票管家是一个面向中小企业的发票自动管理系统，支持发票录入、凭证管理、数据看板、统计报表等核心功能。系统以**广州共生纪元云科技有限公司**为使用主体，设计风格采用**苏式AI美学**——宣纸白底配合青瓷蓝紫单一强调色，信息密度适中，克制而精致。

## 视觉系统

| Token | 值 | 用途 |
|-------|-----|------|
| `--bg` | `#f8f7fb` | 宣纸白底，微冷调 |
| `--surface` | `#ffffff` | 卡片/面板纯白 |
| `--fg` | `#1a1a1e` | 深墨色主文字 |
| `--muted` | `#6e6d75` | 次级灰色文字 |
| `--border` | `#e8e6ef` | 淡紫灰边框 |
| `--accent` | `#5b5fc7` | 青瓷蓝紫强调色 |
| `--on-accent` | `#ffffff` | 强调色上的文字 |
| `--success` | `#2d8a56` | 已报销/通过 |
| `--warn` | `#b05b14` | 待处理 |
| `--danger` | `#c4342d` | 已退回/异常 |

字体：系统原生字体栈（SF Pro / PingFang SC / Microsoft YaHei），等宽数字。

## 文件结构

```
project/
├── start.bat / start.sh / app.bat  ← 启动脚本（浏览器/桌面应用）
├── server.js               ← Express 入口（精简路由注册）
├── server/                 ← 后端模块
│   ├── config.js           ← 配置加载
│   ├── database.js         ← 数据库初始化与迁移
│   ├── middleware/
│   │   ├── auth.js         ← Token 认证 + 过期检查
│   │   ├── serveStatic.js  ← 静态文件服务（pkg 兼容）
│   │   └── rateLimiter.js  ← 内存频率限制
│   ├── routes/
│   │   ├── auth.js         ← 登录/注册/当前用户
│   │   ├── invoices.js     ← 发票 CRUD + 导出 + 上传
│   │   ├── vouchers.js     ← 凭证 CRUD + 借贷分录
│   │   ├── stats.js        ← 统计/报表/类别
│   │   ├── accounts.js     ← 科目表
│   │   ├── audit.js        ← 审计日志
│   │   ├── config.js       ← 系统配置（管理员）
│   │   ├── ai.js           ← AI OCR + 对话 + 连接测试
│   │   └── system.js       ← 关机 + 空闲退出
│   └── utils/
│       ├── helpers.js      ← mapInvoice, mapVoucher, logAudit
│       └── ai-clients.js   ← OpenAI/Claude 客户端函数
├── package.json            ← npm 配置
├── config.json             ← AI 识别配置（不纳入版本控制）
├── config.example.json     ← AI 配置模板
├── index.html              ← 首页（侧边栏导航布局）
├── login.html              ← 登录/注册页
├── pages/                  ← 功能页面
│   ├── dashboard.html      ← 数据看板
│   ├── reports.html        ← 统计报表
│   ├── invoice-list.html   ← 发票列表
│   ├── invoice-add.html    ← 发票录入（手动 + OCR + AI）
│   ├── invoice-detail.html ← 发票详情
│   ├── voucher-list.html   ← 凭证列表
│   ├── voucher-add.html    ← 新增凭证
│   └── voucher-detail.html ← 凭证详情
│   ├── accounts.html       ← 科目表（37个预置科目，树形结构）
│   └── settings.html       ← 系统设置（AI 配置 + 账号）
├── css/
│   └── tokens.css          ← 设计令牌（含焦点态 + 动效偏好）
├── js/
│   ├── auth.js             ← 全局认证守卫
│   └── ai-assistant.js     ← AI 助手面板
├── db/
│   └── schema.sql          ← 数据库结构
├── data.json               ← 旧版数据（首次启动自动迁移）
└── assets/ref/             ← 参考素材
```

## 数据库架构

使用 **SQLite** 数据库（Node.js v22+ 内置 `node:sqlite`，零额外依赖）。

### 表结构

| 表 | 说明 | 核心字段 |
|----|------|---------|
| `users` | 用户账号 | id, username, password(SHA-256), token |
| `invoices` | 发票主表 | id, invoice_date, seller, buyer, type, category_id, amount, tax_rate, tax_amount, total_amount, status, user_id |
| `categories` | 发票类别 | id, name, icon (预置 8 类) |
| `vouchers` | 凭证主表 | id, voucher_date, description, total_amount, status, user_id |
| `voucher_entries` | 凭证明细 | id, voucher_id, account, debit_amount, credit_amount, summary |
| `accounts` | 会计科目表 | id, name, parent_id(树形), account_type, level (预置 37 个科目) |
| `audit_log` | 审计日志 | entity_id, action, old_status, new_status, changed_at |

### 状态流转

**发票**: `pending → reimbursed` / `pending → rejected → pending`

**凭证**: `draft → posted` / `draft → cancelled → draft`

## 功能模块

### 首页 (`index.html`)
- 侧边栏导航布局：Logo → 主菜单 → 发票管理 → 凭证管理 → 分析 → 用户信息
- 4 个统计卡片（发票总数 / 本月新增 / 待报销 / 已报销，API 动态加载）
- 4 个快捷操作卡片
- 最近录入动态列表
- 响应式：桌面侧边栏 → 移动端折叠图标栏

### 发票录入 (`invoice-add.html`)
- **手动录入**：11 字段表单 + 实时含税总额计算 + 表单校验
- **AI 识别**（推荐）：上传发票图片/PDF → AI 视觉模型自动提取 → 自动录入（支持 OpenAI GPT-4o / Claude，需配置 `config.json`）
- **本地 OCR**（备选）：Tesseract.js 浏览器端识别，支持中文，首次自动下载语言包
- **PDF 支持**：pdf.js 自动渲染第一页用于识别

### 发票列表 (`invoice-list.html`)
- 全文搜索（发票号 / 销售方 / 备注）+ 300ms 防抖
- 按状态、类别筛选 + 按日期、金额排序
- 分页显示 + 多选批量操作（标记已报销 / 退回）
- URL 参数预筛选

### 发票详情 (`invoice-detail.html`)
- 完整发票信息 + 不含税金额/税额/含税总额三段式计算
- 状态变更操作（标记已报销 / 退回 / 重置）

### 凭证管理 (`voucher-list.html` / `voucher-add.html` / `voucher-detail.html`)
- 凭证号/摘要搜索 + 状态筛选 + 分页
- 多行借贷分录录入，会计科目从科目表下拉选择
- 实时平衡校验（差额归零才能提交）
- 完整分录明细表 + 借方/贷方合计 + 过账/作废操作
- 支持编辑草稿凭证（PUT）和删除（DELETE）

### 科目表 (`accounts.html`)
- 37 个预置会计科目，资产/负债/权益/收入/费用五大类
- 树形层级结构（一级科目 + 二级明细）
- 凭证录入时自动从科目表加载下拉选项

### 数据看板 & 统计报表
- 4 个统计卡片 + 空白状态诚实占位
- Canvas 图表（数据录入后自动展示）

## 后端服务

### 启动

```bash
npm install        # 安装依赖（仅首次）
node server.js     # 启动服务（默认端口 3456）
```

启动后访问 `http://localhost:3456`。也可双击 `start.bat`（浏览器模式）或 `app.bat`（桌面应用模式）。

### API 文档（除登录/注册外均需 Bearer Token）

| 方法 | 端点 | 说明 |
|------|------|------|
| `POST` | `/api/register` | 注册 |
| `POST` | `/api/login` | 登录 |
| `GET` | `/api/me` | 当前用户信息 |
| `GET` | `/api/invoices` | 发票列表（`?search=&status=&category=&sort=&page=&limit=`） |
| `GET` | `/api/invoices/:id` | 发票详情 |
| `POST` | `/api/invoices` | 录入发票 |
| `PATCH` | `/api/invoices/:id/status` | 发票状态变更 |
| `PATCH` | `/api/invoices/batch-status` | 批量状态变更 |
| `GET` | `/api/vouchers` | 凭证列表 |
| `GET` | `/api/vouchers/:id` | 凭证详情（含分录） |
| `POST` | `/api/vouchers` | 创建凭证（自动借贷平衡校验） |
| `PATCH` | `/api/vouchers/:id/status` | 凭证状态变更 |
| `POST` | `/api/ocr/recognize` | AI 发票识别（需配置 `config.json`） |
| `POST` | `/api/ai/chat` | AI 对话助手（全系统智能引擎） |
| `GET` | `/api/stats` | 看板统计 |
| `GET` | `/api/reports` | 报表数据 |
| `GET` | `/api/accounts` | 科目表（树形结构，含预置 37 个科目） |
| `GET` | `/api/categories` | 类别列表 |
| `GET` | `/api/audit-log` | 审计日志 |

### AI 识别配置

编辑 `config.json` 填入 API Key（支持 OpenAI GPT-4o / Anthropic Claude / 兼容接口）。

### 全系统 AI 集成（v4.0）

AI 大模型贯穿整个系统：

| 功能 | 入口 | 说明 |
|------|------|------|
| 🤖 AI 对话助手 | 侧边栏「AI 助手」按钮 | 自然语言操作：录入发票、查统计、分析数据 |
| 📄 发票 OCR 识别 | 发票录入 → OCR 标签 | AI 视觉模型自动识别发票信息并录入 |
| ✍️ 自然语言录入 | AI 助手对话 | "帮我录入深圳科技公司发票5000元" → 自动创建 |
| 📊 智能分析 | AI 助手对话 | "本月支出情况" → AI 读取真实数据生成分析 |

未配置 AI 时，OCR 自动降级为本地 Tesseract.js 识别。

## 多账号系统

每个用户拥有独立的数据空间。

- 默认管理员：`admin` / `admin123`
- 新用户注册后数据从空白开始
- SHA-256 密码哈希 + 64 位随机 Token 认证
- `js/auth.js` 全局守卫：自动注入 token → 401 自动跳转登录

## 打包分发

### 单文件 EXE（推荐给同事）

```bash
npm run build         # 生成 dist/发票管家.exe (90MB)
```

对方只需双击 EXE，无需安装 Node.js、npm 或数据库。

### 分享文件清单

发送整个 `dist/` 文件夹，包含：
- `发票管家.exe` — 服务端
- `app.bat` — 双击以独立应用窗口启动（无浏览器边框）
- `使用说明.txt`

## 技术要点

- **纯 HTML/CSS/JS**，零运行时依赖（Express 除外）
- **SQLite**（Node.js v22+ 内置 `node:sqlite`）
- **Tesseract.js** 浏览器端中文 OCR + **AI 视觉模型**云端识别
- **pkg** 单文件 EXE 打包
- **Edge App Mode** 桌面应用窗口
- **Canvas API** 手绘图表
- **CSS 自定义属性** 统一设计令牌
- **可访问性**：`:focus-visible` 焦点态、`prefers-reduced-motion` 动效适配、语义化 HTML
- **无 AI 套路**：无 emoji 图标、无紫色渐变、无硬编码 Indigo

## 设计评审

| 维度 | 评分 | 说明 |
|------|------|------|
| 哲学 | 4/5 | 苏式宣纸白底 + 青瓷蓝紫单色贯彻始终 |
| 层次 | 4/5 | 侧边栏导航 + 面包屑，每页单一焦点 |
| 执行 | 4/5 | 完整 token 体系 + `--on-accent` + 全局焦点态 |
| 具体性 | 5/5 | 企业实名、真发票字段、无填充文字 |
| 克制 | 4/5 | 单一强调色，1px 边框，`:focus-visible` 代替装饰 |

## 最近变更

**2026-06-23 — 安全加固与架构优化 (v3.1)**

- 🔒 `config.json` 已加入 `.gitignore`，新增 `config.example.json` 模板，防止 API Key 泄露
- 🔒 登录/注册增加频率限制：登录 5 次/分钟，注册 3 次/分钟
- 🔒 密码策略升级：至少 8 位，必须包含字母和数字
- 🔒 Token 7 天自动过期，过期需重新登录
- 🔒 审计日志新增 `user_id` 字段，多用户模式下可追溯操作人
- 🔒 服务器端统计查询全部参数化，消除 SQL 注入风险
- 🗑️ 移除登录页默认账号明文显示
- 🐛 修复登录/注册按钮在网络异常时无法恢复点击的问题
- 🐛 修复主页、科目表、设置页缺少 `auth.js` 导致 API 请求无 Token 的问题
- 🐛 修复 `schema.sql` 重复执行问题
- 🏗️ `server.js` 模块化拆分为 `server/` 目录：路由/中间件/数据库/工具函数分离
- 🗑️ 移除根目录重复的旧版 `auth.js`
- 📝 新增 `config.example.json` 配置模板

**2026-06-22 — 科目表 (v5.0，参考 ERPNext)**

- ✅ `db/schema.sql` 新增 `accounts` 表，树形层级结构（parent_id 自引用）
- ✅ 预置 37 个会计科目：资产/负债/权益/收入/费用五大类，二级明细
- ✅ `GET /api/accounts` 端点，按编号排序返回完整科目树
- ✅ `pages/accounts.html` 科目表查看页面，层级缩进展示
- ✅ `pages/voucher-add.html` 会计科目从自由文本改为科目表下拉选择
- ✅ `index.html` 侧边栏新增「科目表」导航入口
- ✅ 凭证编辑 (`PUT /api/vouchers/:id`) + 删除 (`DELETE /api/vouchers/:id`)

## 待扩展

- [x] ~~真实 OCR API 接入~~ → Tesseract.js + AI 视觉模型
- [x] ~~用户登录与权限~~ → 多账号 + 管理员角色
- [x] ~~暗色主题切换~~ → `[data-theme="dark"]` + 侧边栏切换
- [x] ~~导出 CSV/Excel~~ → `/api/invoices/export`
- [x] ~~数据看板图表动态化~~ → Canvas 从 `/api/reports` 实时渲染
- [x] ~~会计科目表~~ → 37 个预置科目，树形结构，凭证下拉选择
- [ ] 发票图片上传至服务器存储
- [ ] 数据备份与恢复
- [ ] 邮件通知（报销审批）

## GitHub 仓库说明

- 仓库默认提交源码与初始化 SQL。
- `config.json`、`node_modules/`、SQLite 运行库文件（`db/*.db*`）以及本地打包产物（`*.exe`）不纳入版本控制。
- 首次拉取后请复制 `config.example.json` 为 `config.json`，再填写 AI 配置。
