package com.shutiao.flow

import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.util.Patterns
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewTreeObserver
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

class AssistantUiDelegate(
    private val context: Context,
    private val root: View,
    private val callbacks: Callbacks
) {
    interface Callbacks {
        fun onFinish()
        fun onExtractText() {}
        fun isExtractModeSupported(): Boolean = false
    }

    private val imm by lazy { context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager }
    val input: EditText = root.findViewById(R.id.assistant_input)
    private val selectedIcon: ImageView = root.findViewById(R.id.selected_icon)
    private val assistantRoot: LinearLayout = root.findViewById(R.id.assistant_root)
    private val urlBar: TextView = root.findViewById(R.id.url_bar)
    private val sideBar: View = root.findViewById(R.id.side_bar)
    private var currentLink: OpenLink? = null

    private val keyboardLayoutListener = ViewTreeObserver.OnGlobalLayoutListener {
        val r = Rect().apply { assistantRoot.getWindowVisibleDisplayFrame(this) }
        val screenHeight = assistantRoot.rootView.height
        val keypadHeight = screenHeight - r.bottom
        assistantRoot.translationY = if (keypadHeight > screenHeight * 0.15) -keypadHeight.toFloat() else 0f
    }

    /** 窗口拿到焦点后才能弹输入法，所以先记下诉求，由焦点回调兑现 */
    private var awaitingWindowFocus = false
    private val windowFocusListener = ViewTreeObserver.OnWindowFocusChangeListener { hasFocus ->
        if (hasFocus && awaitingWindowFocus) {
            awaitingWindowFocus = false
            input.requestFocus()
            imm.showSoftInput(input, 0)
        }
    }

    init {
        setupBaseUi()
        setupSearchLogic()
        setupIconRow(root.findViewById(R.id.icon_row))
    }

    private fun setupBaseUi() {
        root.findViewById<View>(R.id.root_container).setOnClickListener { callbacks.onFinish() }

        val btnExtract = root.findViewById<View>(R.id.btn_extract_text)
        if (callbacks.isExtractModeSupported()) {
            btnExtract.setOnClickListener { callbacks.onExtractText() }
        } else {
            btnExtract.visibility = View.GONE
        }

        input.maxLines = App.sharedPreferences.getInt("assistant_max_lines", 5)

        assistantRoot.viewTreeObserver.addOnGlobalLayoutListener(keyboardLayoutListener)
        assistantRoot.viewTreeObserver.addOnWindowFocusChangeListener(windowFocusListener)

        root.findViewById<Button>(R.id.btn_expand).setOnClickListener {
            sideBar.visibility = if (sideBar.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }

        root.findViewById<Button>(R.id.btn_settings).setOnClickListener {
            context.startActivity(
                Intent(
                    context,
                    JumpManageActivity::class.java
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            callbacks.onFinish()
        }
    }

    private fun setupSearchLogic() {
        val savedId = App.sharedPreferences.getString("selected_link_id", null)
        currentLink = if (savedId == "none") null
        else OpenLink.datas.find { it.id == savedId } ?: OpenLink.datas.firstOrNull()

        refreshSelectedIcon()

        selectedIcon.setOnLongClickListener {
            if (currentLink != null) {
                currentLink = null
                App.sharedPreferences.edit().putString("selected_link_id", "none").apply()
                selectedIcon.setImageResource(android.R.drawable.ic_menu_search)
            } else {
                performSearch(input.text.toString(), null)
            }
            true
        }

        input.setOnEditorActionListener { _, id, event ->
            if (id == EditorInfo.IME_ACTION_SEARCH || id == EditorInfo.IME_ACTION_GO ||
                (event != null && event.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)
            ) {
                val targetLink = if (urlBar.visibility == View.VISIBLE) null else currentLink
                performSearch(input.text.toString(), targetLink)
                true
            } else false
        }

        urlBar.setOnClickListener { performSearch(input.text.toString(), null) }

        input.onTextChanged { text ->
            urlBar.visibility = if (Patterns.WEB_URL.matcher(text).matches()) {
                urlBar.text = context.getString(R.string.open_directly, text); View.VISIBLE
            } else View.GONE
        }
    }

    private fun setupIconRow(row: LinearLayout) {
        val inflater = LayoutInflater.from(context)
        OpenLink.datas.filter { it.showInAssistant }.forEach { link ->
            inflater.inflate(R.layout.item_icon_label, row, false).apply {
                findViewById<ImageView>(R.id.icon).let { link.loadIconAsync(context, it) }
                findViewById<TextView>(R.id.name).text = link.name
                setOnClickListener { performSearch(input.text.toString(), link) }
                setOnLongClickListener {
                    currentLink = link
                    App.sharedPreferences.edit().putString("selected_link_id", link.id).apply()
                    refreshSelectedIcon()
                    true
                }
                row.addView(this)
            }
        }
    }

    private fun refreshSelectedIcon() {
        if (currentLink != null) currentLink?.loadIconAsync(context, selectedIcon)
        else selectedIcon.setImageResource(android.R.drawable.ic_menu_search)
    }

    private fun performSearch(key: String, link: OpenLink?) {
        if (key.isNotEmpty()) {
            OpenLink.smartSearch(context, key, link)
            callbacks.onFinish()
        }
    }

    private fun focusAndShowKeyboard() {
        if (input.text.isNotEmpty()) input.selectAll()
        input.requestFocus()
        // 窗口未获焦时 showSoftInput 会被 IMM 以 "view is not served" 丢弃，改等焦点回调再弹
        if (root.hasWindowFocus()) imm.showSoftInput(input, 0) else awaitingWindowFocus = true
    }

    fun onShow(text: String?) {
        input.setText(text ?: App.sharedPreferences.getString("last_input", "") ?: "")
        focusAndShowKeyboard()
    }

    /** 收起输入法并留存草稿；面板可能稍后再次显示，故不在此注销布局监听 */
    fun onPrepareFinish() {
        App.sharedPreferences.edit().putString("last_input", input.text.toString()).apply()
        awaitingWindowFocus = false
        imm.hideSoftInputFromWindow(input.windowToken, 0)
    }

    /** 面板彻底销毁时注销监听 */
    fun release() {
        assistantRoot.viewTreeObserver.removeOnGlobalLayoutListener(keyboardLayoutListener)
        assistantRoot.viewTreeObserver.removeOnWindowFocusChangeListener(windowFocusListener)
    }

    fun setUiVisible(visible: Boolean) {
        assistantRoot.visibility = if (visible) View.VISIBLE else View.GONE
        if (visible) {
            sideBar.visibility = View.GONE
            focusAndShowKeyboard()
        }
    }
}
