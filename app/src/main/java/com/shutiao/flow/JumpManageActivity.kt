package com.shutiao.flow

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.GridView
import android.widget.Toast

class JumpManageActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val list = App.sharedPreferences
        if (list.getString("browser", "").isNullOrEmpty()) {
            showBrowserSelector(this) { finish() }
        }

        setContentView(R.layout.activity_jump_manage)
        val adp = findViewById<GridView>(R.id.startlist)
        adp.adapter = RuleAdapter(this)
        
        findViewById<Button>(R.id.settings).setOnClickListener {
            startActivityForResult(Intent(this, SettingsActivity::class.java), 1)
        }

        val addButton = findViewById<Button>(R.id.add)
        addButton.setOnClickListener {
            startActivityForResult(Intent(this, JumpEditActivity::class.java).putExtra("id", ""), 0)
        }
        addButton.setOnLongClickListener {
            val dialog = AlertDialog.Builder(this)
            dialog.setTitle("导入快捷方式")
            dialog.setMessage("请输入快捷方式的JSON字符串")
            val input = EditText(this)
            dialog.setView(input)
            dialog.setPositiveButton("导入") { _, _ ->
                try {
                    val json = org.json.JSONObject(input.text.toString())
                    val openLink = OpenLink(
                        name = json.optString("name", ""),
                        description = json.optString("description", ""),
                        matchRule = json.optString("matchRule", ""),
                        replaceRule = json.optString("replaceRule", ""),
                        packageName = json.optString("packageName", ""),
                        activity = json.optString("activity", ""),
                        uri = json.optString("uri", ""),
                        extraKey = json.optString("extraKey", ""),
                        extraValue = json.optString("extraValue", "")
                    )
                    openLink.save()
                    Toast.makeText(this, "已导入", Toast.LENGTH_SHORT).show()
                    (adp.adapter as BaseAdapter).notifyDataSetChanged()
                } catch (_: Exception) {
                    Toast.makeText(this, "导入失败", Toast.LENGTH_SHORT).show()
                }
            }
            dialog.setNegativeButton("取消") { _, _ -> }
            dialog.show()
            true
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (resultCode != 0) {
            (findViewById<GridView>(R.id.startlist).adapter as BaseAdapter).notifyDataSetChanged()
        }
    }
}