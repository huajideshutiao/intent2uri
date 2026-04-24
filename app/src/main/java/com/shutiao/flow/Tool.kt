package com.shutiao.flow

import android.app.AlertDialog
import android.content.ComponentName
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.net.Uri
import android.os.IBinder
import android.util.Base64
import android.util.LruCache
import android.view.Gravity
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import org.json.JSONObject
import rikka.shizuku.Shizuku
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

data class OpenLink(
    val name: String,
    val description: String,
    val matchRule: String,
    val replaceRule: String,
    val packageName: String,
    val activity: String,
    var uri: String,
    val extraKey: String,
    var extraValue: String,
    val iconType: String = "app",
    val iconValue: String = "",
    val showInAssistant: Boolean = false
) {
    var id: String = ""

    private val cacheKey: String
        get() = when (iconType) {
            "text" -> "text_${iconValue.ifEmpty { name.take(1) }}"
            "image" -> "img_$iconValue"
            else -> "pkg_" + iconValue.ifEmpty {
                packageName.ifEmpty {
                    App.sharedPreferences.getString("browser", "") ?: ""
                }
            }
        }

    fun getIconBitmap(context: Context): Bitmap {
        val key = cacheKey
        memoryCache.get(key)?.let { return it }

        val cacheDir = File(context.cacheDir, "icons").apply { if (!exists()) mkdirs() }
        val cacheFile = File(cacheDir, "icon_${key.hashCode().toString(16)}.png")

        if (cacheFile.exists()) {
            BitmapFactory.decodeFile(cacheFile.absolutePath)?.let {
                memoryCache.put(key, it)
                return it
            }
        }

        val size = (55 * context.resources.displayMetrics.density).toInt()
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        when (iconType) {
            "text" -> {
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.LTGRAY
                    canvas.drawCircle(size / 2f, size / 2f, size / 2f, this)
                    color = Color.WHITE
                    textSize = size * 0.6f
                    textAlign = Paint.Align.CENTER
                }
                val text = iconValue.ifEmpty { name.take(1) }
                val bounds = Rect()
                paint.getTextBounds(text, 0, text.length, bounds)
                canvas.drawText(text, size / 2f, (size - bounds.top - bounds.bottom) / 2f, paint)
            }

            "image" -> try {
                val options = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                    BitmapFactory.decodeFile(iconValue, this)
                    inSampleSize = calculateInSampleSize(this, size, size)
                    inJustDecodeBounds = false
                }
                BitmapFactory.decodeFile(iconValue, options)?.let { original ->
                    val side = minOf(original.width, original.height)
                    val srcRect = Rect(
                        (original.width - side) / 2,
                        (original.height - side) / 2,
                        (original.width + side) / 2,
                        (original.height + side) / 2
                    )
                    canvas.drawBitmap(original, srcRect, Rect(0, 0, size, size), Paint(Paint.FILTER_BITMAP_FLAG))
                    original.recycle()
                }
            } catch (_: Exception) {
                drawDefaultIcon(context, canvas, size)
            }

            else -> try {
                val pkg =
                    iconValue.ifEmpty { packageName.ifEmpty { App.sharedPreferences.getString("browser", "") ?: "" } }
                context.packageManager.getApplicationIcon(pkg).apply {
                    setBounds(0, 0, size, size)
                    draw(canvas)
                }
            } catch (_: Exception) {
                drawDefaultIcon(context, canvas, size)
            }
        }

        memoryCache.put(key, bitmap)
        if (!key.startsWith("pkg_")) {
            try {
                FileOutputStream(cacheFile).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            } catch (_: Exception) {
            }
        }
        return bitmap
    }

    fun loadIconAsync(context: Context, imageView: ImageView) {
        val key = cacheKey
        imageView.tag = key
        memoryCache.get(key)?.let { imageView.setImageBitmap(it); return }
        iconExecutor.execute {
            val bitmap = getIconBitmap(context)
            imageView.post { if (imageView.tag == key) imageView.setImageBitmap(bitmap) }
        }
    }

    private fun drawDefaultIcon(context: Context, canvas: Canvas, size: Int) {
        context.packageManager.getApplicationIcon(context.packageName).apply {
            setBounds(0, 0, size, size)
            draw(canvas)
        }
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val (height, width) = options.outHeight to options.outWidth
        var inSampleSize = 1
        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2
            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) inSampleSize *= 2
        }
        return inSampleSize
    }

    companion object {
        val memoryCache = LruCache<String, Bitmap>(128)
        val iconExecutor = Executors.newFixedThreadPool(4)

        private fun loadBitmapAsync(cacheKey: String, imageView: ImageView, loadBitmap: () -> Bitmap) {
            imageView.tag = cacheKey
            memoryCache.get(cacheKey)?.let { imageView.setImageBitmap(it); return }
            imageView.setImageDrawable(null)
            iconExecutor.execute {
                val bitmap = loadBitmap()
                memoryCache.put(cacheKey, bitmap)
                imageView.post { if (imageView.tag == cacheKey) imageView.setImageBitmap(bitmap) }
            }
        }

        fun loadAppIcon(context: Context, pkg: String, imageView: ImageView) {
            loadBitmapAsync("pkg_$pkg", imageView) {
                val size = (48 * context.resources.displayMetrics.density).toInt()
                val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                context.packageManager.getApplicationIcon(pkg).apply {
                    setBounds(0, 0, size, size)
                    draw(canvas)
                }
                bitmap
            }
        }

        fun clearIconCache(context: Context, id: String) {
            datas.find { it.id == id }?.let { item ->
                val key = item.cacheKey
                memoryCache.remove(key)
                File(File(context.cacheDir, "icons"), "icon_${key.hashCode().toString(16)}.png").takeIf { it.exists() }
                    ?.delete()
            }
        }

        private var _datas: MutableList<OpenLink>? = null
        val datas: MutableList<OpenLink> get() = _datas ?: run { getDatas(); _datas!! }

        fun getDatas() {
            _datas = App.dbHelper.query("list", null, null, null, null, null, "sort_order ASC").use { cursor ->
                mutableListOf<OpenLink>().apply {
                    while (cursor.moveToNext()) {
                        add(
                            OpenLink(
                                cursor.getString(1),
                                cursor.getString(2),
                                cursor.getString(3),
                                cursor.getString(4),
                                cursor.getString(5),
                                cursor.getString(6),
                                cursor.getString(7),
                                cursor.getString(8),
                                cursor.getString(9),
                                cursor.getString(11) ?: "app",
                                cursor.getString(12) ?: "",
                                cursor.getInt(13) == 1
                            ).apply { id = cursor.getString(0) })
                    }
                }
            }
        }

        fun updateOrder() {
            _datas?.forEachIndexed { index, item ->
                val values = ContentValues().apply { put("sort_order", index) }
                App.dbHelper.update("list", values, "id = ?", arrayOf(item.id))
            }
        }

        fun delete(id: String) {
            App.dbHelper.delete("list", "id = ?", arrayOf(id))
            _datas?.removeIf { it.id == id }
        }
    }

    fun save(id: String? = "") {
        val item = ContentValues().apply {
            put("name", name); put("description", description); put("matchRule", matchRule)
            put("replaceRule", replaceRule); put("packageName", packageName); put("activity", activity)
            put("uri", uri); put("extraKey", extraKey); put("extraValue", extraValue)
            put("iconType", iconType); put("iconValue", iconValue)
            put("showInAssistant", if (showInAssistant) 1 else 0)
        }
        if (id.isNullOrEmpty()) {
            App.dbHelper.insert("list", null, item)
            getDatas()
        } else {
            App.dbHelper.update("list", item, "id = ?", arrayOf(id))
            datas.indexOfFirst { it.id == id }.takeIf { it != -1 }?.let {
                this.id = id
                datas[it] = this
            }
        }
    }

    fun start(keyWord: String) {
        val command = if (replaceRule.startsWith("shell:")) {
            replaceRule.substringAfter("shell:").replace("{key}", keyWord)
        } else buildString {
            val processedKey = if (matchRule.isNotEmpty() && replaceRule.isNotEmpty()) keyWord.replace(
                Regex(matchRule), replaceRule
            ) else keyWord
            append("am start -a android.intent.action.VIEW")
            if (packageName.isNotEmpty()) append(" -n $packageName")
            if (activity.isNotEmpty()) append("/$activity")
            if (uri.isNotEmpty()) append(" -d '${uri.replace("{key}", processedKey)}'")
            if (extraKey.isNotEmpty()) {
                val keys = extraKey.split("\n")
                val values = extraValue.replace("{key}", processedKey).split("\n")
                keys.indices.forEach { i ->
                    if (i < values.size) append(" --e").append(keys[i].replaceRange(1, 2, " '")).append("' '")
                        .append(values[i]).append("'")
                }
            }
            append(" > /dev/null 2>&1\n")
        }

        try {
            val conn = object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                    IUserService.Stub.asInterface(binder).exec(command)
                    Shizuku.unbindUserService(App.args, this, false)
                }

                override fun onServiceDisconnected(name: ComponentName?) {}
            }
            if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                Shizuku.bindUserService(App.args, conn)
            } else {
                Shizuku.addRequestPermissionResultListener(object : Shizuku.OnRequestPermissionResultListener {
                    override fun onRequestPermissionResult(requestCode: Int, grantResult: Int) {
                        if (grantResult == PackageManager.PERMISSION_GRANTED) Shizuku.bindUserService(App.args, conn)
                        else App.runRootCommand(command)
                        Shizuku.removeRequestPermissionResultListener(this)
                    }
                })
                Shizuku.requestPermission(0)
            }
        } catch (_: Exception) {
            App.runRootCommand(command)
        }
    }
}

data class Item(val img: String?, val title: String, val description: String, val link: String)
data class Data(
    val successful: Boolean = true,
    val itemList: MutableList<Item> = mutableListOf(),
    var jump: Boolean = true,
    var url: String = ""
)

class Soutu(val file: ByteArray) {
    private val imageUrl by lazy {
        val res = post(
            "https://yandex.com/images-apphost/image-download?cbird=117&images_avatars_size=preview&images_avatars_namespace=images-cbir",
            mapOf("Content-Type" to "image/jpeg"),
            null,
            null
        )
        "https://avatars.mds.yandex.net/get-images-cbir/${res.substring(15, res.indexOf('"', 16))}/orig"
    }
    var data = Data()
        private set

    companion object {
        var instance: Soutu? = null
            private set
    }

    private fun post(
        url: String, headers: Map<String, String>?, imgPartName: String?, form: ((OutputStream) -> Unit)?
    ): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 3000
            setRequestProperty(
                "Content-Type",
                headers?.get("Content-Type") ?: "multipart/form-data; boundary=----WebKitFormBoundary7MA4YWxkTrZu0gW"
            )
            headers?.filter { it.key != "Content-Type" }?.forEach { (k, v) -> setRequestProperty(k, v) }
        }
        conn.outputStream.use { os ->
            if (headers?.get("Content-Type") == null) {
                os.write("------WebKitFormBoundary7MA4YWxkTrZu0gW\r\nContent-Disposition: form-data; name=\"${imgPartName ?: "image"}\"; filename=\"blob\"\r\nContent-Type: image/jpeg\r\n\r\n".toByteArray())
                os.write(file)
                form?.invoke(os)
                os.write("\r\n------WebKitFormBoundary7MA4YWxkTrZu0gW--\r\n".toByteArray())
            } else os.write(file)
        }
        return conn.inputStream.bufferedReader().use { it.readText() }.also { conn.disconnect() }
    }

    fun upload(site: String, callback: (Data) -> Unit) {
        data = Data()
        Thread {
            when (site) {
                "saucenao" -> data.url = "https://saucenao.com/search.php?url=$imageUrl"
                "google" -> data.url = "https://www.google.com/searchbyimage?client=app&image_url=$imageUrl"
                "yandex" -> data.url = "https://yandex.ru/images/search?rpt=imageview&cbir_page=similar&url=$imageUrl"
                "ascii2d" -> data.url = "https://ascii2d.net/search/url/$imageUrl"
                "百度" -> {
                    val body = post(
                        "https://mtbed.netsons.org/upload.php", mapOf("Origin" to "https://695402.xyz"), null, null
                    )
                    data.url = "https://graph.baidu.com/details?promotion_name=pc_image_shituindex&carousel=0&image=${
                        body.substring(
                            43, body.length - 3
                        ).replace("\\", "")
                    }"
                }

                "animetrace" -> {
                    data.jump = false
                    val body = post("https://api.animetrace.com/v1/search", null, "file", null)
                    val characters = JSONObject(body).getJSONArray("data").getJSONObject(0).getJSONArray("character")
                    for (i in 0 until characters.length()) {
                        val char = characters.getJSONObject(i)
                        val name = char.getString("character")
                        val work = char.getString("work")
                        data.itemList.add(Item(null, name, work, "https://www.bing.com/images/search?q=$name+$work"))
                    }
                }

                "搜图酱" -> {
                    data.jump = false
                    val n = URL("https://soutubot.moe").readText()
                        .let { it.substring(it.indexOf("m: ") + 3..it.indexOf("m: ") + 15).toLong() }
                    val kj = Base64.encodeToString(
                        ((System.currentTimeMillis() / 1000).toBigInteger().pow(2) + (49 + n).toBigInteger()).toString()
                            .toByteArray(), Base64.NO_WRAP
                    ).reversed().replace("=", "")
                    val body = post("https://soutubot.moe/api/search", mapOf("x-api-key" to kj), "file") {
                        it.write("\r\n------WebKitFormBoundary7MA4YWxkTrZu0gW\r\nContent-Disposition: form-data; name=\"factor\"\r\n\r\n1.2".toByteArray())
                    }
                    data.url =
                        "https://soutubot.moe/results/${body.substring(body.indexOf("id") + 5..body.indexOf("id") + 20)}"
                    val list = JSONObject(body).getJSONArray("data")
                    for (i in 0 until list.length()) {
                        val item = list.getJSONObject(i)
                        if (item.getDouble("similarity") < 40.0) break
                        data.itemList.add(
                            Item(
                                item.getString("previewImageUrl"),
                                item.getString("title"),
                                "相似度：${item.get("similarity")}\n来源：${item.getString("source")}",
                                when (item.getString("source")) {
                                    "nhentai" -> "https://nhentai.net${item.getString("subjectPath")}"
                                    "ehentai" -> "https://exhentai.org${item.getString("subjectPath")}"
                                    else -> ""
                                }
                            )
                        )
                    }
                }
            }
            instance = this
            callback(data)
        }.start()
    }
}

fun showBrowserSelector(context: Context, onCancel: (() -> Unit)? = null) {
    val progressDialog = AlertDialog.Builder(context)
        .setTitle("正在加载浏览器列表...")
        .setView(android.widget.ProgressBar(context).apply { setPadding(50, 50, 50, 50) })
        .setCancelable(onCancel != null)
        .apply { onCancel?.let { setOnCancelListener { it() } } }
        .show()

    Thread {
        val pm = context.packageManager
        val browserList = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.bing.com")).let {
            pm.queryIntentActivities(it, PackageManager.MATCH_ALL)
        }.filter { it.activityInfo.packageName != context.packageName }

        (context as? android.app.Activity)?.runOnUiThread {
            progressDialog.dismiss()
            if (browserList.isEmpty()) {
                Toast.makeText(context, "未找到浏览器", Toast.LENGTH_SHORT).show()
                return@runOnUiThread
            }

            val layout = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(16, 16, 16, 16)
                gravity = Gravity.CENTER
            }

            val dialog = AlertDialog.Builder(context)
                .setTitle("选择默认浏览器")
                .setCancelable(onCancel == null)
                .setView(layout)
                .create()

            browserList.forEach { browser ->
                val item = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER
                    setPadding(16, 16, 16, 16)
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                    setOnClickListener {
                        App.sharedPreferences.edit().putString("browser", browser.activityInfo.packageName).apply()
                        Toast.makeText(context, "已设置${browser.loadLabel(pm)}为默认浏览器", Toast.LENGTH_SHORT).show()
                        dialog.dismiss()
                    }
                }
                item.addView(ImageView(context).apply {
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    layoutParams = ViewGroup.LayoutParams(120, 120)
                    setImageDrawable(browser.loadIcon(pm))
                })
                item.addView(TextView(context).apply {
                    text = browser.loadLabel(pm)
                    gravity = Gravity.CENTER
                    setPadding(0, 8, 0, 0)
                })
                layout.addView(item)
            }
            onCancel?.let { dialog.setOnCancelListener { it() } }
            dialog.show()
        }
    }.start()
}
