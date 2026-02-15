package com.shutiao.flow

import android.content.ComponentName
import android.content.ContentValues
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.os.IBinder
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import rikka.shizuku.Shizuku
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL

data class OpenLink(
    val name: String = "",
    val host: String,
    val pp: String,
    val activity: String,
    val keys: String,
    var datas: String,
    val change2: String,
    var uri: String
) {
    companion object {
        fun fromDb(db: SQLiteDatabase, id: String): OpenLink {
            val cursor = db.query("list", null, "id = ?", arrayOf(id), null, null, null)
            cursor.moveToFirst()
            val name = cursor.getString(cursor.getColumnIndexOrThrow("name"))
            val host = cursor.getString(cursor.getColumnIndexOrThrow("host"))
            val change2 = cursor.getString(cursor.getColumnIndexOrThrow("change2"))
            val pp = cursor.getString(cursor.getColumnIndexOrThrow("package"))
            val activity = cursor.getString(cursor.getColumnIndexOrThrow("activity"))
            val keys = cursor.getString(cursor.getColumnIndexOrThrow("keys"))
            val datas = cursor.getString(cursor.getColumnIndexOrThrow("datas"))
            val uri = cursor.getString(cursor.getColumnIndexOrThrow("uri"))
            cursor.close()
            return OpenLink(name, host, pp, activity, keys, datas, change2, uri)
        }

        fun toDb(openLink: OpenLink, db: SQLiteDatabase, id: String = "") {
            val hy = ContentValues().apply {
                put("name", openLink.name)
                put("host", openLink.host)
                put("package", openLink.pp)
                put("activity", openLink.activity)
                put("keys", openLink.keys)
                put("datas", openLink.datas)
                put("change2", openLink.change2)
                put("uri", openLink.uri)
            }
            if (id == "") db.insert("list", null, hy)
            else db.update("list", hy, "id = ?", arrayOf(id))
        }
    }
}

class DbHelper private constructor(context: Context) : SQLiteOpenHelper(context, "list.db", null, 2) {
    companion object {
        @Volatile
        private var instance: DbHelper? = null
        fun getInstance(context: Context): DbHelper {
            return instance ?: synchronized(this) {
                instance ?: DbHelper(context.applicationContext).also { instance = it }
            }
        }
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE list (id INTEGER PRIMARY KEY AUTOINCREMENT,name TEXT,host TEXT, package TEXT, activity TEXT , keys TEXT, datas TEXT, change2 TEXT, uri TEXT)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("ALTER TABLE list ADD change2 TEXT")
        db.execSQL("ALTER TABLE list ADD uri TEXT")
    }
}

fun item(db: SQLiteDatabase, column: String): List<String> {
    val cursor = db.query(
        "list",
        arrayOf("id", column),
        null,
        null,
        null,
        null,
        null
    )
    val lk = mutableListOf<String>()
    if (cursor.moveToFirst()) {
        do {
            val name = cursor.getString(cursor.getColumnIndexOrThrow(column))
            val id = cursor.getInt(cursor.getColumnIndexOrThrow("id")).toString()
            lk.add(name)
            lk.add(id)
        } while (cursor.moveToNext())
    }
    cursor.close()
    return lk
}

object Constant {
    val args by lazy { Shizuku.UserServiceArgs(
        ComponentName(
            BuildConfig.APPLICATION_ID,
            UserService::class.java.name
        )
    ).daemon(true)
        .processNameSuffix("service")
        .debuggable(false)
        .version(BuildConfig.VERSION_CODE) }
}

fun openLink(keyWord: String, openLink: OpenLink) {
    val ii = StringBuilder("am start -a android.intent.action.VIEW")
    openLink.apply {
        var keyWord = keyWord
        if (change2.isNotEmpty()) {
            val lines = change2.split("\n")
            keyWord = keyWord.replace(Regex(lines[0]), lines[1])
        }
        if (keys != "") datas = datas.replace("{key}", keyWord)

        if (pp != "") ii.append(" -n ").append(pp)
        if (activity != "") ii.append("/").append(activity)
        if (uri != "") ii.append(" -d ").append(uri.replace("{key}", keyWord))
        if (keys != "") {
            val ii3 = keys.split("\n")
            val ii4 = datas.split("\n")
            for (df in ii3.indices) {
                ii.append(" --e").append(ii3[df].replaceRange(1, 2, " '"))
                    .append("' '").append(ii4[df]).append('\'')
            }
        }
        val command = ii.toString()
        try {
            val useShizuku = Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
            val conn = object : ServiceConnection {
                override fun onServiceConnected(componentName: ComponentName, iBinder: IBinder) {
                    IUserService.Stub.asInterface(iBinder).exec(command)
                    Shizuku.unbindUserService(Constant.args, this, false)
                }
                override fun onServiceDisconnected(p0: ComponentName?) {}
            }
            if (useShizuku) {
                Shizuku.bindUserService(Constant.args, conn)
            } else {
                Shizuku.addRequestPermissionResultListener(
                    object : Shizuku.OnRequestPermissionResultListener {
                        override fun onRequestPermissionResult(requestCode: Int, grantResult: Int) {
                            if (grantResult == PackageManager.PERMISSION_GRANTED) {
                                try{
                                Shizuku.bindUserService(Constant.args, conn)
                            } else ProcessBuilder("su", "-c", command).start()
                            }finally{
                                Shizuku.removeRequestPermissionResultListener(this)}
                        }
                    }
                )
                Shizuku.requestPermission(0)
            }
        }catch (_: Exception) {
            ProcessBuilder("su", "-c", command).start()
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

object Soutu {
    var file: ByteArray? = null
    private val imageUrl by lazy {
        var imageUrl = post(
            "https://yandex.com/images-apphost/image-download?cbird=117&images_avatars_size=preview&images_avatars_namespace=images-cbir",
            mapOf("Content-Type" to "image/jpeg"),
            null,
            null
        )

        "https://avatars.mds.yandex.net/get-images-cbir/" + imageUrl.subSequence(
            15,
            imageUrl.indexOf('"', 16)
        ) + "/orig"
    }
    var data = Data()

    private fun post(
        url: String,
        headers: Map<String, String>?,
        imgPartName: String? = "image",
        form: ((OutputStream) -> Unit)?
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

    fun upload(site: String, callback: (Data) -> Unit): Thread {
        data = Data()
        return Thread {
            when (site) {
                "saucenao" -> data.url = "https://saucenao.com/search.php?url=$imageUrl"
                "google" -> data.url = "https://www.google.com/searchbyimage?client=app&image_url=$imageUrl"
                "yandex" -> data.url = "https://yandex.ru/images/search?rpt=imageview&cbir_page=similar&url=$imageUrl"
                "ascii2d" -> data.url = "https://ascii2d.net/search/url/$imageUrl"

                "百度" -> {
                    var body = post(
                        "https://aapi.helioho.st/upload.php",
                        mapOf("Origin" to "https://695402.xyz"), null, null
                    )
                    body = body.substring(43, body.length - 3).replace("\\", "")
                    data.url =
                        "https://graph.baidu.com/details?promotion_name=pc_image_shituindex&carousel=0&image=$body"
                }

                "animetrace" -> {
                    data.jump = false
                    val body = post(
                        "https://api.animetrace.com/v1/search",
                        null, "file", null
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
                        ((System.currentTimeMillis() / 1000).toBigInteger()
                            .pow(2) + (49 + n).toBigInteger()).toString()
                            .toByteArray(),
                        Base64.NO_WRAP
                    ).reversed().replace("=", "")
                    val body = post(
                        "https://soutubot.moe/api/search",
                        mapOf("x-api-key" to kj),
                        "file"
                    ) {
                        it.write("\r\n------WebKitFormBoundary7MA4YWxkTrZu0gW\r\nContent-Disposition: form-data; name=\"factor\"\r\n\r\n1.2".toByteArray())
                    }
                    data.url = "https://soutubot.moe/results/" + body.substring(
                        body.indexOf("id").let { it + 5..it + 20 })
                    val data: JSONArray = JSONObject(body).getJSONArray("data")
                    for (i in 0 until data.length()) {
                        val i = data.getJSONObject(i)
                        if ((i["similarity"] as Double) < 40.0) break
                        this@Soutu.data.itemList.add(
                            Item(
                                i["previewImageUrl"] as String,
                                i["title"] as String,
                                "相似度：" + i["similarity"] + "\n来源：" + i["source"],
                                when (i["source"]) {
                                    "nhentai" -> "https://nhentai.net" + i["subjectPath"] as String
                                    "ehentai" -> "https://exhentai.org" + i["subjectPath"] as String
                                    else -> ""
                                }
                            )
                        )
                    }
                }
            }
            callback(data)
        }.apply {
            start()
        }
    }
}
