package com.shutiao.flow

import android.app.Activity
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
import android.widget.GridView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

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
                intent.putExtra("item", idList[position])
                context.startActivityForResult(intent, 1)
            }
            // 长按删除功能
            textView.setOnLongClickListener {

                popupMenu.setOnMenuItemClickListener { menuItem ->
                    when (menuItem.itemId) {
                        R.id.delete -> {
                            val db = App.dbHelper.writableDatabase
                            db.delete("list", "id = ?", arrayOf(idList[position]))
                            Toast.makeText(context, "已删除", Toast.LENGTH_SHORT).show()
                            context.recreate()
                            true
                        }

                        R.id.copy -> {
                            // 复制功能暂不实现
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
            val intent = Intent(this@JumpManageActivity, JumpEditActivity::class.java)
            intent.putExtra("item", "")
            startActivityForResult(intent, 1)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        val adp = findViewById<GridView>(R.id.startlist)
        adp.adapter = GridAdapter(this, item("name"))
        super.onActivityResult(requestCode, resultCode, data)
    }
}