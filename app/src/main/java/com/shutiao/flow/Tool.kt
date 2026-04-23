package com.shutiao.flow

import android.app.AlertDialog
import android.content.ComponentName
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
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
import org.json.JSONArray
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

    fun getIconBitmap(context: Context): Bitmap {
        // 1. 优先从内存缓存读取
        val cacheKey = if (iconType == "app" && iconValue.isNotEmpty()) "pkg_$iconValue" else id
        if (cacheKey.isNotEmpty()) {
            memoryCache.get(cacheKey)?.let { return it }
        }

        val cacheDir = File(context.cacheDir, "icons")
        if (!cacheDir.exists()) cacheDir.mkdirs()
        val cacheFile = File(cacheDir, "${id.ifEmpty { "temp_" + System.currentTimeMillis() }}.png")

        if (cacheFile.exists()) {
            val bitmap = BitmapFactory.decodeFile(cacheFile.absolutePath)
            if (bitmap != null && id.isNotEmpty()) {
                memoryCache.put(id, bitmap)
            }
            if (bitmap != null) return bitmap
        }

        val size = (50 * context.resources.displayMetrics.density).toInt()
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        when (iconType) {
            "text" -> {
                val paint = Paint(Paint.ANTI_ALIAS_FLAG)
                paint.color = Color.LTGRAY
                canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)
                paint.color = Color.WHITE
                paint.textSize = size * 0.6f
                paint.textAlign = Paint.Align.CENTER
                val text = iconValue.ifEmpty { name.take(1) }
                val bounds = Rect()
                paint.getTextBounds(text, 0, text.length, bounds)
                val baseline = (size - bounds.top - bounds.bottom) / 2f
                canvas.drawText(text, size / 2f, baseline, paint)
            }

            "image" -> {
                try {
                    val options = BitmapFactory.Options().apply {
                        inJustDecodeBounds = true
                    }
                    BitmapFactory.decodeFile(iconValue, options)
                    options.inSampleSize = calculateInSampleSize(options, size, size)
                    options.inJustDecodeBounds = false
                    val original = BitmapFactory.decodeFile(iconValue, options)
                    if (original != null) {
                        val srcRect = if (original.width > original.height) {
                            val left = (original.width - original.height) / 2
                            Rect(left, 0, left + original.height, original.height)
                        } else {
                            val top = (original.height - original.width) / 2
                            Rect(0, top, original.width, top + original.width)
                        }
                        canvas.drawBitmap(original, srcRect, Rect(0, 0, size, size), Paint(Paint.FILTER_BITMAP_FLAG))
                        original.recycle()
                    }
                } catch (_: Exception) {
                    drawDefaultIcon(context, canvas, size)
                }
            }

            else -> { // app
                try {
                    val pkg =
                        iconValue.ifEmpty { packageName.ifEmpty { App.sharedPreferences.getString("browser", "")!! } }
                    val drawable = context.packageManager.getApplicationIcon(pkg)
                    drawable.setBounds(0, 0, size, size)
                    drawable.draw(canvas)
                } catch (_: Exception) {
                    drawDefaultIcon(context, canvas, size)
                }
            }
        }

        if (cacheKey.isNotEmpty()) {
            memoryCache.put(cacheKey, bitmap)
            if (!cacheKey.startsWith("pkg_")) { // App 图标无需重复写磁盘，由系统或包名缓存处理
                try {
                    FileOutputStream(cacheFile).use {
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        return bitmap
    }

    fun loadIconAsync(context: Context, imageView: ImageView) {
        val tag = id
        imageView.tag = tag

        // 先看内存缓存
        val cached = memoryCache.get(id)
        if (cached != null) {
            imageView.setImageBitmap(cached)
            return
        }

        // 异步加载
        iconExecutor.execute {
            val bitmap = getIconBitmap(context)
            imageView.post {
                if (imageView.tag == tag) {
                    imageView.setImageBitmap(bitmap)
                }
            }
        }
    }

    private fun drawDefaultIcon(context: Context, canvas: Canvas, size: Int) {
        val drawable = context.packageManager.getApplicationIcon(context.packageName)
        drawable.setBounds(0, 0, size, size)
        drawable.draw(canvas)
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val (height: Int, width: Int) = options.outHeight to options.outWidth
        var inSampleSize = 1
        if (height > reqHeight || width > reqWidth) {
            val halfHeight: Int = height / 2
            val halfWidth: Int = width / 2
            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    companion object {
        val memoryCache = LruCache<String, Bitmap>(128)
        val iconExecutor = Executors.newFixedThreadPool(4)

        private fun loadBitmapAsync(
            cacheKey: String, imageView: ImageView, loadBitmap: () -> Bitmap
        ) {
            imageView.tag = cacheKey
            val cached = memoryCache.get(cacheKey)
            if (cached != null) {
                imageView.setImageBitmap(cached)
                return
            }
            imageView.setImageDrawable(null)
            iconExecutor.execute {
                val bitmap = loadBitmap()
                memoryCache.put(cacheKey, bitmap)
                imageView.post {
                    if (imageView.tag == cacheKey) {
                        imageView.setImageBitmap(bitmap)
                    }
                }
            }
        }

        fun loadAppIcon(context: Context, pkg: String, imageView: ImageView) {
            val cacheKey = "pkg_$pkg"
            loadBitmapAsync(cacheKey, imageView) {
                val pm = context.packageManager
                val drawable = pm.getApplicationIcon(pkg)
                val size = (48 * context.resources.displayMetrics.density).toInt()
                val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                drawable.setBounds(0, 0, size, size)
                drawable.draw(canvas)
                bitmap
            }
        }

        fun clearIconCache(context: Context, id: String) {
            memoryCache.remove(id)
            val cacheFile = File(File(context.cacheDir, "icons"), "$id.png")
            if (cacheFile.exists()) cacheFile.delete()
        }

        private var _datas: MutableList<OpenLink>? = null

        val datas: MutableList<OpenLink>
            get() {
                if (_datas != null) return _datas!!
                getDatas()
                return _datas!!
            }

        fun getDatas() {
            val list = mutableListOf<OpenLink>()
            val cursor = App.dbHelper.query("list", null, null, null, null, null, "sort_order ASC")
            cursor.moveToFirst()
            while (!cursor.isAfterLast) {
                val tmp = OpenLink(
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
                )
                tmp.id = cursor.getString(0)
                list.add(tmp)
                cursor.moveToNext()
            }
            cursor.close()
            _datas = list
        }

        fun updateOrder() {
            _datas?.forEachIndexed { index, openLink ->
                val values = ContentValues().apply {
                    put("sort_order", index)
                }
                App.dbHelper.update("list", values, "id = ?", arrayOf(openLink.id))
            }
        }

        fun delete(id: String) {
            App.dbHelper.delete("list", "id = ?", arrayOf(id))
            datas.removeIf { it.id == id }
        }

    }

    fun save(id: String? = "") {
        val item = ContentValues().apply {
            put("name", name)
            put("description", description)
            put("matchRule", matchRule)
            put("replaceRule", replaceRule)
            put("packageName", packageName)
            put("activity", activity)
            put("uri", uri)
            put("extraKey", extraKey)
            put("extraValue", extraValue)
            put("iconType", iconType)
            put("iconValue", iconValue)
            put("showInAssistant", if (showInAssistant) 1 else 0)
        }
        if (id.isNullOrEmpty()) {
            App.dbHelper.insert("list", null, item)
            getDatas()
        } else {
            App.dbHelper.update("list", item, "id = ?", arrayOf(id))
            datas.indexOfFirst { it.id == id }.let {
                this.id = id
                datas[it] = this
            }
        }
    }

    fun start(keyWord: String) {
        val ii = StringBuilder("am start -a android.intent.action.VIEW")
        if (replaceRule.startsWith("shell:")) {
            ii.setLength(0)
            ii.append(replaceRule.substringAfter("shell:").replace("{key}", keyWord))
        } else {
            var keyWord = keyWord
            if (matchRule.isNotEmpty() && replaceRule.isNotEmpty()) {
                keyWord = keyWord.replace(Regex(matchRule), replaceRule)
            }
            if (packageName.isNotEmpty()) ii.append(" -n ").append(packageName)
            if (activity.isNotEmpty()) ii.append("/").append(activity)
            if (uri.isNotEmpty()) ii.append(" -d ").append(uri.replace("{key}", keyWord))
            val extraValue = extraValue.replace("{key}", keyWord)
            if (extraKey.isNotEmpty()) {
                val keys = extraKey.split("\n")
                val values = extraValue.split("\n")
                for (i in keys.indices) {
                    ii.append(" --e").append(keys[i].replaceRange(1, 2, " '")).append("' '").append(values[i])
                        .append('\'')
                }
            }
            ii.append(" > /dev/null 2>&1")
        }
        ii.append('\n')
        val command = ii.toString()
        try {
            val useShizuku = Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
            val conn = object : ServiceConnection {
                override fun onServiceConnected(componentName: ComponentName, iBinder: IBinder) {
                    IUserService.Stub.asInterface(iBinder).exec(command)
                    Shizuku.unbindUserService(App.args, this, false)
                }

                override fun onServiceDisconnected(p0: ComponentName?) {}
            }
            if (useShizuku) {
                Shizuku.bindUserService(App.args, conn)
            } else {
                Shizuku.addRequestPermissionResultListener(object : Shizuku.OnRequestPermissionResultListener {
                    override fun onRequestPermissionResult(requestCode: Int, grantResult: Int) {
                        if (grantResult == PackageManager.PERMISSION_GRANTED) {
                            Shizuku.bindUserService(App.args, conn)
                        } else {
                            App.su.write(command.toByteArray())
                            App.su.flush()
                        }
                        Shizuku.removeRequestPermissionResultListener(this)
                    }
                })
                Shizuku.requestPermission(0)
            }
        } catch (_: Exception) {
            App.su.write(command.toByteArray())
            App.su.flush()
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
        var imageUrl = post(
            "https://yandex.com/images-apphost/image-download?cbird=117&images_avatars_size=preview&images_avatars_namespace=images-cbir",
            mapOf("Content-Type" to "image/jpeg"),
            null,
            null
        )
        "https://avatars.mds.yandex.net/get-images-cbir/" + imageUrl.substring(
            15, imageUrl.indexOf('"', 16)
        ) + "/orig"
    }
    var data = Data()
        private set

    companion object {
        //实例
        var instance: Soutu? = null
            private set
    }

    private fun post(
        url: String, headers: Map<String, String>?, imgPartName: String?, form: ((OutputStream) -> Unit)?
    ): String {
        val connect = URL(url).openConnection() as HttpURLConnection
        connect.apply {
            requestMethod = "POST"
            doOutput = true
            useCaches = false
            connectTimeout = 1000
            setRequestProperty("Content-Type", "multipart/form-data; boundary=----WebKitFormBoundary7MA4YWxkTrZu0gW")
            headers?.forEach { (key, value) ->
                setRequestProperty(key, value)
            }
        }
        (connect.outputStream).use {
            if (headers?.get("Content-Type").isNullOrEmpty()) {
                it.write("------WebKitFormBoundary7MA4YWxkTrZu0gW\r\nContent-Disposition: form-data; name=\"".toByteArray())
                it.write((imgPartName ?: "image").toByteArray())
                it.write("\"; filename=\"blob\"\r\nContent-Type: image/jpeg\r\n\r\n".toByteArray())
                it.write(file)
                form?.let { it1 -> it1(it) }
                it.write("\r\n------WebKitFormBoundary7MA4YWxkTrZu0gW--\r\n".toByteArray())
            } else it.write(file)
        }
        val imageUrl = connect.inputStream.bufferedReader().readText()
        connect.disconnect()
        return imageUrl
    }

    fun upload(site: String, callback: (Data) -> Unit) {
        data = Data()
//        if (true){
//            return Thread{
//                data.jump = false
//                data.itemList.add(Item(null, "测试", "测试", "测试"))
//                data.itemList.add(Item(null, "测试", "测试", "测试"))
//                callback(data)
//            }.apply { start() }
//        }
        Thread {
            when (site) {
                "saucenao" -> data.url = "https://saucenao.com/search.php?url=$imageUrl"
                "google" -> data.url = "https://www.google.com/searchbyimage?client=app&image_url=$imageUrl"
                "yandex" -> data.url = "https://yandex.ru/images/search?rpt=imageview&cbir_page=similar&url=$imageUrl"
                "ascii2d" -> data.url = "https://ascii2d.net/search/url/$imageUrl"

                "百度" -> {
                    var body = post(
                        "https://mtbed.netsons.org/upload.php", mapOf("Origin" to "https://695402.xyz"), null, null
                    )
                    body = body.substring(43, body.length - 3).replace("\\", "")
                    data.url =
                        "https://graph.baidu.com/details?promotion_name=pc_image_shituindex&carousel=0&image=$body"
                }

                "animetrace" -> {
                    data.jump = false
                    val body = post(
                        "https://api.animetrace.com/v1/search", null, "file", null
                    )
                    val data: JSONArray =
                        JSONObject(body).getJSONArray("data").getJSONObject(0).getJSONArray("character")
                    for (i in 0 until data.length()) {
                        val i = data.getJSONObject(i)
                        this@Soutu.data.itemList.add(
                            Item(
                                null,
                                i["character"] as String,
                                i["work"] as String,
                                "https://www.bing.com/images/search?q=" + i["character"] + "+" + i["work"]
                            )
                        )
                    }
                }

                "搜图酱" -> {
                    data.jump = false
                    val m = URL("https://soutubot.moe").openConnection() as HttpURLConnection
                    val n = m.getInputStream().bufferedReader().readText().let {
                        it.substring(it.indexOf("m: ").let { it1 -> it1 + 3..it1 + 15 })
                    }.toLong()
                    val kj = Base64.encodeToString(
                        ((System.currentTimeMillis() / 1000).toBigInteger().pow(2) + (49 + n).toBigInteger()).toString()
                            .toByteArray(), Base64.NO_WRAP
                    ).reversed().replace("=", "")
                    val body = post(
                        "https://soutubot.moe/api/search", mapOf("x-api-key" to kj), "file"
                    ) {
                        it.write("\r\n------WebKitFormBoundary7MA4YWxkTrZu0gW\r\nContent-Disposition: form-data; name=\"factor\"\r\n\r\n1.2".toByteArray())
                    }
                    data.url = "https://soutubot.moe/results/" + body.substring(
                        body.indexOf("id").let { it + 5..it + 20 })
                    val data: JSONArray = JSONObject(body).getJSONArray("data")
                    for (i in 0 until data.length()) {
                        val i = data.getJSONObject(i)
                        if ((i["similarity"] as Number).toDouble() < 40.0) break
                        this@Soutu.data.itemList.add(
                            Item(
                                i["previewImageUrl"] as String,
                                i["title"] as String,
                                "相似度：" + i["similarity"] + "\n来源：" + i["source"],
                                when (i["source"]) {
                                    "nhentai" -> "https://nhentai.net" + (i["subjectPath"] as String)
                                    "ehentai" -> "https://exhentai.org" + (i["subjectPath"] as String)
                                    else -> ""
                                }
                            )
                        )
                    }
                }
            }
            instance = this@Soutu
            callback(data)
        }.start()
    }
}

fun showBrowserSelector(
    context: Context, onCancel: (() -> Unit)? = null
) {
    val packageManager = context.packageManager
    val currentPackageName = context.packageName

    val browserList: List<ResolveInfo> = run {
        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.bing.com"))
        packageManager.queryIntentActivities(browserIntent, PackageManager.MATCH_ALL)
            .filter { it.activityInfo.packageName != currentPackageName }
    }

    val dialog = AlertDialog.Builder(context).create()
    dialog.setTitle("选择默认浏览器")
    dialog.setCancelable(onCancel == null)

    val layout = LinearLayout(context)
    layout.orientation = LinearLayout.HORIZONTAL
    layout.setPadding(16, 16, 16, 16)
    layout.gravity = Gravity.CENTER

    for (browser in browserList) {
        val item = LinearLayout(context)
        item.orientation = LinearLayout.VERTICAL
        item.gravity = Gravity.CENTER
        item.setPadding(16, 16, 16, 16)
        item.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
        )

        val imgView = ImageView(context)
        imgView.scaleType = ImageView.ScaleType.FIT_CENTER
        imgView.layoutParams = ViewGroup.LayoutParams(120, 120)
        imgView.setImageDrawable(browser.loadIcon(packageManager))

        val textView = TextView(context)
        textView.text = browser.loadLabel(packageManager)
        textView.gravity = Gravity.CENTER
        textView.setPadding(0, 8, 0, 0)
        textView.textSize = 14f

        item.addView(imgView)
        item.addView(textView)

        item.setOnClickListener {
            App.sharedPreferences.edit().putString("browser", browser.activityInfo.packageName).apply()
            Toast.makeText(
                context, "已设置${browser.loadLabel(packageManager)}为默认浏览器", Toast.LENGTH_SHORT
            ).show()
            dialog.dismiss()
        }

        layout.addView(item)
    }

    if (onCancel != null) {
        dialog.setOnCancelListener { onCancel() }
    }

    dialog.setView(layout)
    dialog.show()
}
