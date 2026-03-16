package com.shutiao.flow

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle

class JumpActivity : Activity() {
    private fun open(intent: Intent) {
        val uri = when (intent.action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)?.let { Uri.parse(it) }
            else -> null
        } ?: return
        when (uri.scheme) {
            "kkp" -> OpenLink.datas.first { it.id == uri.authority!! }.start((uri.path ?: "").drop(1))
            else -> {
                val key = uri.toString()
                OpenLink.datas.find {
                    it.matchRule.isNotEmpty() && key.contains(Regex(it.matchRule))
                }?.let {
                    it.start(key)
                    return
                }
                
                val viewIntent = Intent(Intent.ACTION_VIEW, uri).putExtras(intent.extras ?: Bundle())
                val resolveInfo = packageManager.resolveActivity(viewIntent, 0)
                val targetPackage = resolveInfo?.activityInfo?.packageName
                try {
                    // 判断是否为 null、自己、或者是系统的意图解析器
                    if (targetPackage == null || targetPackage == packageName || 
                        targetPackage == "android" || targetPackage == "com.android.intentresolver") {
                        
                        val browserPackage = App.sharedPreferences.getString("browser", "")
                        if (!browserPackage.isNullOrEmpty()) {
                            startActivity(viewIntent.setPackage(browserPackage))
                        } else {
                            // 如果没设默认浏览器，手动寻找除了自己以外的第一个处理者，防止死循环
                            val otherApp = packageManager.queryIntentActivities(viewIntent, 0)
                                .firstOrNull { it.activityInfo.packageName != packageName }
                            
                            if (otherApp != null) {
                                startActivity(viewIntent.setPackage(otherApp.activityInfo.packageName))
                            } else {
                                // 实在找不到，清除 package 尝试让系统处理（可能会弹出选择器）
                                startActivity(viewIntent.setPackage(null))
                            }
                        }
                    } else {
                        startActivity(viewIntent)
                    }
                } catch (_: Exception) { }
            }
        }
    }

    override fun onCreate(bundle: Bundle?) {
        super.onCreate(bundle)
        open(intent)
        finish()
    }

    override fun onNewIntent(intent: Intent?) {
        intent?.let { open(it) }
        finish()
    }
}
