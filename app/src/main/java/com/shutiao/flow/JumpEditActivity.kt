package com.shutiao.flow

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.util.Log
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
import java.io.File
import java.io.FileOutputStream

class JumpEditActivity : Activity() {

    companion object {
        const val RESULT_UPDATED = 1
        const val RESULT_DELETED = 2
        const val RESULT_ADDED = 3
        private const val REQUEST_PICK_IMAGE = 2
    }

    private lateinit var name: EditText
    private lateinit var description: EditText
    private lateinit var packageName: EditText
    private lateinit var activityName: EditText
    private lateinit var uri: EditText
    private lateinit var matchRule: EditText
    private lateinit var replaceRule: EditText
    private lateinit var extra: EditText
    private lateinit var cbShowAssistant: CheckBox
    private lateinit var iconTypeGroup: RadioGroup
    private lateinit var iconPreview: ImageView

    /** 图标取值（包名 / 首字符 / 本地图片路径），随图标类型切换而清空 */
    private var iconValue: String = ""

    private val iconType: String
        get() = when (iconTypeGroup.checkedRadioButtonId) {
            R.id.rb_text -> "text"
            R.id.rb_image -> "image"
            else -> "app"
        }

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_jump_edit)
        val start = findViewById<Button>(R.id.start)
        val save = findViewById<Button>(R.id.save)
        val delete = findViewById<Button>(R.id.delete)
        val copy = findViewById<Button>(R.id.copy)
        val show = findViewById<TextView>(R.id.show)

        name = findViewById(R.id.name)
        description = findViewById(R.id.description)
        packageName = findViewById(R.id.packageName)
        activityName = findViewById(R.id.activity)
        uri = findViewById(R.id.uri)
        matchRule = findViewById(R.id.matchRule)
        replaceRule = findViewById(R.id.replaceRule)
        extra = findViewById(R.id.extra)
        cbShowAssistant = findViewById(R.id.cb_show_assistant)
        iconTypeGroup = findViewById(R.id.iconTypeGroup)
        iconPreview = findViewById(R.id.iconPreview)

        findViewById<Button>(R.id.btn_reset_icon).setOnClickListener {
            iconValue = ""
            iconTypeGroup.check(R.id.rb_app)
            refreshPreview()
        }

        iconPreview.setOnClickListener {
            when (iconTypeGroup.checkedRadioButtonId) {
                R.id.rb_app -> showAppSelector { pkg ->
                    iconValue = pkg
                    refreshPreview()
                }

                R.id.rb_image -> pickImage()
                R.id.rb_text -> {
                    iconValue = ""
                    refreshPreview()
                }
            }
        }

        iconTypeGroup.setOnCheckedChangeListener { _, _ ->
            iconValue = ""
            refreshPreview()
        }

        name.onTextChanged {
            if (iconTypeGroup.checkedRadioButtonId == R.id.rb_text) refreshPreview()
        }

        val id = intent.extras?.getString("id", "")

        findViewById<Button>(R.id.btn_pick_app).setOnClickListener {
            showAppSelector { pkg -> packageName.setText(pkg) }
        }

        // 新建时无可删除对象，删除按钮沿用 XML 的 gone
        if (id.isNullOrEmpty()) {
            findViewById<RadioButton>(R.id.rb_app).isChecked = true
        } else {
            delete.visibility = View.VISIBLE
            OpenLink.datas.first { it.id == id }.let { link ->
                show.text = getString(R.string.open_via_kkp, id)
                name.setText(link.name)
                description.setText(link.description)
                matchRule.setText(link.matchRule)
                replaceRule.setText(link.replaceRule)
                packageName.setText(link.packageName)
                activityName.setText(link.activity)
                uri.setText(link.uri)
                extra.setText(link.extra)
                cbShowAssistant.isChecked = link.showInAssistant
                iconTypeGroup.check(
                    when (link.iconType) {
                        "text" -> R.id.rb_text
                        "image" -> R.id.rb_image
                        else -> R.id.rb_app
                    }
                )
                iconValue = link.iconValue
                refreshPreview()
            }
        }

        cbShowAssistant.setOnCheckedChangeListener { _, isChecked ->
            if (!id.isNullOrEmpty()) buildLink(showAssistant = isChecked).save(id)
        }

        save.setOnClickListener {
            if (!id.isNullOrEmpty()) {
                OpenLink.clearIconCache(id)
                buildLink().save(id)
                intent.putExtra("id", id)
                setResult(RESULT_UPDATED, intent)
            } else {
                buildLink().save()
                setResult(RESULT_ADDED, intent)
            }
            finish()
        }

        copy.setOnClickListener {
            val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("backup", buildLink().toJson().toString()))
            Toast.makeText(this, getString(R.string.exported_to_clipboard), Toast.LENGTH_SHORT).show()
        }

        start.setOnClickListener {
            buildLink(iconT = "app", iconV = "").start("test")
        }
        delete.setOnClickListener {
            OpenLink.delete(id!!)
            intent.putExtra("id", id)
            setResult(RESULT_DELETED, intent)
            finish()
        }
    }

    private fun buildLink(
        showAssistant: Boolean = cbShowAssistant.isChecked,
        iconT: String = iconType,
        iconV: String = iconValue
    ) = OpenLink(
        name.text.toString(),
        description.text.toString(),
        matchRule.text.toString(),
        replaceRule.text.toString(),
        packageName.text.toString(),
        activityName.text.toString(),
        uri.text.toString(),
        extra.text.toString(),
        iconT,
        iconV,
        showAssistant
    )

    private fun refreshPreview() {
        if (iconType == "image" && iconValue.isEmpty()) {
            iconPreview.setImageResource(android.R.drawable.ic_menu_gallery)
            return
        }
        buildLink().loadIconAsync(this, iconPreview)
    }

    private fun showAppSelector(onSelected: (String) -> Unit) {
        val progressDialog = AlertDialog.Builder(this)
            .setTitle(getString(R.string.loading_apps))
            .setView(ProgressBar(this).apply { setPadding(50, 50, 50, 50) })
            .setCancelable(true)
            .show()

        Thread {
            val pm = packageManager
            // 获取所有已安装的应用（包括系统应用）
            val apps = pm.getInstalledApplications(0)
                .map { AppInfo(it.loadLabel(pm).toString(), it.packageName) }
                .sortedBy { it.name }

            runOnUiThread {
                progressDialog.dismiss()
                showAppListDialog(apps, onSelected)
            }
        }.start()
    }

    private fun showAppListDialog(apps: List<AppInfo>, onSelected: (String) -> Unit) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_app_selector, null)
        val searchBar = dialogView.findViewById<EditText>(R.id.search_bar)
        val listView = dialogView.findViewById<ListView>(R.id.app_list)

        // 使用可变列表以便过滤
        val displayApps = apps.toMutableList()
        val adapter = object : ArrayAdapter<AppInfo>(this, R.layout.item_app_selector, displayApps) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val holder: AppViewHolder
                val view: View
                if (convertView == null) {
                    view = LayoutInflater.from(context).inflate(R.layout.item_app_selector, parent, false)
                    holder = AppViewHolder(
                        view.findViewById(R.id.app_name),
                        view.findViewById(R.id.app_package),
                        view.findViewById(R.id.app_icon)
                    )
                    view.tag = holder
                } else {
                    view = convertView
                    holder = view.tag as AppViewHolder
                }

                val app = getItem(position)!!
                holder.name.text = app.name
                holder.pkg.text = app.packageName

                OpenLink.loadAppIcon(context, app.packageName, holder.icon)

                return view
            }
        }
        listView.adapter = adapter

        searchBar.onTextChanged { text ->
            val query = text.lowercase()
            val filtered = apps.filter {
                it.name.lowercase().contains(query) || it.packageName.lowercase().contains(query)
            }
            adapter.clear()
            adapter.addAll(filtered)
        }

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        listView.setOnItemClickListener { _, _, position, _ ->
            // 注意这里要从 adapter 中获取，因为 displayApps 已经被 clear/addAll 修改了
            val selectedApp = adapter.getItem(position)
            if (selectedApp != null) {
                onSelected(selectedApp.packageName)
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    private data class AppInfo(val name: String, val packageName: String)
    private data class AppViewHolder(val name: TextView, val pkg: TextView, val icon: ImageView)

    private fun pickImage() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "image/*"
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        startActivityForResult(Intent.createChooser(intent, getString(R.string.choose_image)), REQUEST_PICK_IMAGE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (resultCode == RESULT_OK && requestCode == REQUEST_PICK_IMAGE) {
            val pickedUri = data?.data ?: return
            saveResizedIcon(pickedUri)?.let {
                iconValue = it
                refreshPreview()
            }
        }
    }

    private fun saveResizedIcon(uri: Uri): String? {
        return try {
            val size = (64 * resources.displayMetrics.density).toInt()
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
                contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, this) }
            }

            val bitmap = contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply {
                    inSampleSize = OpenLink.calculateInSampleSize(options, size, size)
                })
            } ?: return null

            val scaledBitmap = Bitmap.createScaledBitmap(bitmap, size, size, true)
            if (scaledBitmap != bitmap) bitmap.recycle()

            val dir = File(filesDir, "custom_icons").apply { if (!exists()) mkdirs() }
            val destFile = File(dir, "icon_${System.currentTimeMillis()}.png")

            FileOutputStream(destFile).use {
                scaledBitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            scaledBitmap.recycle()
            destFile.absolutePath
        } catch (e: Exception) {
            Log.w("JumpEdit", "save icon failed", e)
            null
        }
    }
}
