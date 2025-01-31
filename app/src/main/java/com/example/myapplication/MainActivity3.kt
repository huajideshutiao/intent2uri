package com.example.myapplication

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore


class MainActivity3 : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {

        fun getLatestImagePath(): String {
            val contentResolver = this.contentResolver
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

        super.onCreate(savedInstanceState)
        val list = getSharedPreferences("list", MODE_PRIVATE)
        val data = intent.data

        class DbHelper(context: Context) : SQLiteOpenHelper(context, "list.db", null, 1) {
            override fun onCreate(db: SQLiteDatabase) {
                db.execSQL("CREATE TABLE list (id INTEGER PRIMARY KEY AUTOINCREMENT,name TEXT,host TEXT, package TEXT, activity TEXT , keys TEXT, datas TEXT)")
            }

            override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
                db.execSQL("DROP TABLE IF EXISTS users")
                onCreate(db)
            }
        }
            val db = DbHelper(this).writableDatabase

            when (data!!.scheme) {
                "kkp" -> {
                    val i1 = data.authority

                    val cursor = db.query("list", null, "id = ?", arrayOf(i1), null, null, null)
                    cursor.moveToFirst()



                    val Pp = cursor.getString(cursor.getColumnIndexOrThrow("package"))
                    val activity = cursor.getString(cursor.getColumnIndexOrThrow("activity"))
                    val keys = cursor.getString(cursor.getColumnIndexOrThrow("keys"))
                    val datas = cursor.getString(cursor.getColumnIndexOrThrow("datas"))
                    cursor.close()



                    var esd = data.path
                    if (esd == null||esd.length<2){
                        esd = ""
                    }else{
                        while (esd!!.startsWith("/")) {
                            esd = esd.substring(1)
                        }
                        while (esd!!.endsWith("/")) {
                            esd = esd.substring(0, esd.length - 1)
                        }
                    }
                    var ii = "am start -n ${Pp}/${activity}"
                    if (keys != null) {
                        val ii3 = keys.split("\n")
                        val ii4 = datas.split("\n")
                        for (df in 0 until ii3.size) {
                            val ioh= ii4[df].replace("{key}",esd)
                                            .replace("{photo}",getLatestImagePath())
                            ii += " --e${ii3[df].replaceRange(1, 2, " '")}' '${ioh}'"
                        }
                    }

                    ProcessBuilder("su", "-c", ii).start()
                    finish()
                }

                "http", "https" -> {

                    var staut = true
                    val cursor = db.query("list", arrayOf("host"), null, null, null, null, null)
                    val ksh :MutableList<String> = mutableListOf()
                    while (cursor.moveToNext()) {
                        val host = cursor.getString(cursor.getColumnIndexOrThrow("host"))
                        ksh.add(host)
                    }
                    cursor.close()

                    for(i in ksh.indices) {
                        if(ksh[i] == data.authority) {

                            val crsor = db.query("list", null, "id = ?", arrayOf((i+1).toString()), null, null, null)
                            crsor.moveToFirst()
                            val Pp = crsor.getString(crsor.getColumnIndexOrThrow("package"))
                            val activity = crsor.getString(crsor.getColumnIndexOrThrow("activity"))
                            val keys = crsor.getString(crsor.getColumnIndexOrThrow("keys"))
                            val datas = crsor.getString(crsor.getColumnIndexOrThrow("datas"))
                            crsor.close()
                            var ii = "am start -n ${Pp}/${activity}"
                            if (keys != null) {
                                val ii3 = keys.split("\n")
                                val ii4 = datas.split("\n")
                                for (df in 0 until ii3.size) {
                                    ii += " --e${ii3[df].replaceRange(1, 2, " '")}' '${
                                        ii4[df].replace("{key}",data.toString())
                                                .replace("{photo}",getLatestImagePath())
                                    }'"
                                }
                            }
                            ProcessBuilder("su", "-c", ii).start()
                            staut = false
                            finish()
                        }
                    }
                    if (staut){
                        val browser = Intent(Intent.ACTION_VIEW, data)
                        browser.setPackage(list.getString("browser", ""))
                        startActivity(browser)
                        finish()
                    }
                }
            }

    }
}