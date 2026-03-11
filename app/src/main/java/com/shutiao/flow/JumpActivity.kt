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
                if (uri.scheme != "http" && uri.scheme != "https") return
                //查询系统推荐的打开应用，没有则使用默认浏览器打开
                val intent = Intent(Intent.ACTION_VIEW, uri)
                val bestPackage =
                    packageManager.queryIntentActivities(intent, 0).firstOrNull()?.activityInfo?.packageName
                startActivity(
                    intent.setPackage(
                        if (bestPackage != packageName) bestPackage
                        else App.sharedPreferences.getString("browser", "")
                    ).putExtras(this.intent.extras ?: Bundle())
                        //.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
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
