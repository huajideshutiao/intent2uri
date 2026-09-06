package com.shutiao.flow

import android.app.Application
import android.content.ComponentName
import android.content.ContentValues
import android.content.SharedPreferences
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import rikka.shizuku.Shizuku
import java.io.OutputStream

class App : Application() {
    companion object {
        lateinit var db: SQLiteDatabase
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
        private var suStream: OutputStream? = null

        @Synchronized
        fun runRootCommand(command: String) {
            val cmd = if (command.endsWith("\n")) command else "$command\n"
            if (!tryWriteCommand(cmd)) {
                suProcess?.destroy()
                suProcess = null
                suStream = null
                tryWriteCommand(cmd)
            }
        }

        private fun tryWriteCommand(cmd: String): Boolean = try {
            val alive = suProcess?.let {
                try {
                    it.exitValue(); false
                } catch (_: IllegalThreadStateException) {
                    true
                }
            } == true
            if (!alive || suStream == null) {
                suProcess = ProcessBuilder("su").start()
                suStream = suProcess!!.outputStream
            }
            suStream!!.run {
                write(cmd.toByteArray())
                flush()
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    override fun onCreate() {
        super.onCreate()
        db = DbHelper(this).writableDatabase
        sharedPreferences = getSharedPreferences("list", MODE_PRIVATE)
        AssistantHealer.checkAndRepair(this)
    }
}

class DbHelper(context: App) : SQLiteOpenHelper(context, "list.db", null, 6) {
    private companion object {
        // 建表列清单只此一份，onCreate 与 v6 迁移重建表共用
        const val COLUMNS =
            "id INTEGER PRIMARY KEY AUTOINCREMENT,name TEXT,description TEXT,matchRule TEXT," +
                "replaceRule TEXT,packageName TEXT,activity TEXT,uri TEXT,extra TEXT," +
                "sort_order INTEGER DEFAULT 0,iconType TEXT,iconValue TEXT,showInAssistant INTEGER DEFAULT 0"
        const val COPY_COLUMNS =
            "id,name,description,matchRule,replaceRule,packageName,activity,uri,extra," +
                "sort_order,iconType,iconValue,showInAssistant"
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE list ($COLUMNS)")
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
        if (oldVersion < 6) {
            // 把 extraKey/extraValue 两列按行合并为 "s.key=value" 形写入新列，然后重建表删掉旧列
            db.execSQL("ALTER TABLE list ADD COLUMN extra TEXT")
            db.query("list", arrayOf("id", "extraKey", "extraValue"), null, null, null, null, null).use { c ->
                val cId = c.getColumnIndexOrThrow("id")
                val cK = c.getColumnIndexOrThrow("extraKey")
                val cV = c.getColumnIndexOrThrow("extraValue")
                while (c.moveToNext()) {
                    val joined = OpenLink.joinExtra(c.getString(cK), c.getString(cV))
                    val cv = ContentValues().apply { put("extra", joined) }
                    db.update("list", cv, "id = ?", arrayOf(c.getString(cId)))
                }
            }
            db.execSQL("CREATE TABLE list_new ($COLUMNS)")
            db.execSQL("INSERT INTO list_new ($COPY_COLUMNS) SELECT $COPY_COLUMNS FROM list")
            db.execSQL("DROP TABLE list")
            db.execSQL("ALTER TABLE list_new RENAME TO list")
        }
    }
}
