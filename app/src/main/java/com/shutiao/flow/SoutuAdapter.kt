package com.shutiao.flow

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.LruCache
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

class SoutuAdapter(private val context: Activity, private val items: List<Item>) : BaseAdapter() {
    private val imageCache = LruCache<String, Bitmap>(50 * 1024 * 1024)
    private val loadingUrls = ConcurrentHashMap<String, Boolean>()

    private class ViewHolder(view: View) {
        val imageView: ImageView = view.findViewById(R.id.imageView4)
        val titleText: TextView = view.findViewById(R.id.name)
        val descriptionText: TextView = view.findViewById(R.id.description)
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
        // 使用 Tag 绑定当前 ImageView 应该显示的 URL
        holder.imageView.tag = item.img
        holder.imageView.setImageDrawable(null)
        if (!item.img.isNullOrEmpty()) {
            val cachedBitmap = imageCache.get(item.img)
            if (cachedBitmap != null) {
                holder.imageView.visibility = View.VISIBLE
                holder.imageView.setImageBitmap(cachedBitmap)
            } else {
                if (!loadingUrls.containsKey(item.img)) {
                    loadingUrls[item.img] = true
                    Thread {
                        try {
                            val connect = URL(item.img).openConnection() as HttpURLConnection
                            connect.connectTimeout = 5000
                            connect.readTimeout = 5000
                            val inputStream = connect.inputStream
                            val bitmap = BitmapFactory.decodeStream(inputStream)
                            inputStream.close()
                            connect.disconnect()
                            if (bitmap != null) {
                                imageCache.put(item.img, bitmap)
                                context.runOnUiThread {
                                    // 遍历可见的 ImageView，只更新 tag 匹配的
                                    val listView = parent as? android.widget.ListView ?: return@runOnUiThread
                                    val start = listView.firstVisiblePosition
                                    val end = listView.lastVisiblePosition
                                    for (i in start..end) {
                                        val itemView = listView.getChildAt(i - start) ?: continue
                                        val imageView = itemView.findViewById<ImageView>(R.id.imageView4)
                                        if (imageView?.tag == item.img) {
                                            imageView.visibility = View.VISIBLE
                                            imageView.setImageBitmap(bitmap)
                                        }
                                    }
                                }
                            }
                            loadingUrls.remove(item.img)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }.start()
                }
            }
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
