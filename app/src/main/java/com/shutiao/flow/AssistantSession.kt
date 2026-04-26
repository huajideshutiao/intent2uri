package com.shutiao.flow

import android.annotation.SuppressLint
import android.app.assist.AssistStructure
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.service.voice.VoiceInteractionSession
import android.text.Editable
import android.text.TextWatcher
import android.util.Patterns
import android.view.ContextThemeWrapper
import android.view.GestureDetector
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.hypot

class AssistantSession(context: Context) : VoiceInteractionSession(context) {
    private val themedContext = ContextThemeWrapper(context, R.style.AppTheme)
    private val imm by lazy { context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager }
    private var assistStructure: AssistStructure? = null
    private lateinit var input: EditText
    private lateinit var overlayContainer: FrameLayout
    private lateinit var selectedIcon: ImageView
    private lateinit var assistantRoot: LinearLayout
    private lateinit var btnExtractDone: Button
    private lateinit var urlBar: TextView
    private lateinit var rootContainer: View
    private lateinit var sideBar: View

    private var currentLink: OpenLink? = null
    private val selectedTexts = mutableSetOf<String>()
    private var isFinishing = false
    private var isSliding = false
    private var lastX = 0f
    private var lastY = 0f

    private val gestureDetector = GestureDetector(themedContext, object : GestureDetector.SimpleOnGestureListener() {
        override fun onSingleTapUp(e: MotionEvent): Boolean {
            findChildAt(e.x, e.y)?.let { v -> setViewSelected(v, !v.isSelected, v.tag as String) }
            return true
        }
    })

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreateContentView(): View =
        LayoutInflater.from(themedContext).inflate(R.layout.assistant_session, FrameLayout(themedContext), false)
            .apply {
                rootContainer = findViewById(R.id.root_container)
                sideBar = findViewById(R.id.side_bar)
                assistantRoot = findViewById(R.id.assistant_root)
                overlayContainer = findViewById(R.id.overlay_container)
                input = findViewById(R.id.assistant_input)
                selectedIcon = findViewById(R.id.selected_icon)
                btnExtractDone = findViewById(R.id.btn_extract_done_float)
                urlBar = findViewById(R.id.url_bar)

                viewTreeObserver.addOnGlobalLayoutListener {
                    val r = Rect().apply { getWindowVisibleDisplayFrame(this) }
                    val keypadHeight = rootView.height - r.bottom
                    assistantRoot.translationY =
                        if (keypadHeight > rootView.height * 0.15) -keypadHeight.toFloat() else 0f
                }

                rootContainer.setOnClickListener { safeFinish() }
                overlayContainer.setOnTouchListener { v, e ->
                    if (e.action == MotionEvent.ACTION_DOWN) {
                        lastX = e.x; lastY = e.y; isSliding = false
                    }
                    if (e.action == MotionEvent.ACTION_MOVE) {
                        if (!isSliding && (hypot((e.x - lastX).toDouble(), (e.y - lastY).toDouble()) > 5)) isSliding =
                            true
                        if (isSliding) findChildAt(e.x, e.y)?.let {
                            if (!it.isSelected) {
                                setViewSelected(
                                    it, true, it.tag as String
                                ); v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            }
                        }
                    }
                    if (e.action == MotionEvent.ACTION_UP && !isSliding) v.performClick()
                    gestureDetector.onTouchEvent(e)
                    true
                }

                val savedId = App.sharedPreferences.getString("selected_link_id", null)
                currentLink = if (savedId == "none") null
                else OpenLink.datas.find { it.id == savedId } ?: OpenLink.datas.firstOrNull()
                if (currentLink != null) {
                    currentLink?.loadIconAsync(context, selectedIcon)
                } else {
                    selectedIcon.setImageResource(android.R.drawable.ic_menu_search)
                }
                selectedIcon.setOnLongClickListener {
                    if (currentLink != null) {
                        currentLink = null
                        App.sharedPreferences.edit().putString("selected_link_id", "none").apply()
                        selectedIcon.setImageResource(android.R.drawable.ic_menu_search)
                    } else {
                        val key = input.text.toString()
                        if (key.isNotEmpty()) {
                            var matched = false
                            OpenLink.datas.forEach {
                                if (it.matchRule.isNotEmpty() && key.contains(Regex(it.matchRule))) {
                                    it.start(key)
                                    matched = true
                                }
                            }
                            if (matched) finish()
                        }
                    }
                    true
                }
                setupIconRow(findViewById(R.id.icon_row))

                input.setOnEditorActionListener { _, id, event ->
                    if (id == EditorInfo.IME_ACTION_SEARCH || id == EditorInfo.IME_ACTION_GO ||
                        (event != null && event.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)
                    ) {
                        val key = input.text.toString()
                        if (key.isNotEmpty()) {
                            OpenLink.smartSearch(context, key, currentLink)
                            finish()
                        }
                        true
                    } else false
                }

                findViewById<Button>(R.id.btn_expand).setOnClickListener {
                    sideBar.visibility = if (sideBar.visibility == View.VISIBLE) View.GONE else View.VISIBLE
                }
                findViewById<Button>(R.id.btn_settings).setOnClickListener {
                    context.startActivity(
                        Intent(
                            context, JumpManageActivity::class.java
                        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    ); finish()
                }
                findViewById<Button>(R.id.btn_extract_text).setOnClickListener { extractText() }
                btnExtractDone.setOnClickListener {
                    selectedTexts.joinToString(" ").takeIf { it.isNotEmpty() }
                        ?.let { input.setText(it); input.setSelection(it.length) }
                    toggleExtractMode(false)
                }

                urlBar.setOnClickListener {
                    val key = input.text.toString()
                    if (key.isNotEmpty()) {
                        OpenLink.smartSearch(context, key, null)
                        finish()
                    }
                }
                input.addTextChangedListener(object : TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                        val text = s?.toString() ?: ""
                        if (Patterns.WEB_URL.matcher(text).matches()) {
                            urlBar.text = "直接打开: $text"
                            urlBar.visibility = View.VISIBLE
                        } else {
                            urlBar.visibility = View.GONE
                        }
                    }

                    override fun afterTextChanged(s: Editable?) {}
                })
                input.setText(App.sharedPreferences.getString("last_input", ""))
            }

    override fun onBackPressed() = safeFinish()

    private fun safeFinish() {
        if (!isFinishing) {
            isFinishing = true; imm.hideSoftInputFromWindow(input.windowToken, 0); finish()
        }
    }

    private fun toggleExtractMode(enabled: Boolean) {
        if (enabled) urlBar.visibility = View.GONE
        rootContainer.setBackgroundColor(if (enabled) Color.TRANSPARENT else 0x80000000.toInt())
        overlayContainer.visibility = if (enabled) View.VISIBLE else View.GONE
        btnExtractDone.visibility = if (enabled) View.VISIBLE else View.GONE
        assistantRoot.visibility = if (enabled) View.GONE else View.VISIBLE
        if (enabled) {
            sideBar.visibility = View.GONE; imm.hideSoftInputFromWindow(input.windowToken, 0)
        } else {
            overlayContainer.removeAllViews(); selectedTexts.clear(); input.requestFocus(); imm.showSoftInput(input, 0)
        }
    }

    private fun setupIconRow(row: LinearLayout) {
        val inflater = LayoutInflater.from(themedContext)
        OpenLink.datas.filter { it.showInAssistant }.forEach { link ->
            inflater.inflate(R.layout.item_assistant_icon, row, false).apply {
                findViewById<ImageView>(R.id.icon).let { link.loadIconAsync(themedContext, it) }
                findViewById<TextView>(R.id.name).text = link.name
                setOnClickListener { link.start(input.text.toString()); finish() }
                setOnLongClickListener {
                    currentLink = link
                    App.sharedPreferences.edit().putString("selected_link_id", link.id).apply()
                    currentLink?.loadIconAsync(context, selectedIcon)
                    true
                }
                row.addView(this)
            }
        }
    }

    override fun onHandleAssist(state: AssistState) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) assistStructure = state.assistStructure
    }

    private fun extractText() {
        val structure = assistStructure ?: return
        toggleExtractMode(true)
        for (i in 0 until structure.windowNodeCount) createVisualOverlay(
            structure.getWindowNodeAt(i).rootViewNode, 0, 0
        )
    }

    private fun createVisualOverlay(node: AssistStructure.ViewNode, ox: Int, oy: Int) {
        val (nx, ny) = ox + node.left to oy + node.top
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

    private fun setViewSelected(v: View, sel: Boolean, txt: String) {
        if (v.isSelected == sel) return
        v.isSelected = sel
        v.setBackgroundResource(if (sel) R.drawable.text_rect_bg_selected else R.drawable.text_rect_bg)
        if (sel) selectedTexts += txt else selectedTexts -= txt
    }

    private fun findChildAt(x: Float, y: Float) =
        (overlayContainer.childCount - 1 downTo 0).asSequence().map { overlayContainer.getChildAt(it) }
            .firstOrNull { x >= it.left && x <= it.right && y >= it.top && y <= it.bottom }

    override fun onShow(args: Bundle?, flags: Int) {
        super.onShow(args, flags); isFinishing = false; input.requestFocus()
        args?.getString("share_text")?.let { input.setText(it); input.setSelection(it.length) }
        input.postDelayed({ if (!isFinishing) imm.showSoftInput(input, 0) }, 100)
    }

    override fun onHide() {
        super.onHide(); if (::input.isInitialized) App.sharedPreferences.edit()
            .putString("last_input", input.text.toString()).apply()
    }
}
