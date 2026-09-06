package com.shutiao.flow

import android.app.role.RoleManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.service.voice.VoiceInteractionService

/**
 * 修复"半坏"状态：role 仍在本应用手里，但系统实际生效的 VoiceInteractionService 已漂移
 * （澎湃/MIUI 重启后重新初始化语音子系统，默认选回超级小爱）。默认数字助手没有公开 API
 * 可以静默切换，只能借 Shizuku(shell)/root 执行命令强制系统重新绑定本应用的服务。
 */
object AssistantHealer {
    private const val SETTING_KEY = "voice_interaction_service"
    private const val RETRY_INTERVAL_MS = 30_000L
    private const val VERIFY_DELAY_MS = 1_000L

    private var lastRepairAt = 0L
    private var verifying = false

    /** 半坏 = 系统名单里助手仍是我，但运行态服务不是我。 */
    fun isHalfBroken(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        return isRoleHeld(context) && !isActive(context)
    }

    /** 完全健康 = role 在手且运行态服务也是本应用。 */
    fun isDefaultAssistant(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        return isRoleHeld(context) && isActive(context)
    }

    private fun isRoleHeld(context: Context): Boolean =
        context.getSystemService(RoleManager::class.java)
            ?.isRoleHeld(RoleManager.ROLE_ASSISTANT) == true

    /** 运行态服务是本应用（不看 role，JumpActivity 判断能否拉起面板时用）。 */
    fun isActive(context: Context): Boolean = VoiceInteractionService.isActiveService(
        context, ComponentName(context, AssistantService::class.java)
    )

    /** 被动检查：进程启动与助手入口各查一次；修复失败时按 [RETRY_INTERVAL_MS] 节流重试。 */
    fun checkAndRepair(context: Context) {
        if (!isHalfBroken(context)) return
        val now = System.currentTimeMillis()
        if (now - lastRepairAt < RETRY_INTERVAL_MS) return
        lastRepairAt = now
        ShellExec.exec(buildRepairCommand(context.applicationContext), requestPermissionIfNeeded = false)
    }

    /**
     * 主动修复（设置页按钮触发）：不节流；1 秒内的重复触发直接忽略（防连点）；
     * 命令为异步下发，[VERIFY_DELAY_MS] 后回查运行态，[onSettled] 回调 true 表示
     * 系统已重新绑定本应用，false 需调用方退回手动设置路径。
     */
    fun repairAndVerify(context: Context, onSettled: (Boolean) -> Unit) {
        if (verifying) return
        val app = context.applicationContext
        verifying = true
        lastRepairAt = System.currentTimeMillis()
        ShellExec.exec(buildRepairCommand(app), requestPermissionIfNeeded = false)
        Handler(Looper.getMainLooper()).postDelayed({
            verifying = false
            onSettled(isActive(app))
        }, VERIFY_DELAY_MS)
    }

    /** 三连修复：写回设置项兜底"被改写成小爱"；role 摘除+回挂强制系统重新绑定
     *（role 回挂失败最坏情况是助手置空，重新选择一次即可，不会比半坏更糟） */
    private fun buildRepairCommand(context: Context): String {
        val service = ComponentName(context, AssistantService::class.java)
        return buildString {
            append("settings put secure $SETTING_KEY '${service.flattenToString()}'\n")
            append("cmd role remove-role-holder --user 0 ${RoleManager.ROLE_ASSISTANT} ${context.packageName}\n")
            append("cmd role add-role-holder --user 0 ${RoleManager.ROLE_ASSISTANT} ${context.packageName}\n")
        }
    }
}
