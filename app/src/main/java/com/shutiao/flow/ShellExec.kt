package com.shutiao.flow

import android.content.ComponentName
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import rikka.shizuku.Shizuku

/**
 * 特权命令下发的唯一通道：优先经 Shizuku 用户服务执行，Shizuku 不可用时回落 root。
 * [requestPermissionIfNeeded] 区分两种策略——true（用户主动触发的启动动作）在未授权时
 * 弹 Shizuku 授权框，按用户选择决定 bind 还是回落 root；false（静默自愈）不弹窗直接走 root。
 */
object ShellExec {
    fun exec(command: String, requestPermissionIfNeeded: Boolean) {
        try {
            val conn = object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                    IUserService.Stub.asInterface(binder).exec(command)
                    Shizuku.unbindUserService(App.args, this, false)
                }

                override fun onServiceDisconnected(name: ComponentName?) {}
            }
            if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                Shizuku.bindUserService(App.args, conn)
                return
            }
            if (!requestPermissionIfNeeded) {
                App.runRootCommand(command)
                return
            }
            Shizuku.addRequestPermissionResultListener(object : Shizuku.OnRequestPermissionResultListener {
                override fun onRequestPermissionResult(requestCode: Int, grantResult: Int) {
                    if (grantResult == PackageManager.PERMISSION_GRANTED) Shizuku.bindUserService(App.args, conn)
                    else App.runRootCommand(command)
                    Shizuku.removeRequestPermissionResultListener(this)
                }
            })
            Shizuku.requestPermission(0)
        } catch (_: Exception) {
            App.runRootCommand(command)
        }
    }
}
