# 已知问题（Known issues）

## CI 在 `assembleDebug` 步骤失败（signing 配置）

**现象**：GitHub Actions 的 `Assemble debug APK` 报

```
> Keystore file '.../D:\.android\debug.keystore' not found for signing config 'localDebug'
```

**原因**：`docs/app/build.gradle.kts` 里的 `localDebug` 签名配置写死了 Windows 路径
（`storeFile = File("D:\\.android\\debug.keystore")`）。这是仓库最初在 Windows 上开发时留下的，
在 Linux/macOS 或没有 `D:\` 盘的环境下必然失败。

**当前状态**：`Unit tests` 在 CI 上是**通过**的（1089 个测试全绿）；只有 APK 打包这一步受限。

**修复方向**（二选一）：

1. **删掉 `localDebug` 这个 signingConfig**，让 `debug` 构建使用 AGP 默认的 debug 签名
   （自动生成 `~/.android/debug.keystore`，跨平台可用）；
2. 或改成可配置：`storeFile = System.getenv("DEBUG_KEYSTORE")?.let { file(it) }`，
   CI 里通过 secret 注入。

**本地构建不受影响**（Windows 上有 `D:\.android\debug.keystore`）。