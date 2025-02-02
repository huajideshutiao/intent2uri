package com.example.myapplication

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.net.Uri
import android.provider.MediaStore

data class OpenLink(val name: String = "", val host: String, val pp: String, val activity: String, val keys: String, var datas: String, val change2: String, var uri: String) {

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

        fun toDb(openLink: OpenLink,db: SQLiteDatabase, id :String = "") {
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
            if (id == "")db.insert("list", null, hy)
            else db.update("list", hy, "id = ?", arrayOf(id))
        }
    }
}

class DbHelper(context: Context) : SQLiteOpenHelper(context, "list.db", null, 2) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE list (id INTEGER PRIMARY KEY AUTOINCREMENT,name TEXT,host TEXT, package TEXT, activity TEXT , keys TEXT, datas TEXT, change2 TEXT, uri TEXT)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("ALTER TABLE list ADD change2 TEXT")
        db.execSQL("ALTER TABLE list ADD uri TEXT")
    }
}

fun item(db: SQLiteDatabase,column:String) : List<String> {
    val cursor = db.query("list", arrayOf("id",column), null, null, null, null, null)
    val lk :MutableList<String> = mutableListOf()
    while (cursor.moveToNext()) {
        val name = cursor.getString(cursor.getColumnIndexOrThrow(column))
        lk.add(name)
        val id = cursor.getString(cursor.getColumnIndexOrThrow("id"))
        lk.add(id)
    }
    cursor.close()
    return lk
}


fun getLatestImagePath(context: Context): String {
    val contentResolver = context.contentResolver
    var latestImagePath = ""
    val projection = arrayOf(MediaStore.Images.Media.DATA)
    val sortOrder = "${MediaStore.Images.Media.DATE_TAKEN} DESC"
    val uri: Uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI

    val cursor: Cursor? = contentResolver.query(
        uri,
        projection,
        null,
        null,
        sortOrder
    )

    cursor?.use {
        if (it.moveToFirst()) {
            latestImagePath = it.getString(it.getColumnIndexOrThrow(MediaStore.Images.Media.DATA))
        }
    }
    return latestImagePath
}

fun openLink(openLink: OpenLink) {



    var ii = "am start -a android.intent.action.VIEW"
    openLink.apply {
        if (pp != "") ii += " -n $pp"
        if (activity != "") ii += '/' + activity
        if (uri != "") ii += " -d " + uri
        if (keys != "") {
            val ii3 = keys.split("\n")
            val ii4 = datas.split("\n")
            for (df in ii3.indices) {
                ii += " --e${ii3[df].replaceRange(1, 2, " '")}' '${ii4[df]}'"
        }

    }
        ProcessBuilder("su", "-c", ii).start()
}
}