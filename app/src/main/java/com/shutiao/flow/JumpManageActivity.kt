package com.shutiao.flow

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.GridView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import rikka.shizuku.SystemServiceHelper.getSystemService

class JumpManageActivity : Activity() {
    class GridAdapter(
        private val context: Activity,
        data: Pair<List<String>, List<String>>,
    ) : BaseAdapter() {
        private val idList = data.first
        private val nameList = data.second
        override fun getCount() = nameList.size
        override fun getItem(position: Int) = nameList[position]
        override fun getItemId(position: Int) = position.toLong()
        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            var textView: TextView
            if (convertView == null) {
                textView = TextView(parent!!.context).apply {
                    textSize = 20f
                    gravity = Gravity.CENTER
                    val borderDrawable = GradientDrawable()
                    borderDrawable.shape = GradientDrawable.RECTANGLE
                    borderDrawable.setStroke(2, Color.BLACK)
                    borderDrawable.cornerRadius = 8f
                    background = borderDrawable
                    setPadding(8, 8, 8, 8)
                }
            } else textView = convertView as TextView

            val popupMenu = android.widget.PopupMenu(parent!!.context, textView)
            popupMenu.menuInflater.inflate(R.menu.jump_manage_menu, popupMenu.menu)

            textView.text = nameList[position]
            textView.setOnClickListener {
                val intent = Intent(parent.context, JumpEditActivity::class.java)
                intent.putExtra("id", idList[position])
                context.startActivityForResult(intent, 0)
            }
            // 长按删除功能
            textView.setOnLongClickListener {
                popupMenu.setOnMenuItemClickListener { menuItem ->
                    when (menuItem.itemId) {
                        R.id.delete -> {
                            App.dbHelper.delete("list", "id = ?", arrayOf(idList[position]))
                            Toast.makeText(context, "已删除", Toast.LENGTH_SHORT).show()
                            context.recreate()
                            true
                        }
                        R.id.copy -> {
                            val json = Json.encodeToString(OpenLink.fromDb(idList[position]))
                            val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("json", json))
                            Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
                            true
                        }
                        else -> false
                    }
                }
                popupMenu.show()
                true
            }
            return textView
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val list = App.sharedPreferences
        val browserList: List<ResolveInfo> = run {
            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.bing.com"))
            val browserList =
                packageManager.queryIntentActivities(browserIntent, PackageManager.MATCH_ALL)
                    .filter { it.activityInfo.packageName != packageName }

            if (list.getString("browser", "") == "") list.edit()
                .putString("browser", browserList[0].activityInfo.packageName).apply()
            browserList
        }

        setContentView(R.layout.activity_jump_manage)
        val adp = findViewById<GridView>(R.id.startlist)
        val aadp = findViewById<LinearLayout>(R.id.applist)

        for (i in browserList) {
            val textView = TextView(this)
            val oo = i.activityInfo
            textView.text = oo.applicationInfo.loadLabel(packageManager)
            textView.layoutParams = ViewGroup.LayoutParams(200, 200)
            textView.gravity = Gravity.CENTER
            val borderDrawable = GradientDrawable()
            borderDrawable.shape = GradientDrawable.RECTANGLE
            borderDrawable.setStroke(2, Color.BLACK)
            borderDrawable.cornerRadius = 8f
            textView.background = borderDrawable
            textView.setPadding(8, 8, 8, 8)
            textView.setOnClickListener {
                list.edit().putString("browser", oo.packageName).apply()
                Toast.makeText(
                    this,
                    "已设置${oo.applicationInfo.loadLabel(packageManager)}为默认打开方式",
                    Toast.LENGTH_SHORT
                ).show()
            }
            aadp.addView(textView)
        }
        adp.adapter = GridAdapter(this, item("name"))
        // 设置添加按钮的点击事件
        val addButton = findViewById<Button>(R.id.add)
        addButton.setOnClickListener {
            startActivityForResult(Intent(this, JumpEditActivity::class.java).putExtra("id", ""), 0)
        }
        addButton.setOnLongClickListener {
                //允许导入json，显示一个对话框输入
                val dialog = AlertDialog.Builder(this)
                dialog.setTitle("导入快捷方式")
                dialog.setMessage("请输入快捷方式的json字符串")
                val input = EditText(this)
                dialog.setView(input)
                dialog.setPositiveButton("导入") { _, _ ->
                    val json = input.text.toString()
                    try {
                        val link = Json.decodeFromString<OpenLink>(json)
                        link.toDb()
                        Toast.makeText(this, "已导入", Toast.LENGTH_SHORT).show()
                        this@JumpManageActivity.recreate()
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
            val adp = findViewById<GridView>(R.id.startlist)
            adp.adapter = GridAdapter(this, item("name"))
        }
    }
}