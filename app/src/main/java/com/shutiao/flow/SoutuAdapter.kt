package com.shutiao.flow

import android.app.Activity
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import java.net.HttpURLConnection
import java.net.URL

class SoutuAdapter(private val context: Activity, private val items: List<Item>) : BaseAdapter() {

    override fun getCount(): Int = items.size
    override fun getItem(position: Int): Item = items[position]
    override fun getItemId(position: Int): Long = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view: View
        val holder: ItemViewHolder

        if (convertView == null) {
            view = LayoutInflater.from(context).inflate(R.layout.item, parent, false)
            view.layoutParams = view.layoutParams.apply {
                width = ViewGroup.LayoutParams.MATCH_PARENT
            }
            holder = ItemViewHolder(view)
            view.tag = holder
        } else {
            view = convertView
            holder = view.tag as ItemViewHolder
        }

        val item = getItem(position)
        holder.title.text = item.title
        holder.description.text = item.description

        val url = item.img
        if (url.isNullOrEmpty()) {
            holder.icon.visibility = View.GONE
        } else {
            holder.icon.visibility = View.VISIBLE
            holder.icon.layoutParams = holder.icon.layoutParams.apply {
                height = ViewGroup.LayoutParams.WRAP_CONTENT
                width = 500
            }
            OpenLink.loadBitmapAsync(url, holder.icon) { downloadBitmap(url) }
        }

        view.setOnClickListener {
            if (item.link.isNotEmpty()) {
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(item.link))
                    context.startActivity(intent)
                } catch (e: Exception) {
                    Log.w("SoutuAdapter", "open link failed: ${item.link}", e)
                }
            }
        }

        return view
    }

    private fun downloadBitmap(url: String) = try {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 5000
            readTimeout = 5000
        }
        conn.inputStream.use { BitmapFactory.decodeStream(it) }.also { conn.disconnect() }
    } catch (e: Exception) {
        Log.w("SoutuAdapter", "thumbnail download failed: $url", e)
        null
    }
}
