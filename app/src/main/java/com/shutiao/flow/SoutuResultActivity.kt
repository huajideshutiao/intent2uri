package com.shutiao.flow

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ListView
import android.widget.TextView

class SoutuResultActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_soutu_result)
        val mainLayout = findViewById<android.widget.RelativeLayout>(R.id.main)
        val results = findViewById<ListView>(R.id.imageResults)
        // 对整个布局设置 OnApplyWindowInsetsListener
        mainLayout.setOnApplyWindowInsetsListener { _, insets ->
            val statusBarHeight = insets.systemWindowInsetTop
            val imeHeight = insets.systemWindowInsetBottom
            results.setPadding(
                results.paddingLeft,
                statusBarHeight,
                results.paddingRight,
                imeHeight
            )
            insets
        }
        mainLayout.requestApplyInsets()
        val items = Soutu.data.itemList

        class ResultAdapter() : BaseAdapter() {
            private inner class ViewHolder(view: View) {
                val imageView: ImageView = view.findViewById(R.id.imageView4)
                val titleText: TextView = view.findViewById(R.id.name)
                val descriptionText: TextView = view.findViewById(R.id.description)
                var currentPosition: Int = -1
            }
            override fun getCount(): Int = items.size
            override fun getItem(position: Int): Item = items[position]
            override fun getItemId(position: Int): Long = position.toLong()
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view: View
                val holder: ViewHolder

                if (convertView == null) {
                    view = View.inflate(this@SoutuResultActivity, R.layout.item, null)
                    holder = ViewHolder(view)
                    view.tag = holder
                } else {
                    view = convertView
                    holder = view.tag as ViewHolder
                }

                val item = getItem(position)
                holder.currentPosition = position

                // 清除之前的图片，避免复用问题
                holder.imageView.setImageDrawable(null)

                // 原生图片加载逻辑
                if (!item.img.isNullOrEmpty()) {
                    Thread {
                            val connect = java.net.URL(item.img).openConnection() as java.net.HttpURLConnection
                            connect.connectTimeout = 5000
                            connect.readTimeout = 5000
                            val inputStream = connect.inputStream
                            val bitmap = android.graphics.BitmapFactory.decodeStream(inputStream)
                            inputStream.close()
                            connect.disconnect()
                            // 在主线程更新 UI
                            this@SoutuResultActivity.runOnUiThread {
                                // 检查位置是否匹配，避免图片加载到错误的位置
                                if (holder.currentPosition == position) {
                                    holder.imageView.visibility = View.VISIBLE
                                    holder.imageView.setImageBitmap(bitmap)
                                }
                            }
                    }.start()
                }

                holder.titleText.text = item.title
                holder.descriptionText.text = item.description

                view.setOnClickListener {
                    if (item.link.isNotEmpty()) {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(item.link))
                        this@SoutuResultActivity.startActivity(intent)
                    }
                }

                return view
            }
        }
        results.adapter = ResultAdapter()
        val url = intent.getStringExtra("url")
        findViewById<ImageButton>(R.id.open).setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }
    }
}