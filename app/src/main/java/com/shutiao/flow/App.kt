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
        private var suProcess: Process? = null
        private var suStream: java.io.OutputStream? = null

        fun runRootCommand(command: String) {
            val cmd = if (command.endsWith("\n")) command else "$command\n"
            try {
                val isAlive = try {
                    suProcess?.exitValue()
                    false
                } catch (_: IllegalThreadStateException) {
                    true
                }

                if (suProcess == null || suStream == null || !isAlive) {
                    suProcess = ProcessBuilder("su").start()
                    suStream = suProcess!!.outputStream
                }

                suStream?.run {
                    write(cmd.toByteArray())
                    flush()
                }
            } catch (_: Exception) {
                suProcess?.destroy()
                suProcess = null
                suStream = null
                // 如果第一次失败，尝试重新启动一次进程执行
                try {
                    suProcess = ProcessBuilder("su").start()
                    suStream = suProcess!!.outputStream
                    suStream?.run {
                        write(cmd.toByteArray())
                        flush()
                    }
                } catch (e2: Exception) {
                    e2.printStackTrace()
                }
            }
        }
    }
    override fun onCreate() {
        super.onCreate()
        dbHelper = DbHelper(this).writableDatabase
        sharedPreferences = getSharedPreferences("list", MODE_PRIVATE)
    }

}

class DbHelper(context: App) : SQLiteOpenHelper(context, "list.db", null, 5) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE list (id INTEGER PRIMARY KEY AUTOINCREMENT,name TEXT,description TEXT,matchRule TEXT,replaceRule TEXT,packageName TEXT,activity TEXT,uri TEXT,extraKey TEXT,extraValue TEXT,sort_order INTEGER DEFAULT 0,iconType TEXT,iconValue TEXT,showInAssistant INTEGER DEFAULT 0)")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 3) {
            db.execSQL("ALTER TABLE list ADD COLUMN sort_order INTEGER DEFAULT 0")
        }
        if (oldVersion < 4) {
            db.execSQL("ALTER TABLE list ADD COLUMN iconType TEXT")
            db.execSQL("ALTER TABLE list ADD COLUMN iconValue TEXT")
        }
        if (oldVersion < 5) {
            db.execSQL("ALTER TABLE list ADD COLUMN showInAssistant INTEGER DEFAULT 0")
        }
    }
}
