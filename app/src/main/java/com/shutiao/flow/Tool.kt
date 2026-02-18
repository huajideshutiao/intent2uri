package com.shutiao.flow

import android.content.ComponentName
import android.content.ContentValues
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import rikka.shizuku.Shizuku
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL

data class OpenLink(
    val name: String,
    val description: String,
    val matchRule: String,
    val replaceRule: String,
    val packageName: String,
    val activity: String,
    var uri: String,
    val extraKey: String,
    var extraValue: String
) {
    var id: String = ""

    companion object {

        private var _datas: MutableList<OpenLink>? = null

        val datas: MutableList<OpenLink>
            get() {
                if (_datas != null) return _datas!!
                getDatas()
                return _datas!!
            }

        fun getDatas() {
            val list = mutableListOf<OpenLink>()
            val cursor = App.dbHelper.query("list", null, null, null, null, null, null)
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
                    cursor.getString(9)
                )
                tmp.id = cursor.getString(0)
                list.add(tmp)
                cursor.moveToNext()
            }
            cursor.close()
            _datas = list
        }

        fun fromString(backup: String): OpenLink {
            val op = backup.substring(8, backup.length - 1).split(", ")
            return OpenLink(
                op[0].substringAfter("="),
                op[1].substringAfter("="),
                op[2].substringAfter("="),
                op[3].substringAfter("="),
                op[4].substringAfter("="),
                op[5].substringAfter("="),
                op[6].substringAfter("="),
                op[7].substringAfter("="),
                op[8].substringAfter("=")
            )
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
            if (matchRule.isNotEmpty()) {
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
                    ii.append(" --e").append(keys[i].replaceRange(1, 2, " '"))
                        .append("' '").append(values[i]).append('\'')
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
                Shizuku.addRequestPermissionResultListener(
                    object : Shizuku.OnRequestPermissionResultListener {
                        override fun onRequestPermissionResult(requestCode: Int, grantResult: Int) {
                            if (grantResult == PackageManager.PERMISSION_GRANTED) {
                                Shizuku.bindUserService(App.args, conn)
                            } else {
                                App.su.write(command.toByteArray())
                                App.su.flush()
                            }
                            Shizuku.removeRequestPermissionResultListener(this)
                        }
                    }
                )
                Shizuku.requestPermission(0)
            }
        } catch (_: Exception) {
            App.su.write(command.toByteArray())
            App.su.flush()
        }
    }
}

fun item(column: String): Pair<List<String>, List<String>> {
    val cursor = App.dbHelper.query(
        "list",
        arrayOf("id", column),
        null,
        null,
        null,
        null,
        null
    )
    val idList = mutableListOf<String>()
    val dataList = mutableListOf<String>()

    if (cursor.moveToFirst()) {
        do {
            val id = cursor.getString(cursor.getColumnIndexOrThrow("id"))
            val data = cursor.getString(cursor.getColumnIndexOrThrow(column))
            idList.add(id)
            dataList.add(data)
        } while (cursor.moveToNext())
    }
    cursor.close()
    return Pair(idList, dataList)
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
            mapOf("Content-Type" to "image/jpeg"), null, null
        )
        "https://avatars.mds.yandex.net/get-images-cbir/" + imageUrl.substring(
            15, imageUrl.indexOf('"', 16)
        ) + "/orig"
    }
    var data = Data()

    private fun post(
        url: String,
        headers: Map<String, String>?,
        imgPartName: String?,
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
//        if (true){
//            return Thread{
//                data.jump = false
//                data.itemList.add(Item(null, "测试", "测试", "测试"))
//                data.itemList.add(Item(null, "测试", "测试", "测试"))
//                callback(data)
//            }.apply { start() }
//        }
        return Thread {
            when (site) {
                "saucenao" -> data.url = "https://saucenao.com/search.php?url=$imageUrl"
                "google" -> data.url = "https://www.google.com/searchbyimage?client=app&image_url=$imageUrl"
                "yandex" -> data.url = "https://yandex.ru/images/search?rpt=imageview&cbir_page=similar&url=$imageUrl"
                "ascii2d" -> data.url = "https://ascii2d.net/search/url/$imageUrl"

                "百度" -> {
                    var body = post(
                        "https://mtbed.netsons.org/upload.php",
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
