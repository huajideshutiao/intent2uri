package com.shutiao.flow

import android.content.Context
import android.util.AttributeSet
import android.view.KeyEvent
import android.widget.EditText

class BackEditText @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = android.R.attr.editTextStyle
) : EditText(context, attrs, defStyleAttr) {

    var onBackListener: (() -> Boolean)? = null

    override fun onKeyPreIme(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK && event?.action == KeyEvent.ACTION_UP) {
            if (onBackListener?.invoke() == true) {
                return true
            }
        }
        return super.onKeyPreIme(keyCode, event)
    }
}
