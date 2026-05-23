package com.shutiao.flow

import android.annotation.SuppressLint
import android.app.assist.AssistStructure
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.service.voice.VoiceInteractionSession
import android.view.ContextThemeWrapper
import android.view.GestureDetector
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import kotlin.math.hypot

class AssistantSession(context: Context) : VoiceInteractionSession(context) {
    private val themedContext = ContextThemeWrapper(context, R.style.AppTheme)
    private lateinit var uiDelegate: AssistantUiDelegate
    private lateinit var root: View
    private lateinit var overlayContainer: FrameLayout
    private lateinit var btnExtractDone: Button
    private var assistStructure: AssistStructure? = null
    private val selectedTexts = mutableSetOf<String>()
    private var isFinishing = false

    override fun onCreateContentView(): View {
        root =
            LayoutInflater.from(themedContext).inflate(R.layout.assistant_session, FrameLayout(themedContext), false)
        overlayContainer = root.findViewById(R.id.overlay_container)
        btnExtractDone = root.findViewById(R.id.btn_extract_done_float)

        uiDelegate = AssistantUiDelegate(
            themedContext,
            root,
            object : AssistantUiDelegate.Callbacks {
                override fun onFinish() {
                    safeFinish()
                }

                override fun onExtractText() {
                    extractText()
                }

                override fun isExtractModeSupported(): Boolean = true
            },
        )

        setupExtractUi()
        return root
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupExtractUi() {
        var lastX = 0f
        var lastY = 0f
        var isSliding = false
        val gestureDetector = GestureDetector(themedContext, object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapUp(e: MotionEvent): Boolean {
                findChildAt(e.x, e.y)?.let { v -> setViewSelected(v, !v.isSelected, v.tag as String) }
                return true
            }
        })

        overlayContainer.setOnTouchListener { v, e ->
            if (e.action == MotionEvent.ACTION_DOWN) {
                lastX = e.x
                lastY = e.y
                isSliding = false
            }
            if (e.action == MotionEvent.ACTION_MOVE) {
                if (!isSliding && hypot((e.x - lastX).toDouble(), (e.y - lastY).toDouble()) > 5) isSliding = true
                if (isSliding) findChildAt(e.x, e.y)?.let {
                    if (!it.isSelected) {
                        setViewSelected(it, true, it.tag as String)
                        v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    }
                }
            }
            gestureDetector.onTouchEvent(e)
            true
        }

        btnExtractDone.setOnClickListener {
            selectedTexts.joinToString(" ").takeIf { it.isNotEmpty() }?.let {
                uiDelegate.input.setText(it)
                uiDelegate.input.setSelection(it.length)
            }
            toggleExtractMode(false)
        }
    }

    private fun extractText() {
        val structure = assistStructure ?: return
        uiDelegate.onPrepareFinish()
        toggleExtractMode(enabled = true)
        for (i in 0 until structure.windowNodeCount) createVisualOverlay(
            structure.getWindowNodeAt(i).rootViewNode,
            0,
            0
        )
    }

    private fun createVisualOverlay(node: AssistStructure.ViewNode, ox: Int, oy: Int) {
        val (nx, ny) = (ox + node.left) to (oy + node.top)
        if (node.visibility == View.VISIBLE) {
            node.text?.takeIf { it.isNotEmpty() }?.let { text ->
                overlayContainer.addView(View(context).apply {
                    layoutParams =
                        FrameLayout.LayoutParams(node.width, node.height).apply { leftMargin = nx; topMargin = ny }
                    setBackgroundResource(R.drawable.text_rect_bg); tag = text.toString()
                })
            }
            for (i in 0 until node.childCount) createVisualOverlay(node.getChildAt(i), nx, ny)
        }
    }

    private fun toggleExtractMode(enabled: Boolean) {
        overlayContainer.visibility = if (enabled) View.VISIBLE else View.GONE
        btnExtractDone.visibility = if (enabled) View.VISIBLE else View.GONE
        root.setBackgroundColor(if (enabled) 0 else 0x80000000.toInt())
        uiDelegate.setUiVisible(!enabled)
        if (!enabled) {
            overlayContainer.removeAllViews()
            selectedTexts.clear()
            // 释放可能很大的结构数据，下次进入提取模式需要新 onHandleAssist 回调重新拿到
            assistStructure = null
        }
    }

    private fun setViewSelected(v: View, sel: Boolean, txt: String) {
        if (v.isSelected == sel) return
        v.isSelected = sel
        v.setBackgroundResource(if (sel) R.drawable.text_rect_bg_selected else R.drawable.text_rect_bg)
        if (sel) selectedTexts += txt else selectedTexts -= txt
    }

    private fun findChildAt(x: Float, y: Float) = (overlayContainer.childCount - 1 downTo 0).asSequence()
        .map { overlayContainer.getChildAt(it) }
        .firstOrNull { x >= it.left && x <= it.right && y >= it.top && y <= it.bottom }

    override fun onHandleAssist(state: AssistState) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) assistStructure = state.assistStructure
    }

    override fun onShow(args: Bundle?, flags: Int) {
        super.onShow(args, flags)
        isFinishing = false
        uiDelegate.onShow(args?.getString("share_text"))
    }

    private fun safeFinish() {
        if (!isFinishing) {
            isFinishing = true
            uiDelegate.onPrepareFinish()
            finish()
        }
    }

    override fun onBackPressed() {
        if (overlayContainer.visibility == View.VISIBLE) {
            toggleExtractMode(false)
        } else {
            safeFinish()
        }
    }
}
