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

class RuleAdapter(
    private val context: Activity,
) : BaseAdapter() {
    private class ViewHolder(context: Context, view: View) {
        val imageView: ImageView = view.findViewById(R.id.imageView4)
        val titleText: TextView = view.findViewById(R.id.name)
        val descriptionText: TextView = view.findViewById(R.id.description)
        val popupMenu: PopupMenu =
            PopupMenu(context, view).apply { this.menuInflater.inflate(R.menu.jump_manage_menu, this.menu) }
    }

    override fun getCount() = OpenLink.datas.size
    override fun getItem(position: Int) = OpenLink.datas[position]
    override fun getItemId(position: Int) = position.toLong()
    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val view: View
        val holder: ViewHolder
        if (convertView == null) {
            view = View.inflate(context, R.layout.item, null)
            holder = ViewHolder(context, view)
            view.tag = holder
        } else {
            view = convertView
            holder = view.tag as ViewHolder
        }

        holder.titleText.text = getItem(position).name
        holder.descriptionText.text = getItem(position).description
        try {
            holder.imageView.setImageDrawable(
                context.packageManager.getApplicationIcon(
                    getItem(position).packageName.ifEmpty { App.sharedPreferences.getString("browser", "")!! }
                )
            )
        } catch (_: PackageManager.NameNotFoundException) {
            holder.imageView.setImageDrawable(
                context.packageManager.getApplicationIcon(context.packageName)
            )
        }
        holder.imageView.visibility = View.VISIBLE
        view.setOnClickListener {
            val intent = Intent(context, JumpEditActivity::class.java)
            intent.putExtra("id", getItem(position).id)
            context.startActivityForResult(intent, 0)
        }
        // 长按删除功能
        view.setOnLongClickListener {
            holder.popupMenu.setOnMenuItemClickListener { menuItem ->
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
            holder.popupMenu.show()
            true
        }
        return view
    }
}