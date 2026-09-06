package com.shutiao.flow

import android.app.Activity
import android.app.AlertDialog
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.net.Uri
import android.text.Editable
import android.text.TextWatcher
import android.util.Base64
import android.util.Log
import android.util.LruCache
import android.util.Patterns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject
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
    var extra: String,
    val iconType: String = "app",
    val iconValue: String = "",
    val showInAssistant: Boolean = false
) {
    var id: String = ""

    val matchRegex: Regex? by lazy {
        if (matchRule.isNotEmpty()) runCatching { Regex(matchRule) }.getOrNull() else null
    }

    private val cacheKey: String
        get() = when (iconType) {
            "text" -> "text_${iconValue.ifEmpty { name.take(1) }}"
            "image" -> "img_$iconValue"
            else -> "pkg_$iconPackage"
        }

    // app 图标的取源顺序：图标专用包名 → 规则目标包名 → 默认浏览器
    private val iconPackage: String
        get() = iconValue.ifEmpty {
            packageName.ifEmpty { App.sharedPreferences.getString("browser", "") ?: "" }
        }

    fun getIconBitmap(context: Context): Bitmap {
        val key = cacheKey
        memoryCache.get(key)?.let { return it }

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
                context.packageManager.getApplicationIcon(iconPackage).apply {
                    setBounds(0, 0, size, size)
                    draw(canvas)
                }
            } catch (_: Exception) {
                drawDefaultIcon(context, canvas, size)
            }
        }

        memoryCache.put(key, bitmap)
        return bitmap
    }

    fun loadIconAsync(context: Context, imageView: ImageView) {
        loadBitmapAsync(cacheKey, imageView) { getIconBitmap(context) }
    }

    private fun drawDefaultIcon(context: Context, canvas: Canvas, size: Int) {
        context.packageManager.getApplicationIcon(context.packageName).apply {
            setBounds(0, 0, size, size)
            draw(canvas)
        }
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("name", name); put("description", description); put("matchRule", matchRule)
        put("replaceRule", replaceRule); put("packageName", packageName); put("activity", activity)
        put("uri", uri); put("extra", extra)
        put("iconType", iconType); put("iconValue", iconValue)
        put("showInAssistant", showInAssistant)
    }

    companion object {
        // 图标与远端缩略图共用一份缓存与一个线程池
        private val memoryCache = object : LruCache<String, Bitmap>(20 * 1024 * 1024) {
            override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
        }
        private val bitmapExecutor = Executors.newFixedThreadPool(4)

        private val dataLock = Any()

        fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
            var inSampleSize = 1
            while (options.outHeight / inSampleSize / 2 >= reqHeight &&
                options.outWidth / inSampleSize / 2 >= reqWidth
            ) inSampleSize *= 2
            return inSampleSize
        }

        /** 位图异步加载唯一入口：命中缓存直接回填，否则清空占位并在后台生产；tag 守卫防止复用视图错位。 */
        fun loadBitmapAsync(cacheKey: String, imageView: ImageView, loadBitmap: () -> Bitmap?) {
            imageView.tag = cacheKey
            memoryCache.get(cacheKey)?.let { imageView.setImageBitmap(it); return }
            imageView.setImageDrawable(null)
            bitmapExecutor.execute {
                val bitmap = loadBitmap() ?: return@execute
                memoryCache.put(cacheKey, bitmap)
                imageView.post { if (imageView.tag == cacheKey) imageView.setImageBitmap(bitmap) }
            }
        }

        fun execute(task: () -> Unit) = bitmapExecutor.execute(task)

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

        fun clearIconCache(id: String) {
            datas.find { it.id == id }?.let { item ->
                memoryCache.remove(item.cacheKey)
            }
        }

        private var _datas: MutableList<OpenLink>? = null
        val datas: MutableList<OpenLink>
            get() = synchronized(dataLock) { _datas ?: run { loadDatas(); _datas!! } }

        fun getDatas() = synchronized(dataLock) { loadDatas() }

        private fun loadDatas() {
            _datas = App.db.query("list", null, null, null, null, null, "sort_order ASC").use { c ->
                val cId = c.getColumnIndexOrThrow("id")
                val cName = c.getColumnIndexOrThrow("name")
                val cDescription = c.getColumnIndexOrThrow("description")
                val cMatchRule = c.getColumnIndexOrThrow("matchRule")
                val cReplaceRule = c.getColumnIndexOrThrow("replaceRule")
                val cPackageName = c.getColumnIndexOrThrow("packageName")
                val cActivity = c.getColumnIndexOrThrow("activity")
                val cUri = c.getColumnIndexOrThrow("uri")
                val cExtra = c.getColumnIndexOrThrow("extra")
                val cIconType = c.getColumnIndexOrThrow("iconType")
                val cIconValue = c.getColumnIndexOrThrow("iconValue")
                val cShowInAssistant = c.getColumnIndexOrThrow("showInAssistant")
                mutableListOf<OpenLink>().apply {
                    while (c.moveToNext()) {
                        add(
                            OpenLink(
                                c.getString(cName),
                                c.getString(cDescription),
                                c.getString(cMatchRule),
                                c.getString(cReplaceRule),
                                c.getString(cPackageName),
                                c.getString(cActivity),
                                c.getString(cUri),
                                c.getString(cExtra) ?: "",
                                c.getString(cIconType) ?: "app",
                                c.getString(cIconValue) ?: "",
                                c.getInt(cShowInAssistant) == 1
                            ).apply { id = c.getString(cId) }
                        )
                    }
                }
            }
        }

        // 仅供 backup 导入兼容旧格式（旧版本备份分两个字段）。新存储统一用合并后的 extra 列。
        internal fun joinExtra(rawKey: String?, rawValue: String?): String {
            if (rawKey.isNullOrEmpty()) return ""
            val keys = rawKey.split("\n")
            val values = (rawValue ?: "").split("\n")
            return keys.mapIndexed { i, k ->
                val v = values.getOrNull(i) ?: ""
                if (v.isEmpty()) k else "$k=$v"
            }.joinToString("\n")
        }

        /** 备份文件与剪贴板 JSON 的统一入口，缺字段取默认值。 */
        fun fromJson(json: JSONObject) = OpenLink(
            name = json.optString("name"),
            description = json.optString("description"),
            matchRule = json.optString("matchRule"),
            replaceRule = json.optString("replaceRule"),
            packageName = json.optString("packageName"),
            activity = json.optString("activity"),
            uri = json.optString("uri"),
            extra = if (json.has("extra")) json.optString("extra")
            else joinExtra(json.optString("extraKey"), json.optString("extraValue")),
            iconType = json.optString("iconType", "app"),
            iconValue = json.optString("iconValue"),
            showInAssistant = json.optBoolean("showInAssistant")
        )

        fun updateOrder() {
            synchronized(dataLock) {
                _datas?.forEachIndexed { index, item ->
                    val values = ContentValues().apply { put("sort_order", index) }
                    App.db.update("list", values, "id = ?", arrayOf(item.id))
                }
            }
        }

        fun delete(id: String) {
            App.db.delete("list", "id = ?", arrayOf(id))
            synchronized(dataLock) { _datas?.removeIf { it.id == id } }
        }

        fun openExternal(context: Context, intent: Intent) {
            val resolveInfo = context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
            if (resolveInfo?.activityInfo?.packageName == context.packageName || resolveInfo == null) {
                intent.setPackage(App.sharedPreferences.getString("browser", ""))
            }
            if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                context.startActivity(intent)
            } catch (_: Exception) {
                if (intent.getPackage() != null) {
                    intent.setPackage(null)
                    try {
                        context.startActivity(intent)
                    } catch (_: Exception) {
                    }
                }
            }
        }

        fun smartSearch(context: Context, key: String, item: OpenLink? = null, intent: Intent? = null) {
            val target = item ?: datas.find { it.matchRegex?.containsMatchIn(key) == true }
            if (target != null) {
                target.start(key)
            } else if (intent != null) {
                openExternal(context, intent)
            } else if (key.isNotEmpty()) {
                if (Patterns.WEB_URL.matcher(key).matches()) {
                    val uri = try {
                        if (key.contains("://")) Uri.parse(key) else Uri.parse("http://$key")
                    } catch (_: Exception) {
                        null
                    }
                    if (uri != null) openExternal(context, Intent(Intent.ACTION_VIEW, uri))
                } else {
                    openExternal(context, Intent(Intent.ACTION_WEB_SEARCH).putExtra("query", key))
                }
            }
        }
    }

    fun save(id: String? = "") {
        val item = ContentValues().apply {
            put("name", name); put("description", description); put("matchRule", matchRule)
            put("replaceRule", replaceRule); put("packageName", packageName); put("activity", activity)
            put("uri", uri); put("extra", extra)
            put("iconType", iconType); put("iconValue", iconValue)
            put("showInAssistant", if (showInAssistant) 1 else 0)
        }
        if (id.isNullOrEmpty()) {
            App.db.insert("list", null, item)
            getDatas()
        } else {
            App.db.update("list", item, "id = ?", arrayOf(id))
            synchronized(dataLock) {
                datas.indexOfFirst { it.id == id }.takeIf { it != -1 }?.let {
                    this.id = id
                    datas[it] = this
                }
            }
        }
    }

    fun start(keyWord: String) {
        val command = if (replaceRule.startsWith("shell:")) {
            replaceRule.substringAfter("shell:").replace("{key}", keyWord)
        } else buildString {
            val processedKey = matchRegex?.takeIf { replaceRule.isNotEmpty() }
                ?.replace(keyWord, replaceRule) ?: keyWord
            append("am start -a android.intent.action.VIEW")
            if (packageName.isNotEmpty()) append(" -n $packageName")
            if (activity.isNotEmpty()) append("/$activity")
            if (uri.isNotEmpty()) {
                append(" -d '")
                append(uri.replace("{key}", processedKey).shellSingleQuote())
                append("'")
            }
            if (extra.isNotEmpty()) {
                // 每行格式 "s.key=value"：首字符是类型 (s/i/z/...)，'.' 分隔，'=' 之后是值
                extra.replace("{key}", processedKey).split("\n").forEach { line ->
                    if (line.length < 2 || line[1] != '.') return@forEach
                    val eq = line.indexOf('=')
                    val typeChar = when (line[0].lowercaseChar()) {
                        'b' -> 'z'
                        else -> line[0].lowercaseChar()
                    }
                    val keyEnd = if (eq >= 0) eq else line.length
                    val keyName = line.substring(2, keyEnd)
                    if (keyName.isEmpty()) return@forEach
                    val value = if (eq >= 0) line.substring(eq + 1) else ""
                    append(" --e").append(typeChar)
                    append(" '").append(keyName.shellSingleQuote()).append("'")
                    append(" '").append(value.shellSingleQuote()).append("'")
                }
            }
            append(" > /dev/null 2>&1\n")
        }

        ShellExec.exec(command, requestPermissionIfNeeded = true)
    }
}

data class Item(val img: String?, val title: String, val description: String, val link: String)
data class Data(
    val successful: Boolean = true,
    val itemList: MutableList<Item> = mutableListOf(),
    var jump: Boolean = true,
    var url: String = ""
)

/** item.xml 的唯一绑定入口，主页卡片与搜图结果行共用。 */
class ItemViewHolder(view: View) {
    val icon: ImageView = view.findViewById(R.id.imageView4)
    val title: TextView = view.findViewById(R.id.name)
    val description: TextView = view.findViewById(R.id.description)
}

/** 只关心文本变化的监听，避开三方法 TextWatcher 样板。 */
fun EditText.onTextChanged(action: (String) -> Unit) {
    addTextChangedListener(object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = action(s?.toString() ?: "")
        override fun afterTextChanged(s: Editable?) {}
    })
}

private fun String.shellSingleQuote(): String = replace("'", "'\\''")

fun List<Item>.encodeToIntent(): String = JSONArray().apply {
    this@encodeToIntent.forEach { item ->
        put(JSONObject().apply {
            put("img", item.img ?: JSONObject.NULL)
            put("title", item.title)
            put("description", item.description)
            put("link", item.link)
        })
    }
}.toString()

fun decodeItemsFromIntent(s: String?): List<Item> {
    if (s.isNullOrEmpty()) return emptyList()
    val arr = JSONArray(s)
    return List(arr.length()) {
        val o = arr.getJSONObject(it)
        Item(
            if (o.isNull("img")) null else o.getString("img"),
            o.getString("title"),
            o.getString("description"),
            o.getString("link"),
        )
    }
}

class Soutu(val file: ByteArray, private val context: Context) {
    private val imageUrl by lazy {
        val res = post(
            "https://yandex.com/images-apphost/image-download?cbird=117&images_avatars_size=preview&images_avatars_namespace=images-cbir",
            mapOf("Content-Type" to "image/jpeg"),
            null,
            null
        )
        "https://avatars.mds.yandex.net/get-images-cbir/${res.substring(15, res.indexOf('"', 16))}/orig"
    }

    companion object {
        // 搜图请求会堵在网络上（connectTimeout 3s），与位图线程池分开以免饰卡图标/缩略图加载
        private val executor = Executors.newCachedThreadPool()
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
        executor.execute {
            val result = try {
                search(site)
            } catch (e: Exception) {
                // 网络/解析失败走 successful=false 回传调用方提示，不让异常抛到线程池外杀进程
                Log.w("Soutu", "search failed: $site", e)
                Data(successful = false)
            }
            callback(result)
        }
    }

    private fun search(site: String): Data {
        val data = Data()
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
                            context.getString(R.string.similarity_format, item.get("similarity"), item.getString("source")),
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
        return data
    }
}

fun showBrowserSelector(activity: Activity, onCancel: (() -> Unit)? = null) {
    val progressDialog = AlertDialog.Builder(activity)
        .setTitle(activity.getString(R.string.loading_browsers))
        .setView(android.widget.ProgressBar(activity).apply { setPadding(50, 50, 50, 50) })
        .setCancelable(onCancel != null)
        .apply { onCancel?.let { setOnCancelListener { it() } } }
        .show()

    Thread {
        val pm = activity.packageManager
        val browserList = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.bing.com")).let {
            pm.queryIntentActivities(it, PackageManager.MATCH_ALL)
        }.filter { it.activityInfo.packageName != activity.packageName }

        activity.runOnUiThread {
            progressDialog.dismiss()
            if (browserList.isEmpty()) {
                Toast.makeText(activity, activity.getString(R.string.no_browser_found), Toast.LENGTH_SHORT).show()
                return@runOnUiThread
            }

            val density = activity.resources.displayMetrics.density
            val iconSize = (64 * density).toInt()

            val layout = GridLayout(activity).apply {
                columnCount = 4
                useDefaultMargins = true
                setPadding(16, 16, 16, 16)
            }

            val dialog = AlertDialog.Builder(activity)
                .setTitle(activity.getString(R.string.select_default_browser))
                .setCancelable(onCancel == null)
                .setView(ScrollView(activity).apply { addView(layout) })
                .create()

            val inflater = LayoutInflater.from(activity)
            browserList.forEach { browser ->
                // 与助手图标行共用 item_icon_label，仅图标尺寸与字号按对话框较大的版式覆盖
                inflater.inflate(R.layout.item_icon_label, layout, false).apply {
                    setPadding(8, 8, 8, 8)
                    layoutParams = GridLayout.LayoutParams().apply {
                        width = 0
                        height = ViewGroup.LayoutParams.WRAP_CONTENT
                        columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                    }
                    findViewById<ImageView>(R.id.icon).apply {
                        layoutParams = layoutParams.apply { width = iconSize; height = iconSize }
                        // 对话框里图标框比助手行大，用 FIT_CENTER 让小图也能填满
                        scaleType = ImageView.ScaleType.FIT_CENTER
                        setImageDrawable(browser.loadIcon(pm))
                    }
                    findViewById<TextView>(R.id.name).apply {
                        textSize = 14f
                        text = browser.loadLabel(pm)
                    }
                    setOnClickListener {
                        App.sharedPreferences.edit().putString("browser", browser.activityInfo.packageName).apply()
                        Toast.makeText(
                            activity,
                            activity.getString(R.string.browser_set, browser.loadLabel(pm)),
                            Toast.LENGTH_SHORT
                        ).show()
                        dialog.dismiss()
                    }
                    layout.addView(this)
                }
            }
            onCancel?.let { dialog.setOnCancelListener { it() } }
            dialog.show()
        }
    }.start()
}
