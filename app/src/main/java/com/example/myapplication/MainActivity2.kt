package com.example.myapplication

import android.app.Activity
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
class MainActivity2 : Activity() {
    private val db by lazy { DbHelper(this).writableDatabase}
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main2)
        val button = findViewById<Button>(R.id.button)
        val button1 = findViewById<Button>(R.id.button1)
        val button2 = findViewById<Button>(R.id.button2)
        button2.visibility = Button.VISIBLE
        button.visibility = Button.VISIBLE
        val i1 = findViewById<EditText>(R.id.i1)
        val i2 = findViewById<EditText>(R.id.i2)
        val i3 = findViewById<EditText>(R.id.i3)
        val i4 = findViewById<EditText>(R.id.i4)
        val i5 = findViewById<EditText>(R.id.i5)
        val i6 = findViewById<EditText>(R.id.i6)
        val i7 = findViewById<EditText>(R.id.i7)
        val i = findViewById<EditText>(R.id.i)
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

    override fun finish() {
        db.close()
        super.finish()
    }
}