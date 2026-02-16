package com.shutiao.flow

import android.app.Activity
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
class JumpEditActivity : Activity() {
    private val db by lazy { DbHelper.getInstance(this).writableDatabase}
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_jump_edit)
        val button = findViewById<Button>(R.id.start)
        val button1 = findViewById<Button>(R.id.save)
        val button2 = findViewById<Button>(R.id.delete)
        button2.visibility = Button.VISIBLE
        button.visibility = Button.VISIBLE
        val i1 = findViewById<EditText>(R.id.packageName)
        val i2 = findViewById<EditText>(R.id.activity)
        val i3 = findViewById<EditText>(R.id.extra_key)
        val i4 = findViewById<EditText>(R.id.extra_value)
        val i5 = findViewById<EditText>(R.id.matchRule)
        val i6 = findViewById<EditText>(R.id.replaceRule)
        val i7 = findViewById<EditText>(R.id.uri)
        val i = findViewById<EditText>(R.id.name)
        val show = findViewById<TextView>(R.id.show)
        val item = intent.extras?.getString("item", "")

        OpenLink.fromDb(db, item!!).apply {
            if (host == "") {
                show.text = "你可以通过 kkp://${item}/ 来打开这个快捷方式"
            }
            i.setText(name)
            i5.setText(host)
            i1.setText(pp)
            i2.setText(activity)
            i3.setText(keys)
            i4.setText(datas)
            i6.setText(change2)
            i7.setText(uri)
        }

        button1.setOnClickListener {
            OpenLink.toDb(
                OpenLink(
                    i.text.toString(),
                    i5.text.toString(),
                    i1.text.toString(),
                    i2.text.toString(),
                    i3.text.toString(),
                    i4.text.toString(),
                    i6.text.toString(),
                    i7.text.toString()
                ), db, item
            )
        }

        button.setOnClickListener {
            openLink(
                "test", OpenLink(
                    i.text.toString(),
                    i5.text.toString(),
                    i1.text.toString(),
                    i2.text.toString(),
                    i3.text.toString(),
                    i4.text.toString(),
                    i6.text.toString(),
                    i7.text.toString()
                )
            )
        }
        button2.setOnClickListener {
            db.delete("list", "id = ?", arrayOf(item))
            finish()
        }
    }
}