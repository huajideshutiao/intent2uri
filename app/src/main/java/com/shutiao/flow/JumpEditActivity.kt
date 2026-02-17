package com.shutiao.flow

import android.app.Activity
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView

class JumpEditActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_jump_edit)
        val start = findViewById<Button>(R.id.start)
        val save = findViewById<Button>(R.id.save)
        val delete = findViewById<Button>(R.id.delete)
        delete.visibility = Button.VISIBLE
        start.visibility = Button.VISIBLE

        val name = findViewById<EditText>(R.id.name)
        val description = findViewById<EditText>(R.id.description)
        val packageName = findViewById<EditText>(R.id.packageName)
        val activity = findViewById<EditText>(R.id.activity)
        val uri = findViewById<EditText>(R.id.uri)
        val matchRule = findViewById<EditText>(R.id.matchRule)
        val replaceRule = findViewById<EditText>(R.id.replaceRule)
        val extraKey = findViewById<EditText>(R.id.extra_key)
        val extraValue = findViewById<EditText>(R.id.extra_value)

        val show = findViewById<TextView>(R.id.show)

        val id = intent.extras?.getString("id", "")

        // 如果是新建项目，隐藏删除按钮
        if (id.isNullOrEmpty()) delete.visibility = Button.GONE
        else {
            OpenLink.datas.first { it.id == id }.apply {
                show.text = "你可以通过 kkp://${id}/ 来打开这个快捷方式"
                name.setText(this.name)
                description.setText(this.description)
                matchRule.setText(this.matchRule)
                replaceRule.setText(this.replaceRule)
                packageName.setText(this.packageName)
                activity.setText(this.activity)
                uri.setText(this.uri)
                extraKey.setText(this.extraKey)
                extraValue.setText(this.extraValue)
            }
        }

        save.setOnClickListener {
            OpenLink(
                name.text.toString(),
                description.text.toString(),
                matchRule.text.toString(),
                replaceRule.text.toString(),
                packageName.text.toString(),
                activity.text.toString(),
                uri.text.toString(),
                extraKey.text.toString(),
                extraValue.text.toString()
            ).save(id)
            intent.putExtra("id", id)
            setResult(1, intent)
        }

        start.setOnClickListener {
            OpenLink(
                name.text.toString(),
                description.text.toString(),
                matchRule.text.toString(),
                replaceRule.text.toString(),
                packageName.text.toString(),
                activity.text.toString(),
                uri.text.toString(),
                extraKey.text.toString(),
                extraValue.text.toString()
            ).start("test")
        }
        delete.setOnClickListener {
            OpenLink.delete(id!!)
            intent.putExtra("id", id)
            setResult(1, intent)
            finish()
        }
    }
}