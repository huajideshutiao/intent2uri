package com.shutiao.flow

import android.app.Activity
import android.os.Bundle
import android.view.LayoutInflater

class AssistantActivity : Activity() {
    private lateinit var uiDelegate: AssistantUiDelegate
    private var isFinishingAct = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LayoutInflater.from(this).inflate(R.layout.assistant_session, null)
        setContentView(root)

        uiDelegate = AssistantUiDelegate(this, root, object : AssistantUiDelegate.Callbacks {
            override fun onFinish() {
                safeFinish()
            }
        })

        uiDelegate.onShow(intent.getStringExtra("share_text"))
    }

    private fun safeFinish() {
        if (!isFinishingAct) {
            isFinishingAct = true
            uiDelegate.onPrepareFinish()
            finish()
            overridePendingTransition(0, android.R.anim.fade_out)
        }
    }

    override fun onBackPressed() {
        safeFinish()
    }
}
