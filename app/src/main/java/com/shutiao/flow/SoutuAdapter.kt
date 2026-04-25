package com.shutiao.flow

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.LruCache
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.TextView
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

class SoutuAdapter(private val context: Activity, private val items: List<Item>) : BaseAdapter() {

    companion object {
        private val imageCache = LruCache<String, Bitmap>(20 * 1024 * 1024) // 20MB
        private val executor = Executors.newFixedThreadPool(4)
    }

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
            view = LayoutInflater.from(context).inflate(R.layout.item, parent, false)
            view.layoutParams = view.layoutParams.apply {
                width = ViewGroup.LayoutParams.MATCH_PARENT
//                height = 700
            }
            holder = ViewHolder(view)
            view.tag = holder
        } else {
            view = convertView
            holder = view.tag as ViewHolder
        }

        val item = getItem(position)
        holder.titleText.text = item.title
        holder.descriptionText.text = item.description

        val url = item.img
        holder.imageView.tag = url

        if (url.isNullOrEmpty()) {
            holder.imageView.visibility = View.GONE
        } else {
            holder.imageView.visibility = View.VISIBLE
            holder.imageView.layoutParams = holder.imageView.layoutParams.apply {
                height = ViewGroup.LayoutParams.WRAP_CONTENT
                width = 500
            }
            val cachedBitmap = imageCache.get(url)
            if (cachedBitmap != null) {
                holder.imageView.setImageBitmap(cachedBitmap)
            } else {
                holder.imageView.setImageBitmap(null)
                loadBitmapAsync(url, holder.imageView)
            }
        }

        view.setOnClickListener {
            if (item.link.isNotEmpty()) {
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(item.link))
                    context.startActivity(intent)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        return view
    }

    private fun loadBitmapAsync(url: String, imageView: ImageView) {
        executor.execute {
            try {
                val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 5000
                    readTimeout = 5000
                }
                val bitmap = conn.inputStream.use { BitmapFactory.decodeStream(it) }
                conn.disconnect()

                if (bitmap != null) {
                    imageCache.put(url, bitmap)
                    context.runOnUiThread {
                        if (imageView.tag == url) {
                            imageView.setImageBitmap(bitmap)
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
