package com.shutiao.flow

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.GridView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class JumpManageActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val list = App.sharedPreferences
        val browserList: List<ResolveInfo> = run {
            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.bing.com"))
            val browserList =
                packageManager.queryIntentActivities(browserIntent, PackageManager.MATCH_ALL)
                    .filter { it.activityInfo.packageName != packageName }

            if (list.getString("browser", "").isNullOrEmpty()) list.edit()
                .putString("browser", browserList[0].activityInfo.packageName).apply()
            browserList
        }

        setContentView(R.layout.activity_jump_manage)
        val adp = findViewById<GridView>(R.id.startlist)
        val appList = findViewById<LinearLayout>(R.id.applist)

        val item = LinearLayout(this)
        item.orientation = LinearLayout.VERTICAL
        item.gravity = Gravity.CENTER
        item.layoutParams = ViewGroup.LayoutParams(200, 200)
        item.setPadding(8, 8, 8, 8)
        val imgView = ImageView(this)
        imgView.scaleType = ImageView.ScaleType.FIT_CENTER
        imgView.layoutParams = ViewGroup.LayoutParams(200, 120)
        val textView = TextView(this)
        textView.gravity = Gravity.CENTER

        for (i in browserList) {
            imgView.setImageDrawable(i.loadIcon(packageManager))
            textView.text = i.loadLabel(packageManager)
            item.addView(imgView)
            item.addView(textView)
            item.setOnClickListener {
                list.edit().putString("browser", i.activityInfo.packageName).apply()
                Toast.makeText(
                    this,
                    "已设置${i.loadLabel(packageManager)}为默认打开方式",
                    Toast.LENGTH_SHORT
                ).show()
            }
            appList.addView(item)
        }
        adp.adapter = RuleAdapter(this)
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
                try {
                    OpenLink.fromString(input.text.toString()).save()
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