package com.shutiao.flow

import android.app.assist.AssistStructure
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.service.voice.VoiceInteractionSession
import android.text.TextUtils
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewTreeObserver
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class AssistantSession(context: Context) : VoiceInteractionSession(context) {
    private val themedContext: Context = ContextThemeWrapper(context, R.style.AppTheme)

    private var assistStructure: AssistStructure? = null
    private lateinit var iconRow: LinearLayout
    private lateinit var sideBar: LinearLayout
    private lateinit var input: EditText
    private lateinit var overlayContainer: FrameLayout
    private lateinit var selectedIcon: ImageView
    private lateinit var rootContainer: View
    private lateinit var assistantRoot: LinearLayout
    private lateinit var btnExtractDone: Button

    private var currentSelectedLink: OpenLink? = null

    override fun onCreate() {
        super.onCreate()
        window?.window?.apply {
            setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
            setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
        }
    }

    override fun onComputeInsets(outInsets: Insets) {
        super.onComputeInsets(outInsets)
        // 核心修改：设置全屏拦截触摸，防止点击事件透传到下方应用
        // 同时也保证了点击空白背景区域（root_container）可以触发退出逻辑
        outInsets.touchableInsets = Insets.TOUCHABLE_INSETS_FRAME
    }

    private lateinit var sessionView: View
    private val selectedTexts = mutableListOf<String>()

    override fun onCreateContentView(): View {
        val inflater = themedContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as android.view.LayoutInflater
        val parent = FrameLayout(themedContext)
        val view = inflater.inflate(R.layout.assistant_session, parent, false)
        sessionView = view
        rootContainer = view.findViewById(R.id.root_container)
        assistantRoot = view.findViewById(R.id.assistant_root)
        overlayContainer = view.findViewById(R.id.overlay_container)
        input = view.findViewById(R.id.assistant_input)

        iconRow = view.findViewById(R.id.icon_row)
        sideBar = view.findViewById(R.id.side_bar)
        selectedIcon = view.findViewById(R.id.selected_icon)

        val btnExpand = view.findViewById<Button>(R.id.btn_expand)
        val btnSettings = view.findViewById<Button>(R.id.btn_settings)
        val btnExtract = view.findViewById<Button>(R.id.btn_extract_text)
        btnExtractDone = view.findViewById(R.id.btn_extract_done_float)

        val layoutListener = object : ViewTreeObserver.OnGlobalLayoutListener {
            private val r = Rect()
            override fun onGlobalLayout() {
                view.getWindowVisibleDisplayFrame(r)
                val screenHeight = view.rootView.height
                val keypadHeight = screenHeight - r.bottom
                assistantRoot.translationY = if (keypadHeight > screenHeight * 0.15) -keypadHeight.toFloat() else 0f
            }
        }
        view.viewTreeObserver.addOnGlobalLayoutListener(layoutListener)

        rootContainer.setOnClickListener { finish() }
        assistantRoot.setOnClickListener { }
        sideBar.setOnClickListener { }

        val lastSelectedId = App.sharedPreferences.getString("selected_link_id", "")
        currentSelectedLink = OpenLink.datas.find { it.id == lastSelectedId } ?: OpenLink.datas.firstOrNull()
        updateSelectedToolUI()

        setupIconRow()

        val lastInput = App.sharedPreferences.getString("last_input", "")
        input.setText(lastInput)
        if (!lastInput.isNullOrEmpty()) {
            input.setSelection(lastInput.length)
        }

        input.setOnEditorActionListener { _, actionId, event ->
            val isEnterDown =
                event != null && event.action == KeyEvent.ACTION_DOWN && event.keyCode == KeyEvent.KEYCODE_ENTER
            if (actionId == EditorInfo.IME_ACTION_SEARCH || isEnterDown) {
                val text = input.text.toString()
                if (text.isNotEmpty()) {
                    currentSelectedLink?.start(text)
                    finish()
                }
                true
            } else false
        }

        btnExpand.setOnClickListener {
            sideBar.visibility = if (sideBar.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }

        btnSettings.setOnClickListener {
            val intent = Intent(context, JumpManageActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            finish()
        }

        btnExtract.setOnClickListener { extractText() }

        btnExtractDone.setOnClickListener {
            val combinedText = selectedTexts.joinToString(" ")
            if (combinedText.isNotEmpty()) {
                input.setText(combinedText)
                input.setSelection(combinedText.length)
            }
            exitExtractMode()
        }

        // 核心修改：识屏界面拦截点击事件，防止误退出
        overlayContainer.setOnClickListener {
            /* 消费点击事件，在文字提取模式下不触发 rootContainer 的 finish() */
        }

        return view
    }

    private var isFinishing = false

    override fun onBackPressed() {
        safeFinish()
    }

    private fun safeFinish() {
        if (isFinishing) return
        isFinishing = true
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        input.clearFocus()
        imm.hideSoftInputFromWindow(input.windowToken, 0)
        finish()
    }

    private fun exitExtractMode() {
        rootContainer.setBackgroundColor(0x80000000.toInt())
        overlayContainer.removeAllViews()
        overlayContainer.visibility = View.GONE
        btnExtractDone.visibility = View.GONE
        assistantRoot.visibility = View.VISIBLE
        selectedTexts.clear()

        input.requestFocus()
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(input, 0)
    }

    private fun updateSelectedToolUI() {
        val link = currentSelectedLink ?: return
        link.loadIconAsync(context, selectedIcon)
    }

    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)
        isFinishing = false
        input.requestFocus()
        args?.getString("share_text")?.let { text ->
            input.setText(text)
            input.setSelection(text.length)
        }
        input.postDelayed({
            if (!isFinishing) {
                val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showSoftInput(input, 0)
            }
        }, 200)
    }

    override fun onHide() {
        super.onHide()
        if (::input.isInitialized) {
            App.sharedPreferences.edit().putString("last_input", input.text.toString()).apply()
        }
    }

    private fun setupIconRow() {
        iconRow.removeAllViews()
        val density = themedContext.resources.displayMetrics.density
        val typedValue = android.util.TypedValue()
        themedContext.theme.resolveAttribute(android.R.attr.textColorPrimary, typedValue, true)
        OpenLink.datas.filter { it.showInAssistant }.forEach { link ->
            val itemLayout = LinearLayout(themedContext)
            itemLayout.orientation = LinearLayout.VERTICAL
            itemLayout.gravity = Gravity.CENTER
            val layoutParams = LinearLayout.LayoutParams(
                (50 * density).toInt(),
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            layoutParams.setMargins(0, 0, (8 * density).toInt(), 0)
            itemLayout.layoutParams = layoutParams

            val iconSize = (35 * density).toInt()
            val imageView = ImageView(themedContext)
            imageView.layoutParams = LinearLayout.LayoutParams(iconSize, iconSize)
            imageView.scaleType = ImageView.ScaleType.FIT_CENTER

            link.loadIconAsync(themedContext, imageView)

            val textView = TextView(themedContext)
            textView.text = link.name
            textView.textSize = 11f
            textView.gravity = Gravity.CENTER
            textView.setPadding(0, (4 * density).toInt(), 0, 0)
            textView.maxLines = 1
            textView.ellipsize = TextUtils.TruncateAt.END

            itemLayout.addView(imageView)
            itemLayout.addView(textView)

            itemLayout.setOnClickListener {
                link.start(input.text.toString())
                finish()
            }

            itemLayout.setOnLongClickListener {
                currentSelectedLink = link
                App.sharedPreferences.edit().putString("selected_link_id", link.id).apply()
                updateSelectedToolUI()
                Toast.makeText(context, "已设为当前默认项: ${link.name}", Toast.LENGTH_SHORT).show()
                true
            }
            iconRow.addView(itemLayout)
        }
    }

    override fun onHandleAssist(state: AssistState) {
        super.onHandleAssist(state)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            this.assistStructure = state.assistStructure
        }
    }

    private fun extractText() {
        val structure = assistStructure ?: return
        rootContainer.setBackgroundColor(Color.TRANSPARENT)
        overlayContainer.removeAllViews()
        overlayContainer.visibility = View.VISIBLE
        btnExtractDone.visibility = View.VISIBLE
        selectedTexts.clear()

        assistantRoot.visibility = View.GONE
        sideBar.visibility = View.GONE

        // 自动收起输入法
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(input.windowToken, 0)

        for (i in 0 until structure.windowNodeCount) {
            createVisualOverlay(structure.getWindowNodeAt(i).rootViewNode, 0, 0)
        }
    }

    private fun createVisualOverlay(node: AssistStructure.ViewNode, offsetX: Int, offsetY: Int) {
        val nodeX = offsetX + node.left
        val nodeY = offsetY + node.top
        if (node.visibility == View.VISIBLE) {
            val text = node.text
            if (!text.isNullOrEmpty()) {
                val rectView = View(context)
                val params = FrameLayout.LayoutParams(node.width, node.height)
                params.leftMargin = nodeX
                params.topMargin = nodeY
                rectView.layoutParams = params
                rectView.setBackgroundResource(R.drawable.text_rect_bg)

                var isSelected = false
                rectView.setOnClickListener {
                    isSelected = !isSelected
                    if (isSelected) {
                        rectView.setBackgroundResource(R.drawable.text_rect_bg_selected)
                        selectedTexts.add(text.toString())
                    } else {
                        rectView.setBackgroundResource(R.drawable.text_rect_bg)
                        selectedTexts.remove(text.toString())
                    }
                }
                overlayContainer.addView(rectView)
            }
            for (i in 0 until node.childCount) {
                createVisualOverlay(node.getChildAt(i), nodeX, nodeY)
            }
        }
    }
}
