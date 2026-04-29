package com.shutiao.flow

import android.content.Intent
import android.os.Bundle
import android.service.voice.VoiceInteractionService
import android.service.voice.VoiceInteractionSession.SHOW_SOURCE_APPLICATION
import android.service.voice.VoiceInteractionSession.SHOW_WITH_ASSIST
import android.service.voice.VoiceInteractionSession.SHOW_WITH_SCREENSHOT

class AssistantService : VoiceInteractionService() {

    private var isReady = false
    private var pendingIntent: Intent? = null

    override fun onReady() {
        super.onReady()
        isReady = true
        pendingIntent?.let {
            handleIntent(it)
            pendingIntent = null
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "com.shutiao.flow.SHOW_ASSISTANT") {
            if (isReady) {
                handleIntent(intent)
            } else {
                pendingIntent = intent
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    private fun handleIntent(intent: Intent) {
        val text = intent.getStringExtra("share_text")
        val args = Bundle().apply {
            putString("share_text", text)
        }
        showSession(args, SHOW_SOURCE_APPLICATION or SHOW_WITH_ASSIST or SHOW_WITH_SCREENSHOT)
    }
}
