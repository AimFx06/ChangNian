# 常念项目落地版

这是根据《常念项目策划书》落地的第一版完整项目，包含 UniApp 前端、Spring Boot 后端、MySQL 数据库、WebSocket 实时同步和 DeepSeek API 封装。

## 目录

- `changan-app`：UniApp Vue3 + TypeScript 前端，可运行 H5，也可导入 HBuilderX 打包 Android APK。
- `changan-server`：Spring Boot 3 + MyBatis-Plus 后端。
- `docs`：接口说明、数据库设计和演示流程。

## 已实现范围

- 注册、登录、家庭邀请码绑定。
- 默认演示账号：
  - 本人端：`child / 16888`
  - 妈妈端：`mother / 16888`
  - 爸爸端：`father / 16888`
  - 家庭邀请码：`CHANGNIAN-DEMO`
- 子女端四个核心业务能力：
  - 近况：周报、近期状态、SOS 横幅。
  - 话题：话题卡、递话筒、老人回应列表。
  - 家：回忆视图、数据视图。
- 老人端一屏：
  - 消息回应。
  - 内置用药提醒、吃了、待会吃。
  - SOS 三秒倒计时，发送紧急记录并实时通知子女端。
  - 看病助手入口占位。
- DeepSeek 后端接口：
  - `/api/ai/weekly-report`
  - `/api/ai/topics`
  - `/api/ai/insights/refresh`

## 后端启动

要求：

- JDK 17
- MySQL 8
- Maven 3.9+

默认数据库连接：

```powershell
DB_HOST=localhost
DB_PORT=3306
DB_NAME=changnian
DB_USERNAME=root
DB_PASSWORD=123456
```

启动：

```powershell
cd D:\AI\Codex\常念\项目落地大文件夹\changan-server
mvn spring-boot:run
```

如果没有安装 Maven，可以使用我本次验证时下载的临时 Maven：

```powershell
cd D:\AI\Codex\常念\项目落地大文件夹\changan-server
& "$env:TEMP\codex-maven-3.9.9\apache-maven-3.9.9\bin\mvn.cmd" spring-boot:run
```

健康检查：

```powershell
Invoke-WebRequest http://127.0.0.1:8080/api/health
```

## DeepSeek 配置

不要把 Key 写进前端。后端通过环境变量读取：

```powershell
$env:DEEPSEEK_API_KEY="你的 DeepSeek Key"
$env:DEEPSEEK_MODEL="deepseek-v4-flash"
```

未配置 Key 时，AI 接口会返回本地兜底示例，方便开发流程继续跑。

DeepSeek 调用链路在后端完成：前端点击话题页的 `AI卡片` 按钮，调用 `/api/ai/insights/refresh`。后端按当前选择的妈妈/爸爸从数据库收集留言、用药、就诊、近况和记忆信号，再调用 DeepSeek 生成周报、每日近况、话题卡和“说过的事”，分别写入 `weekly_reports`、`daily_statuses`、`topic_cards` 和 `family_memories`。接口返回 `mocked=false` 才表示本次是真实 DeepSeek 生成；未配置 Key 时返回本地兜底示例。

## 前端启动

```powershell
cd D:\AI\Codex\常念\项目落地大文件夹\changan-app
npm install
npm run dev:h5 -- --host 0.0.0.0
```

本机浏览器访问：

```text
http://127.0.0.1:5173/
```

手机同 Wi-Fi 访问时，用 Vite 输出的 Network 地址，例如：

```text
http://192.168.16.197:5173/
```

前端会自动把 API 指向同一个局域网 IP 的 `8080` 端口。

如果手机打不开页面，通常是 Windows 把当前 Wi-Fi 识别为公用网络并拦截入站端口。请用管理员 PowerShell 执行：

```powershell
cd D:\AI\Codex\常念\项目落地大文件夹
.\tools\allow-phone-access.ps1
```

然后在手机浏览器打开：

```text
http://192.168.16.197:5173/
```

如果仍打不开，检查手机和电脑是否在同一个 Wi-Fi，或改用手机热点让电脑连接手机热点后，重新查看前端命令输出里的 `Network` 地址。

## 打包到 vivo 手机

第一版推荐流程：

1. 用 HBuilderX 打开 `changan-app`。
2. 确认 `src/manifest.json` 中的 Android 权限包含网络、录音、相机。
3. 运行到 Android 手机或云打包 APK。
4. 手机和电脑在同一 Wi-Fi 下，后端运行在电脑 `8080` 端口。
5. 如需固定后端地址，可在构建时设置：

```powershell
$env:VITE_API_HOST="http://电脑局域网IP:8080"
npm run build:h5
```

## 当前验证结果

- 后端：`mvn -DskipTests package` 通过。
- 前端：`npm run build:h5` 通过。
- 数据库：MySQL 8 可连接，后端启动后自动建表和写入演示账号。
- 接口闭环已验证：
  - 子女登录成功。
  - 子女读取首页成功。
  - 子女递话筒成功。
  - 老人读取待回应成功。
  - 老人回应成功。
  - 用药记录成功。
  - SOS 记录成功。
