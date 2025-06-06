package com.example.myapplication

import android.app.Activity
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
            button.visibility = Button.VISIBLE
        val i1 = findViewById<TextInputEditText>(R.id.i1)
        val i2 = findViewById<TextInputEditText>(R.id.i2)
        val i3 = findViewById<TextInputEditText>(R.id.i3)
        val i4 = findViewById<TextInputEditText>(R.id.i4)
        val i5 = findViewById<TextInputEditText>(R.id.i5)
        val i6 = findViewById<TextInputEditText>(R.id.i6)
        val i7 = findViewById<TextInputEditText>(R.id.i7)
        val i = findViewById<TextInputEditText>(R.id.i)
        val show = findViewById<TextView>(R.id.show)


        val item = intent.extras?.getString("item","")
        val db = DbHelper(this).writableDatabase

        OpenLink.fromDb(db,item!!).apply {
        if (host==""){
            show.text = "你可以通过 kkp://${item}/ 来打开这个快捷方式"
        }
        i.setText(name)
        i5.setText (host)
        i1.setText (pp)
        i2.setText (activity)
        i3.setText(keys)
        i4.setText(datas)
        i6.setText(change2.joinToString("\n"))
        i7.setText(uri)
        }





        button1.setOnClickListener {
            OpenLink.toDb(OpenLink(i.text.toString(),i5.text.toString(),i1.text.toString(),i2.text.toString(),i3.text.toString(),i4.text.toString(),i6.text.toString().split("\n"),i7.text.toString()),db, item)
            finish()
        }

        button.setOnClickListener {
            var ii = "am start -a android.intent.action.VIEW"

            if (i1.text.toString() != "") ii += " -n ${i1.text}"
            if (i2.text.toString() != "") ii += "/${i2.text}"
            if (i7.text.toString() != "") ii += " -d "+i7.text

            if (i3.text.toString() != "") {
                val ii3 = i3.text?.split("\n")
                val ii4 = i4.text?.split("\n")
                if (ii3!!.size== ii4!!.size){
                    for (rfi in ii3.indices)ii+=" --e${ii3[rfi].replaceRange(1,2," '")}' '${ii4[rfi]}'"}
            }
            ProcessBuilder("su","-c", ii).start()
            db.close()
            finish()

        }
        button2.setOnClickListener {
            db.delete("list", "id = ?", arrayOf(item.toString()))
            db.close()
            finish()
        }
    }
}