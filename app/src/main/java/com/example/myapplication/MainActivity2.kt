package com.example.myapplication

import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import com.google.android.material.textfield.TextInputEditText

class MainActivity2 : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main2)
        val button = findViewById<Button>(R.id.button)
        val button1 = findViewById<Button>(R.id.button1)
        val button2 = findViewById<Button>(R.id.button2)
            button2.visibility = Button.VISIBLE
        val i1 = findViewById<TextInputEditText>(R.id.i1)
        val i2 = findViewById<TextInputEditText>(R.id.i2)
        val i3 = findViewById<TextInputEditText>(R.id.i3)
        val i4 = findViewById<TextInputEditText>(R.id.i4)
        val i5 = findViewById<TextInputEditText>(R.id.i5)
        val i = findViewById<TextInputEditText>(R.id.i)
        val show = findViewById<TextView>(R.id.show)


        val item = intent.extras?.getString("item","")

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

            val cursor = db.query("list", null, "id = ?", arrayOf(item), null, null, null)
            cursor.moveToFirst()
            val name = cursor.getString(cursor.getColumnIndexOrThrow("name"))
            val host = cursor.getString(cursor.getColumnIndexOrThrow("host"))
            val Pp = cursor.getString(cursor.getColumnIndexOrThrow("package"))
            val activity = cursor.getString(cursor.getColumnIndexOrThrow("activity"))
            val keys = cursor.getString(cursor.getColumnIndexOrThrow("keys"))
            val datas = cursor.getString(cursor.getColumnIndexOrThrow("datas"))
        if (host==""){
            show.setText("你可以通过 kkp://${item}/ 来打开这个快捷方式")
        }
        cursor.close()
        i.setText(name)
        i5.setText (host)
        i1.setText (Pp)
        i2.setText (activity)
        i3.setText(keys)
        i4.setText(datas)





        button1.setOnClickListener {
            val hy = ContentValues().apply {
                put("name", i.text.toString())
                put("host", i5.text.toString())
                put("package", i1.text.toString())
                put("activity", i2.text.toString())
                put("keys", i3.text.toString())
                put("datas", i4.text.toString())
            }
            db.update("list", hy, "id = ?", arrayOf(item.toString()))
        }

        button.setOnClickListener {
            var ii = "am start -n ${i1.text}/${i2.text}"
            val ii3 = i3.text?.split("\n")
            val ii4 = i4.text?.split("\n")
            if (ii4 != null&&ii3!=null) {
                if (ii3.size==ii4.size){
                    for (rfi in ii3.indices)ii+=" --e${ii3[rfi].replaceRange(1,2," '")}' '${ii4[rfi]}'"}
            }
            ProcessBuilder("su","-c", ii).start()

        }
        button2.setOnClickListener {
            db.delete("list", "id = ?", arrayOf(item.toString()))
            db.close()
            finish()
        }
    }
}