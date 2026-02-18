package com.shutiao.flow

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle


class JumpActivity : Activity() {
    private fun open(intent: Intent) {
        val uri = when (intent.action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> {
                if (intent.type?.startsWith("text/") == true) {
                    val text = intent.getStringExtra(Intent.EXTRA_TEXT)
                    text?.let { Uri.parse(it) }
                } else {
                    null
                }
            }
            else -> null
        } ?: return

        when (uri.scheme) {
            "kkp" -> {
                val authority = uri.authority!!
                val keyWord = (uri.path ?: "").drop(1)
                OpenLink.datas.first { it.id == authority }.start(keyWord)
            }

            "http", "https" -> {
                val key = uri.toString()
                val (idList, matchRuleList) = item("matchRule")
                for (i in matchRuleList.indices) {
                    if (matchRuleList[i].isNotEmpty() && key.contains(Regex(matchRuleList[i]))) {
                        OpenLink.datas.first { it.id == idList[i] }.start(key)
                        return
                    }
                }
                startActivity(intent)
            }
        }
        moveTaskToBack(true)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        open(intent)
    }

    override fun onNewIntent(intent: Intent?) {
        intent?.let { open(it) }
    }
}
