# 财务系统

这是一个本地运行的发票与凭证管理系统，当前项目结构已经迁移为：

- 后端：Spring Boot 3.4 + MyBatis-Plus + PostgreSQL + Flyway
- 前端：Vue 3 + Vite
- 启动方式：双击 `启动.bat`

## 常用文件

| 路径 | 说明 |
| --- | --- |
| `启动.bat` | 一键启动 PostgreSQL、停止旧 Java 进程、启动 Spring Boot jar，并打开浏览器 |
| `pom.xml` | 后端 Maven 配置 |
| `src/main/java/` | Spring Boot 后端源码 |
| `src/main/resources/db/migration/` | Flyway 数据库迁移脚本 |
| `src/main/resources/static/` | Vue 构建后的静态资源，由 Spring Boot 直接托管 |
| `frontend/` | Vue 前端源码 |
| `frontend/package.json` | 前端依赖与构建脚本 |
| `target/finance-system-4.0.0.jar` | `启动.bat` 当前运行的应用包 |
| `data/ai-config.json` | 本地 AI 配置数据，不应提交或外发 |

## 启动

双击：

```bat
启动.bat
```

启动后访问：

```text
http://127.0.0.1:3456
```

默认账号：

```text
admin / admin123
```

## 开发与打包

前端构建：

```powershell
cd frontend
npm.cmd run build
```

后端打包：

```powershell
C:\tools\maven\apache-maven-3.9.8\bin\mvn.cmd package -DskipTests
```

如果打包时报 `Unable to rename ... finance-system-4.0.0.jar`，通常是旧服务正在占用 jar。先停止 3456 端口对应 Java 进程，再重新打包。

## 当前主要功能

- 发票录入、图片/PDF 识别、发票详情与列表
- 凭证录入、凭证列表、凭证明细、过账/删除操作密码校验
- 凭证列表支持导出 CSV 与 Excel
- 科目表
- 资产负债表、利润表、现金流量表
- 系统设置与 AI 配置

## 文件整理说明

项目已经从旧版 HTML/Node 结构迁移到 Spring Boot + Vue。旧的根目录 `index.html`、`login.html`、`pages/`、`css/`、`js/` 不再参与启动，当前页面来自 `frontend` 构建后写入的 `src/main/resources/static/`。
