package com.shutiao.flow

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle


class JumpActivity : Activity() {
    private fun open(data: Uri) {
        when (data.scheme) {
            "kkp" -> {
                val authority = data.authority!!
                val keyWord = (data.path ?: "").drop(1)
                OpenLink.fromDb(authority).start(keyWord)
            }

            "http", "https" -> {
                val key = data.toString()
                val (idList, matchRuleList) = item("matchRule")
                for (i in matchRuleList.indices) {
                    if (matchRuleList[i].isNotEmpty() && key.contains(Regex(matchRuleList[i]))) {
                        OpenLink.fromDb(idList[i]).start(key)
                        return
                    }
                }
                startActivity(Intent(Intent.ACTION_VIEW, data).setPackage(App.sharedPreferences.getString("browser", "")))
            }
        }
        moveTaskToBack(true)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent?) {
        intent?.let { handleIntent(it) }
    }

    private fun handleIntent(intent: Intent) {
        when (intent.action) {
            Intent.ACTION_VIEW -> {
                intent.data?.let { open(it) }
            }

            Intent.ACTION_SEND -> {
                if (intent.type?.startsWith("text/") == true) {
                    val text = intent.getStringExtra(Intent.EXTRA_TEXT)
                    text?.let {
                        // 处理分享的文本内容
                        val uri = Uri.parse(it)
                        open(uri)
                    }
                }
            }
        }
    }
}