package com.shutiao.flow

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings

class JumpActivity : Activity() {

    private fun isAssistant(context: Context): Boolean {
        val assistant = Settings.Secure.getString(context.contentResolver, "assistant")
        if (assistant.isNullOrEmpty()) return false
        val cn = ComponentName.unflattenFromString(assistant)
        return cn?.packageName == context.packageName
    }

    private fun open(intent: Intent) {
        when (intent.action) {
            Intent.ACTION_VIEW -> {
                val uri = intent.data ?: return
                when (uri.scheme) {
                    "kkp" -> OpenLink.smartSearch(
                        this, (uri.path ?: "").drop(1), OpenLink.datas.find { it.id == uri.authority }
                    )

                    else -> OpenLink.smartSearch(
                        this, uri.toString(), null, Intent(Intent.ACTION_VIEW, uri).putExtras(intent.extras ?: Bundle())
                    )
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
                    if (isAssistant(this)) {
                        startService(Intent(this, AssistantService::class.java).apply {
                            action = "com.shutiao.flow.SHOW_ASSISTANT"
                            putExtra("share_text", text)
                        })
                    } else {
                        startActivity(Intent(this, AssistantActivity::class.java).apply {
                            putExtra("share_text", text)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        })
                    }
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
