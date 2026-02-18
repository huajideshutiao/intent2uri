package com.shutiao.flow

import android.app.Activity
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import java.net.HttpURLConnection
import java.net.URL

class SoutuAdapter(private val context: Activity, private val items: List<Item>) : BaseAdapter() {
    private class ViewHolder(view: View) {
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
            view = View.inflate(context, R.layout.item, null)
            view.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                700
            )
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
                    val connect = URL(item.img).openConnection() as HttpURLConnection
                    connect.connectTimeout = 5000
                    connect.readTimeout = 5000
                    val inputStream = connect.inputStream
                    val bitmap = BitmapFactory.decodeStream(inputStream)
                    inputStream.close()
                    connect.disconnect()
                    // 在主线程更新 UI
                context.runOnUiThread {
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
                context.startActivity(intent)
            }
        }

        return view
    }
}