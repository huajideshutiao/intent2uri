package com.shutiao.flow

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
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
                //if (uri.scheme != "http" && uri.scheme != "https") return
                val intent = Intent(Intent.ACTION_VIEW, uri).putExtras(intent.extras ?: Bundle())
                val resolveInfo = packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
                if (resolveInfo == null || resolveInfo.activityInfo.packageName == packageName) {
                    startActivity(
                        intent.setPackage(App.sharedPreferences.getString("browser", ""))
                    )
                } else startActivity(intent)
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
