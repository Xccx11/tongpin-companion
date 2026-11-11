# 已知问题（Known issues）

> 2026-11-11：**CI 的签名问题已修复** ✅ —— 提交 `833ef96` 随仓库分发了一个一次性 debug keystore
> （`docs/app/debug.keystore`），`localDebug` 改为相对路径引用，CI 现在 **build ✓ / tests ✓** 全绿。

## 历史：CI `assembleDebug` 曾因 debug 签名失败

**根因**：`docs/app/build.gradle.kts` 的 `debug` 构建类型引用 `signingConfigs.getByName("localDebug")`，
而 `localDebug` 的 `storeFile` 写死为 `D:\.android\debug.keystore`（Windows 盘符）——
任何没有 `D:` 盘的环境（Linux/macOS CI）必然失败。

**修复**（`833ef96`）：

1. 生成一次性 debug 密钥并**随仓库分发**（debug 密钥可公开，仅用于本地/CI 构建）：
   ```
   keytool -genkeypair -keystore docs/app/debug.keystore -storepass android \
     -alias androiddebugkey -keypass android -keyalg RSA -keysize 2048 -validity 10000 \
     -dname "CN=Android Debug,O=Android,C=US"
   ```
2. `localDebug.storeFile` 改为 `rootProject.file("docs/app/debug.keystore")`（相对路径，跨平台）。
3. 其余密码/别名保持不变，`debug` 的引用保持不变。

> ⚠️ 该密钥**只用于 debug 构建**；正式发布请使用你自己的签名配置（`release` 走
> `KEYSTORE_PATH` / `KEYSTORE_PASSWORD` / `KEY_ALIAS` / `KEY_PASSWORD` 环境变量）。