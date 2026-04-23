package com.shutiao.flow

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import org.json.JSONObject

class JumpEditActivity : Activity() {

    private fun getIconType(group: RadioGroup): String = when (group.checkedRadioButtonId) {
        R.id.rb_text -> "text"
        R.id.rb_image -> "image"
        else -> "app"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_jump_edit)
        val start = findViewById<Button>(R.id.start)
        val save = findViewById<Button>(R.id.save)
        val delete = findViewById<Button>(R.id.delete)
        val copy = findViewById<Button>(R.id.copy)
        val show = findViewById<TextView>(R.id.show)
        delete.visibility = View.VISIBLE
        start.visibility = View.VISIBLE

        val name = findViewById<EditText>(R.id.name)
        val description = findViewById<EditText>(R.id.description)
        val packageName = findViewById<EditText>(R.id.packageName)
        val activity = findViewById<EditText>(R.id.activity)
        val uri = findViewById<EditText>(R.id.uri)
        val matchRule = findViewById<EditText>(R.id.matchRule)
        val replaceRule = findViewById<EditText>(R.id.replaceRule)
        val extraKey = findViewById<EditText>(R.id.extra_key)
        val extraValue = findViewById<EditText>(R.id.extra_value)
        val cbShowAssistant = findViewById<CheckBox>(R.id.cb_show_assistant)

        val iconTypeGroup = findViewById<RadioGroup>(R.id.iconTypeGroup)
        val iconValue = findViewById<TextView>(R.id.iconValue)
        val iconPreview = findViewById<ImageView>(R.id.iconPreview)
        val btnPickApp = findViewById<Button>(R.id.btn_pick_app)

        fun refreshPreview() {
            val tempLink = OpenLink(
                name.text.toString(), "", "", "",
                packageName.text.toString(), "", "", "", "",
                getIconType(iconTypeGroup), iconValue.text.toString(),
                cbShowAssistant.isChecked
            )
            tempLink.loadIconAsync(this, iconPreview)
        }

        iconPreview.setOnClickListener {
            when (iconTypeGroup.checkedRadioButtonId) {
                R.id.rb_app -> showAppSelector { pkg ->
                    iconValue.text = pkg
                    refreshPreview()
                }

                R.id.rb_image -> pickImage()
                R.id.rb_text -> {
                    iconValue.text = ""
                    refreshPreview()
                }
            }
        }

        iconTypeGroup.setOnCheckedChangeListener { _, _ ->
            iconValue.text = ""
            refreshPreview()
        }

        name.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (iconTypeGroup.checkedRadioButtonId == R.id.rb_text) {
                    refreshPreview()
                }
            }

            override fun afterTextChanged(s: Editable?) {}
        })

        val id = intent.extras?.getString("id", "")

        btnPickApp.setOnClickListener {
            showAppSelector { pkg -> packageName.setText(pkg) }
        }

        // 如果是新建项目，隐藏删除按钮
        if (id.isNullOrEmpty()) {
            delete.visibility = View.GONE
            findViewById<RadioButton>(R.id.rb_app).isChecked = true
        } else {
            OpenLink.datas.first { it.id == id }.apply {
                show.text = "你可以通过 kkp://${id}/ 来打开这个快捷方式"
                name.setText(this.name)
                description.setText(this.description)
                matchRule.setText(this.matchRule)
                replaceRule.setText(this.replaceRule)
                packageName.setText(this.packageName)
                activity.setText(this.activity)
                uri.setText(this.uri)
                extraKey.setText(this.extraKey)
                extraValue.setText(this.extraValue)
                cbShowAssistant.isChecked = this.showInAssistant
                when (this.iconType) {
                    "app" -> findViewById<RadioButton>(R.id.rb_app).isChecked = true
                    "text" -> findViewById<RadioButton>(R.id.rb_text).isChecked = true
                    "image" -> findViewById<RadioButton>(R.id.rb_image).isChecked = true
                }
                iconValue.text = this.iconValue
                refreshPreview()
            }
        }

        cbShowAssistant.setOnCheckedChangeListener { _, isChecked ->
            if (!id.isNullOrEmpty()) {
                OpenLink(
                    name.text.toString(),
                    description.text.toString(),
                    matchRule.text.toString(),
                    replaceRule.text.toString(),
                    packageName.text.toString(),
                    activity.text.toString(),
                    uri.text.toString(),
                    extraKey.text.toString(),
                    extraValue.text.toString(),
                    getIconType(iconTypeGroup),
                    iconValue.text.toString(),
                    isChecked
                ).save(id)
            }
        }

        save.setOnClickListener {
            if (!id.isNullOrEmpty()) {
                OpenLink.clearIconCache(this, id)
            }
            OpenLink(
                name.text.toString(),
                description.text.toString(),
                matchRule.text.toString(),
                replaceRule.text.toString(),
                packageName.text.toString(),
                activity.text.toString(),
                uri.text.toString(),
                extraKey.text.toString(),
                extraValue.text.toString(),
                getIconType(iconTypeGroup),
                iconValue.text.toString(),
                cbShowAssistant.isChecked
            ).save(id)
            intent.putExtra("id", id)
            setResult(1, intent)
            finish()
        }

        copy.setOnClickListener {
            val json = JSONObject().apply {
                put("name", name.text.toString())
                put("description", description.text.toString())
                put("matchRule", matchRule.text.toString())
                put("replaceRule", replaceRule.text.toString())
                put("packageName", packageName.text.toString())
                put("activity", activity.text.toString())
                put("uri", uri.text.toString())
                put("extraKey", extraKey.text.toString())
                put("extraValue", extraValue.text.toString())
                // 不导出图标信息
            }
            val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("backup", json.toString()))
            Toast.makeText(this, "规则已导出到剪贴板", Toast.LENGTH_SHORT).show()
        }

        start.setOnClickListener {
            OpenLink(
                name.text.toString(),
                description.text.toString(),
                matchRule.text.toString(),
                replaceRule.text.toString(),
                packageName.text.toString(),
                activity.text.toString(),
                uri.text.toString(),
                extraKey.text.toString(),
                extraValue.text.toString(),
                "app",
                "",
                cbShowAssistant.isChecked
            ).start("test")
        }
        delete.setOnClickListener {
            OpenLink.delete(id!!)
            intent.putExtra("id", id)
            setResult(1, intent)
            finish()
        }
    }

    private fun showAppSelector(onSelected: (String) -> Unit) {
        val dialog = AlertDialog.Builder(this)
            .setTitle("正在加载应用列表...")
            .setView(ProgressBar(this).apply { setPadding(50, 50, 50, 50) })
            .show()

        Thread {
            val pm = packageManager
            // 优化：移除 GET_META_DATA，加快查询速度
            val apps = pm.getInstalledApplications(0)
                .filter { it.flags and ApplicationInfo.FLAG_SYSTEM == 0 || it.packageName == "com.android.browser" }
                .map {
                    AppInfo(
                        it.loadLabel(pm).toString(),
                        it.packageName,
                        it
                    )
                }
                .sortedBy { it.name }

            runOnUiThread {
                dialog.dismiss()
                showAppListDialog(apps, onSelected)
            }
        }.start()
    }

    private fun showAppListDialog(apps: List<AppInfo>, onSelected: (String) -> Unit) {
        val dialog = AlertDialog.Builder(this).create()
        val listView = ListView(this)
        val adapter = object : ArrayAdapter<AppInfo>(this, R.layout.item_app_selector, apps) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val holder: ViewHolder
                val view: View
                if (convertView == null) {
                    view = LayoutInflater.from(context).inflate(R.layout.item_app_selector, parent, false)
                    holder = ViewHolder(
                        view.findViewById(R.id.app_name),
                        view.findViewById(R.id.app_package),
                        view.findViewById(R.id.app_icon)
                    )
                    view.tag = holder
                } else {
                    view = convertView
                    holder = view.tag as ViewHolder
                }

                val app = getItem(position)!!
                holder.name.text = app.name
                holder.pkg.text = app.packageName

                // 调用全局 App 图标加载器，实现全应用复用
                OpenLink.loadAppIcon(context, app.packageName, holder.icon)

                return view
            }
        }
        listView.adapter = adapter
        listView.setOnItemClickListener { _, _, position, _ ->
            onSelected(apps[position].packageName)
            dialog.dismiss()
        }
        dialog.setView(listView)
        dialog.show()
    }

    private data class AppInfo(val name: String, val packageName: String, val appInfo: ApplicationInfo)
    private data class ViewHolder(val name: TextView, val pkg: TextView, val icon: ImageView)

    private fun pickImage() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "image/*"
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        startActivityForResult(Intent.createChooser(intent, "选择图片"), 2)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (resultCode == RESULT_OK && requestCode == 2) {
            val uri = data?.data ?: return

            // 尝试获取真实路径
            var path: String? = null
            if (uri.scheme == "file") {
                path = uri.path
            } else if (uri.scheme == "content") {
                val projection = arrayOf(android.provider.MediaStore.Images.Media.DATA)
                try {
                    contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val columnIndex = cursor.getColumnIndex(android.provider.MediaStore.Images.Media.DATA)
                            if (columnIndex != -1) {
                                path = cursor.getString(columnIndex)
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            // 如果无法获取路径（例如来自某些云盘或新版 Android 的安全限制），则将文件复制到私有目录
            if (path == null) {
                try {
                    val fileName = "icon_${System.currentTimeMillis()}.png"
                    val file = java.io.File(filesDir, "custom_icons")
                    if (!file.exists()) file.mkdirs()
                    val destFile = java.io.File(file, fileName)
                    contentResolver.openInputStream(uri)?.use { input ->
                        java.io.FileOutputStream(destFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                    path = destFile.absolutePath
                } catch (e: Exception) {
                    e.printStackTrace()
                    Toast.makeText(this, "图片选择失败", Toast.LENGTH_SHORT).show()
                }
            }

            if (path != null) {
                findViewById<TextView>(R.id.iconValue).text = path
                // 这里手动调用刷新
                val type = findViewById<RadioGroup>(R.id.iconTypeGroup).checkedRadioButtonId
                val nameView = findViewById<EditText>(R.id.name)
                val pkgView = findViewById<EditText>(R.id.packageName)
                val preview = findViewById<ImageView>(R.id.iconPreview)
                OpenLink(
                    nameView.text.toString(), "", "", "",
                    pkgView.text.toString(), "", "", "", "",
                    if (type == R.id.rb_image) "image" else "app", path,
                    findViewById<CheckBox>(R.id.cb_show_assistant).isChecked
                ).loadIconAsync(this, preview)
            }
        }
    }
}
