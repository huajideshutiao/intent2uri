package com.shutiao.flow

import android.app.Activity
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher

class AssistantActivity : Activity() {
    private lateinit var uiDelegate: AssistantUiDelegate
    private var isFinishingAct = false
    private var backCallback: OnBackInvokedCallback? = null

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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val cb = OnBackInvokedCallback { safeFinish() }
            onBackInvokedDispatcher.registerOnBackInvokedCallback(
                OnBackInvokedDispatcher.PRIORITY_DEFAULT, cb
            )
            backCallback = cb
        }
    }

    private fun safeFinish() {
        if (!isFinishingAct) {
            isFinishingAct = true
            uiDelegate.onPrepareFinish()
            finish()
            @Suppress("DEPRECATION")
            overridePendingTransition(0, android.R.anim.fade_out)
        }
    }

    @Deprecated("Deprecated in Java")
    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) safeFinish()
        else super.onBackPressed()
    }

    override fun onDestroy() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            backCallback?.let { onBackInvokedDispatcher.unregisterOnBackInvokedCallback(it) }
            backCallback = null
        }
        super.onDestroy()
    }
}
