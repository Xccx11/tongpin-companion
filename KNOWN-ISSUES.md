# 已知问题（Known issues）

## CI 的 `assembleDebug` 步骤失败（debug 签名）

**已定位到根因**（2026-11-11，干净 clone 复现确认）：

1. `docs/app/build.gradle.kts` 里 `debug` 构建类型引用了 `signingConfigs.getByName("localDebug")`；
2. `localDebug` 这个 signingConfig 原先写死 Windows 路径（`D:\.android\debug.keystore`），
   在任何没有 `D:` 盘的环境（Linux/macOS CI）必然失败；
3. 只删 `localDebug` 配置、保留引用会报 `SigningConfig with name 'localDebug' not found`；
   两者都删则报 Gradle 8.14 的 `signingConfigData.storeFile ... unreadable inputs`。

**当前状态**：
- ✅ `Unit tests` 在 CI 上通过（1089 个测试全绿）；
- ❌ `Assemble debug APK` 仍失败（仅在 CI / 非 Windows 环境）；
- ✅ **本地 Windows 构建正常**（`BUILD SUCCESSFUL`）。

**推荐修复（改为一次性 debug 密钥，随仓库分发）**：

1. 生成一个 debug keystore 并提交到仓库（debug 密钥是一次性的，公开无害）：
   ```
   keytool -genkeypair -keystore docs/app/debug.keystore -storepass android \
     -alias androiddebugkey -keypass android -keyalg RSA -keysize 2048 -validity 10000 \
     -dname "CN=Android Debug,O=Android,C=US"
   ```
2. 在 `signingConfigs` 里新增：
   ```kotlin
   create("localDebug") {
       storeFile = rootProject.file("docs/app/debug.keystore")
       storePassword = "android"
       keyAlias = "androiddebugkey"
       keyPassword = "android"
   }
   ```
3. `debug { signingConfig = signingConfigs.getByName("localDebug") }` 保持引用一致。

> 注意：不要把**正式发布密钥**放进仓库；上面这个只用于 debug 构建。