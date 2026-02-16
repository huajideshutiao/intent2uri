package com.shutiao.flow

import android.app.Application
import android.content.ComponentName
import android.content.SharedPreferences
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import rikka.shizuku.Shizuku

class App : Application() {
    companion object {
        lateinit var dbHelper: SQLiteDatabase
        lateinit var sharedPreferences: SharedPreferences

        val args by lazy {
            Shizuku.UserServiceArgs(
                ComponentName(
                    BuildConfig.APPLICATION_ID,
                    UserService::class.java.name
                )
            ).daemon(true)
                .processNameSuffix("service")
                .debuggable(BuildConfig.DEBUG)
                .version(BuildConfig.VERSION_CODE)
        }
        val su by lazy { ProcessBuilder("su").start().outputStream }
    }
    override fun onCreate() {
        super.onCreate()
        dbHelper = DbHelper(this).writableDatabase
        sharedPreferences = getSharedPreferences("list", MODE_PRIVATE)
    }
}

class DbHelper(context: App) : SQLiteOpenHelper(context, "list.db", null, 2) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE list (id INTEGER PRIMARY KEY AUTOINCREMENT,name TEXT,description TEXT,matchRule TEXT,replaceRule TEXT,packageName TEXT,activity TEXT,uri TEXT,extraKey TEXT,extraValue TEXT)")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {

    }
}
