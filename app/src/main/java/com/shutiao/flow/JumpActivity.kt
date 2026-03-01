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
                startActivity(
                    Intent(Intent.ACTION_VIEW, uri)
                        .setPackage(App.sharedPreferences.getString("browser", ""))
                        .putExtras(intent.extras ?: Bundle())
                )
            }
        }
        finish()
    }

    override fun onCreate(bundle: Bundle?) {
        super.onCreate(bundle)
        open(intent)
    }

    override fun onNewIntent(intent: Intent?) {
        intent?.let { open(it) }
    }
}
