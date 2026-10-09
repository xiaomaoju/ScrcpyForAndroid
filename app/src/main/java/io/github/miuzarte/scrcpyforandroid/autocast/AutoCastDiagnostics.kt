package io.github.miuzarte.scrcpyforandroid.autocast

/** A bounded startup trace; no pairing code, address, screen content or device serial is collected. */
internal class AutoCastDiagnostics(private val persist: (String) -> Unit = {}) {
    private val entries = ArrayDeque<String>()
    private var header = ""
    private var startedNanos = 0L
    var step = AutoCastStep.C1
        private set

    @Synchronized fun begin(device: String) {
        entries.clear()
        startedNanos = System.nanoTime()
        header = "Flip5 AutoCast\n${device.take(500)}"
        enter(AutoCastStep.C1)
    }

    @Synchronized fun enter(value: AutoCastStep) {
        step = value
        note("step", "${value.name} ${value.label}")
    }

    @Synchronized fun note(label: String, value: String) {
        val elapsedMs = (System.nanoTime() - startedNanos) / 1_000_000
        entries.addLast("[+${elapsedMs}ms] $label: ${value.take(900)}")
        while (entries.size > 40) entries.removeFirst()
        persist(report())
    }

    @Synchronized fun report(): String = header + "\n" + entries.joinToString("\n").takeLast(12_000 - header.length - 1)

    @Synchronized fun failure(error: Exception, timedOut: Boolean): String {
        val message = "${step.name}：${step.label}${if (timedOut) "超时" else "失败"}。"
        note("failure", "$message ${error.javaClass.simpleName}: ${error.message.orEmpty()}")
        return if (timedOut) "$message 请点“复制诊断”保留本次检查结果。"
        else "$message ${error.message.orEmpty().take(180)}"
    }

    companion object {
        /** Allowlisted fields only: never persist raw dumpsys names, unique IDs or addresses. */
        internal fun displayDumpSummary(dump: String): String = dump.lineSequence()
            .filter { it.contains("DisplayDeviceInfo{") || it.contains("mBaseDisplayInfo=DisplayInfo{") }
            .take(6)
            .map { line ->
                val kind = if (line.contains("DisplayDeviceInfo{")) "device" else "logical"
                val fields = listOfNotNull(
                    Regex("\\bdisplayId[ =]+\\d+").find(line)?.value,
                    Regex("\\b\\d+ x \\d+\\b").find(line)?.value,
                    Regex("\\bstate[ =]+[A-Z_]+").find(line)?.value,
                    Regex("\\btype[ =]+[A-Z_]+").find(line)?.value,
                ) + Regex("\\bFLAG_(?:PRIVATE|OWN_CONTENT_ONLY|TRUSTED)\\b").findAll(line).map { it.value }.distinct().toList()
                // Six records plus separators fit within note()'s 900-character limit.
                "$kind: ${fields.joinToString(", ").ifEmpty { "fields unavailable" }}".take(140)
            }.joinToString("; ").ifEmpty { "no recognized display records" }
    }
}

internal enum class AutoCastStep(val label: String) {
    C1("连接本机"), P1("读取主界面配置"), R1("恢复上次屏幕状态"),
    F1("读取双屏模式"), F2("读取折叠状态"), F3("启动双屏守卫"), F4("等待双屏模式生效"),
    W1("等待外屏出现"), W2("重新打开外屏窗口"), D1("检查内外屏状态"), D2("读取内屏捕获源"),
    V1("启动视频服务"), V2("等待视频首帧"), M1("监测投屏状态"),
}
