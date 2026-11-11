package com.aster.companion.ui

/**
 * 协程未捕获异常的兜底：**记日志，不杀进程**。
 *
 * ## 为什么必须有它
 *
 * `viewModelScope` 的环境是 `SupervisorJob + Dispatchers.Main.immediate`。某个 `launch` 里
 * 抛出未捕获异常时，异常会交给**线程的未捕获处理器** → Android 直接杀掉进程 → **闪退**。
 * 实测就是这样：她发照片那条路上抛了一次 NPE，整个应用就消失了。
 *
 * **"一次操作失败"绝不该等于"应用消失"。** 用法是把本兜底挂到已有的 `launch` 上：
 *
 * ```kotlin
 * viewModelScope.launch(crashGuard) { … }   // 而不是 viewModelScope.launch { … }
 * ```
 *
 * 这样只有那一个协程失败，异常落进日志（tag `TongpinCrash`），主线程与界面继续可用；
 * 用户看到的最多是"这次没拍成"。
 *
 * ## 为什么不用 `Thread.setDefaultUncaughtExceptionHandler` 拦住
 *
 * 那样确实能"不闪退"，但**主线程的未捕获异常一旦不杀进程，主线程的 `Looper` 已经退出了**，
 * 应用会变成**界面卡死的僵尸**（用户还得手动杀），比闪退更糟。在协程层面接住则不会。
 *
 * `CrashLogRecorder`（进程级、写文件到手机）保留不动——它继续兜住 native 崩溃与主线程上
 * 非协程路径的异常。
 */
internal val crashGuard = kotlinx.coroutines.CoroutineExceptionHandler { _, error ->
    android.util.Log.e("TongpinCrash", "协程未捕获异常（已拦下，不闪退）", error)
}
