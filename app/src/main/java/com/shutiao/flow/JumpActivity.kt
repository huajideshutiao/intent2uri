package com.shutiao.flow

import android.app.Activity
import android.content.Intent
import android.os.Bundle

class JumpActivity : Activity() {
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
