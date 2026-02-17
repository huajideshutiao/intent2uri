package com.shutiao.flow

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast

class GridAdapter(
    private val context: Activity,
) : BaseAdapter() {
    override fun getCount() = OpenLink.datas.size
    override fun getItem(position: Int) = OpenLink.datas[position]
    override fun getItemId(position: Int) = position.toLong()
    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val item: View = convertView ?: View.inflate(parent!!.context, R.layout.item, null)

        val popupMenu = PopupMenu(parent!!.context, item)
        popupMenu.menuInflater.inflate(R.menu.jump_manage_menu, popupMenu.menu)

        item.findViewById<TextView>(R.id.name).text = getItem(position).name
        val imageView = item.findViewById<ImageView>(R.id.imageView4)
        try {
            imageView.setImageDrawable(
                context.packageManager.getApplicationIcon(
                    getItem(position).packageName.ifEmpty { App.sharedPreferences.getString("browser", "")!! }
                )
            )
        } catch (_: PackageManager.NameNotFoundException) {
            imageView.setImageDrawable(
                context.packageManager.getApplicationIcon(context.packageName)
            )
        }
        imageView.visibility = View.VISIBLE
        item.setOnClickListener {
            val intent = Intent(parent.context, JumpEditActivity::class.java)
            intent.putExtra("id", getItem(position).id)
            context.startActivityForResult(intent, 0)
        }
        // 长按删除功能
        item.setOnLongClickListener {
            popupMenu.setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    R.id.delete -> {
                        OpenLink.delete(getItem(position).id)
                        Toast.makeText(context, "已删除", Toast.LENGTH_SHORT).show()
                        notifyDataSetChanged()
                        true
                    }

                    R.id.copy -> {
                        val backup = getItem(position).toString()
                        val clipboard =
                            context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("backup", backup))
                        Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
                        true
                    }

                    else -> false
                }
            }
            popupMenu.show()
            true
        }
        return item
    }
}