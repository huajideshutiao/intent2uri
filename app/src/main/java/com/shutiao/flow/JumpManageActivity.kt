package com.shutiao.flow

import android.animation.LayoutTransition
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.DragEvent
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.GridLayout
import android.widget.Toast
import org.json.JSONObject

class JumpManageActivity : Activity() {

    private lateinit var gridLayout: GridLayout
    private val viewCache = mutableMapOf<String, View>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val list = App.sharedPreferences
        if (list.getString("browser", "").isNullOrEmpty()) {
            showBrowserSelector(this) { finish() }
        }

        setContentView(R.layout.activity_jump_manage)
        gridLayout = findViewById(R.id.startlist)
        gridLayout.layoutTransition = LayoutTransition()

        refreshList()

        findViewById<Button>(R.id.settings).setOnClickListener {
            startActivityForResult(Intent(this, SettingsActivity::class.java), 1)
        }

        val addButton = findViewById<Button>(R.id.add)
        addButton.setOnClickListener {
            startActivityForResult(Intent(this, JumpEditActivity::class.java).putExtra("id", ""), 0)
        }
        addButton.setOnLongClickListener {
            val dialog = AlertDialog.Builder(this)
            dialog.setTitle(getString(R.string.import_shortcut))
            dialog.setMessage(getString(R.string.import_json_prompt))
            val input = EditText(this)
            dialog.setView(input)
            dialog.setPositiveButton(getString(R.string.import_action)) { _, _ ->
                try {
                    OpenLink.fromJson(JSONObject(input.text.toString())).save()
                    Toast.makeText(this, getString(R.string.imported), Toast.LENGTH_SHORT).show()
                    refreshList()
                } catch (_: Exception) {
                    Toast.makeText(this, getString(R.string.import_failed), Toast.LENGTH_SHORT).show()
                }
            }
            dialog.setNegativeButton(getString(R.string.cancel)) { _, _ -> }
            dialog.show()
            true
        }
    }

    private fun createItemView(item: OpenLink, inflater: LayoutInflater): View {
        val view = inflater.inflate(R.layout.item, gridLayout, false)
        view.tag = ItemViewHolder(view)

        updateViewBinding(view, item)

        view.setOnClickListener {
            val intent = Intent(this, JumpEditActivity::class.java)
            intent.putExtra("id", item.id)
            startActivityForResult(intent, 0)
        }

        val params = view.layoutParams as GridLayout.LayoutParams
        params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
        view.layoutParams = params

        view.setOnLongClickListener {
            // 拖拽只在本 Activity 内进行，位置信息走 localState，无需 ClipData 载荷
            view.startDragAndDrop(null, View.DragShadowBuilder(view), view, 0)
            true
        }

        view.setOnDragListener { v, event ->
            when (event.action) {
                DragEvent.ACTION_DRAG_ENTERED -> {
                    val draggedView = event.localState as View
                    if (v != draggedView && v.parent == gridLayout) {
                        val draggedIndex = gridLayout.indexOfChild(draggedView)
                        val targetIndex = gridLayout.indexOfChild(v)
                        if (draggedIndex != -1 && targetIndex != -1) {
                            gridLayout.removeViewAt(draggedIndex)
                            gridLayout.addView(draggedView, targetIndex)

                            val data = OpenLink.datas.removeAt(draggedIndex)
                            OpenLink.datas.add(targetIndex, data)
                        }
                    }
                }
                DragEvent.ACTION_DROP -> {
                    Thread { OpenLink.updateOrder() }.start()
                }
            }
            true
        }
        return view
    }

    private fun updateViewBinding(view: View, item: OpenLink) {
        val holder = view.tag as ItemViewHolder
        holder.title.text = item.name
        holder.description.text = item.description
        holder.icon.setImageBitmap(item.getIconBitmap(this))
    }

    private fun refreshList() {
        val currentDatas = OpenLink.datas
        val inflater = LayoutInflater.from(this)

        val existingIds = currentDatas.map { it.id }.toSet()
        val iterator = viewCache.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (entry.key !in existingIds) {
                gridLayout.removeView(entry.value)
                iterator.remove()
            }
        }

        currentDatas.forEachIndexed { index, item ->
            val existingView = viewCache[item.id]
            if (existingView == null) {
                val newView = createItemView(item, inflater)
                viewCache[item.id] = newView
                gridLayout.addView(newView, index)
            } else {
                if (gridLayout.indexOfChild(existingView) != index) {
                    gridLayout.removeView(existingView)
                    gridLayout.addView(existingView, index)
                }
                updateViewBinding(existingView, item)
            }
        }
    }

    private fun refreshItem(id: String) {
        val item = OpenLink.datas.find { it.id == id } ?: return
        val view = viewCache[id] ?: return
        updateViewBinding(view, item)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        val id = data?.getStringExtra("id")
        when (resultCode) {
            JumpEditActivity.RESULT_UPDATED -> {
                if (id != null) refreshItem(id)
            }
            JumpEditActivity.RESULT_ADDED, JumpEditActivity.RESULT_DELETED -> {
                refreshList()
            }
        }
    }
}
