package com.shutiao.flow

import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.text.Editable
import android.text.TextWatcher
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
    private var globalLayoutListener: ViewTreeObserver.OnGlobalLayoutListener? = null

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

        val maxLines = App.sharedPreferences.getInt("assistant_max_lines", 5)
        input.maxLines = maxLines

        globalLayoutListener = ViewTreeObserver.OnGlobalLayoutListener {
            val r = Rect().apply { assistantRoot.getWindowVisibleDisplayFrame(this) }
            val screenHeight = assistantRoot.rootView.height
            val keypadHeight = screenHeight - r.bottom
            assistantRoot.translationY = if (keypadHeight > screenHeight * 0.15) -keypadHeight.toFloat() else 0f
        }
        assistantRoot.viewTreeObserver.addOnGlobalLayoutListener(globalLayoutListener)

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

        input.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val t = s?.toString() ?: ""
                urlBar.visibility = if (Patterns.WEB_URL.matcher(t).matches()) {
                    urlBar.text = context.getString(R.string.open_directly, t); View.VISIBLE
                } else View.GONE
            }

            override fun afterTextChanged(s: Editable?) {}
        })
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

    fun onShow(text: String?) {
        val finalGenText = text ?: App.sharedPreferences.getString("last_input", "") ?: ""
        input.setText(finalGenText)
        if (finalGenText.isNotEmpty()) {
            input.selectAll()
        }
        input.requestFocus()
        input.postDelayed({ imm.showSoftInput(input, 0) }, 100)
    }

    fun onPrepareFinish() {
        App.sharedPreferences.edit().putString("last_input", input.text.toString()).apply()
        imm.hideSoftInputFromWindow(input.windowToken, 0)
        globalLayoutListener?.let {
            assistantRoot.viewTreeObserver.removeOnGlobalLayoutListener(it)
            globalLayoutListener = null
        }
    }

    fun setUiVisible(visible: Boolean) {
        assistantRoot.visibility = if (visible) View.VISIBLE else View.GONE
        if (visible) {
            sideBar.visibility = View.GONE
            if (input.text.isNotEmpty()) {
                input.selectAll()
            }
            input.requestFocus()
            input.postDelayed({ imm.showSoftInput(input, 0) }, 100)
        }
    }
}
