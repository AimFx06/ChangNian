# 常念 APK 打包复现说明

提交包中的 APK 是一个原生 Android WebView 外壳，内置最新 H5 前端资源，并通过登录页“服务器”输入框连接 Docker 后端。这样评委无需安装 HBuilderX 也能直接安装测试。

## 产物位置

```text
android-wrapper/app/build/outputs/apk/release/app-release.apk
```

提交包中复制为：

```text
01-可运行产品包/常念.apk
```

## 复现步骤

1. 构建后端 jar：

```powershell
cd changan-server
D:\AI\Codex\.codex-tools\apache-maven-3.9.9\bin\mvn.cmd -q -DskipTests package
```

2. 构建 H5 前端：

```powershell
cd ..\changan-app
npm.cmd run build:h5
```

3. 同步 H5 到 Android assets：

```powershell
cd ..
Remove-Item -Recurse -Force android-wrapper\app\src\main\assets\www\*
Copy-Item -Recurse changan-app\dist\build\h5\* android-wrapper\app\src\main\assets\www\
```

4. 打包 release APK：

```powershell
$env:ANDROID_HOME='D:\AI\Codex\.codex-tools\android-sdk'
$env:ANDROID_SDK_ROOT='D:\AI\Codex\.codex-tools\android-sdk'
cd android-wrapper
D:\AI\Codex\.codex-tools\gradle-8.10.2\bin\gradle.bat --no-daemon assembleRelease
```

5. 验证 APK 签名和清单：

```powershell
D:\AI\Codex\.codex-tools\android-sdk\build-tools\35.0.0\apksigner.bat verify --verbose --print-certs app\build\outputs\apk\release\app-release.apk
D:\AI\Codex\.codex-tools\android-sdk\build-tools\35.0.0\aapt2.exe dump badging app\build\outputs\apk\release\app-release.apk
```

## 已确认信息

- 包名：`com.changnian.app`
- 应用名：`常念`
- versionName：`1.0.0`
- minSdk：23
- targetSdk：35
- 权限：网络、录音、相机、网络状态
- 摄像头和麦克风声明为非强制硬件，降低安装兼容性限制。

## 说明

uni-app 官方 App 资源也可通过 `npm.cmd run build:app` 生成到 `changan-app/dist/build/app`，但正式原生 APK 通常需要 HBuilderX 或云打包。本提交包采用 Android WebView 外壳，便于离线复现和比赛评审安装。

