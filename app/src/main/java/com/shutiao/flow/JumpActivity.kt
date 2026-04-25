package com.shutiao.flow

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle

class JumpActivity : Activity() {
    private fun open(intent: Intent) {
        when (intent.action) {
            Intent.ACTION_VIEW -> {
                val uri = intent.data ?: return
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
                        val newIntent = Intent(Intent.ACTION_VIEW, uri).putExtras(intent.extras ?: Bundle())
                        val resolveInfo = packageManager.resolveActivity(newIntent, PackageManager.MATCH_DEFAULT_ONLY)
                        if (resolveInfo == null || resolveInfo.activityInfo.packageName == packageName) {
                            startActivity(
                                newIntent.setPackage(App.sharedPreferences.getString("browser", ""))
                            )
                        } else startActivity(newIntent)
                    }
                }
            }

            else -> {
                val text = when (intent.action) {
                    Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)
                    Intent.ACTION_PROCESS_TEXT -> intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()
                    Intent.ACTION_WEB_SEARCH, Intent.ACTION_SEARCH -> intent.getStringExtra("query")
                    else -> ""
                }
                if (!text.isNullOrEmpty()) {
                    startService(Intent(this, AssistantService::class.java).apply {
                        action = "com.shutiao.flow.SHOW_ASSISTANT"
                        putExtra("share_text", text)
                    })
                }
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
