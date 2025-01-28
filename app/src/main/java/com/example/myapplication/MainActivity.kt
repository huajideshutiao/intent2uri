package com.example.myapplication

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.textfield.TextInputEditText

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val list = getSharedPreferences("list", MODE_PRIVATE)
        fun getInstalledBrowserApps(): List<ResolveInfo> {
            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.bing.com"))
            val browserList =
                packageManager.queryIntentActivities(browserIntent, PackageManager.MATCH_ALL)

            if (list.getString("browser", "") == "")list.edit().putString("browser", browserList[0].activityInfo.packageName).apply()
            return browserList.filter { it.activityInfo.packageName != packageName }
        }

        val browserList = getInstalledBrowserApps()

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

        fun item() : List<String> {
            val cursor = db.query("list", arrayOf("id","name"), null, null, null, null, null)
            val lk :MutableList<String> = mutableListOf()
            while (cursor.moveToNext()) {
                val name = cursor.getString(cursor.getColumnIndexOrThrow("name"))
                lk.add(name)
                val id = cursor.getString(cursor.getColumnIndexOrThrow("id"))
                lk.add(id)
            }
            cursor.close()
            return lk
        }


            lateinit var io: ActivityResultLauncher<Intent>
            setContentView(R.layout.activity_main)
            val button = findViewById<Button>(R.id.button)
            val button1 = findViewById<Button>(R.id.button1)
            val i1 = findViewById<TextInputEditText>(R.id.i1)
            val i2 = findViewById<TextInputEditText>(R.id.i2)
            val i3 = findViewById<TextInputEditText>(R.id.i3)
            val i4 = findViewById<TextInputEditText>(R.id.i4)
            val i5 = findViewById<TextInputEditText>(R.id.i5)
            val i = findViewById<TextInputEditText>(R.id.i)
            val adp = findViewById<RecyclerView>(R.id.startlist)
            val aadp = findViewById<RecyclerView>(R.id.applist)



        class ListAdapter(private val total: List<String>) :
                RecyclerView.Adapter<ListAdapter.ViewHolder>() {

                inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
                    val textView = itemView as TextView
                }

                override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
                    val view = TextView(parent.context)
                    return ViewHolder(view)
                }

                override fun onBindViewHolder(holder: ViewHolder, position: Int) {

                    holder.textView.apply {
                        text = total[2*position]
                        textSize = 20f
                        gravity = Gravity.CENTER
                        val borderDrawable = GradientDrawable()
                        borderDrawable.shape = GradientDrawable.RECTANGLE
                        borderDrawable.setStroke(2, Color.BLACK)
                        borderDrawable.cornerRadius = 8f
                        background = borderDrawable
                        setPadding(8, 8, 8, 8)
                        setOnClickListener {
                            //启动activity2并传入position
                            val intent = Intent(this@MainActivity, MainActivity2::class.java)
                            intent.putExtra("item", total[2*position+1])
                            io.launch(intent)
                        }
                    }
                }

                override fun getItemCount() = total.size/2
            }

            class AppAdapter(private val total: List<ResolveInfo>) :
                RecyclerView.Adapter<AppAdapter.ViewHolder>() {

                inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
                    val textView = itemView as TextView

                }

                override fun onCreateViewHolder(
                    parent: ViewGroup,
                    viewType: Int
                ): AppAdapter.ViewHolder {
                    val view = TextView(parent.context)
                    return ViewHolder(view)
                }

                override fun onBindViewHolder(holder: AppAdapter.ViewHolder, position: Int) {

                    holder.textView.apply {
                    val oo = total[position].activityInfo
                    text = oo.applicationInfo.loadLabel(packageManager)
                        layoutParams = ViewGroup.LayoutParams(200, 200)
                    gravity = Gravity.CENTER
                        val borderDrawable = GradientDrawable()
                        borderDrawable.shape = GradientDrawable.RECTANGLE
                        borderDrawable.setStroke(2, Color.BLACK)
                        borderDrawable.cornerRadius = 8f
                        background = borderDrawable
                        setPadding(8, 8, 8, 8)
                        setOnClickListener {
                        list.edit().putString("browser", oo.packageName).apply()
                        Toast.makeText(
                            this@MainActivity,
                            "已设置${oo.applicationInfo.loadLabel(packageManager)}为默认打开方式",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
                }

                override fun getItemCount() = total.size
            }




        io = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
                adp.adapter = ListAdapter(item())
            }
            adp.adapter = ListAdapter(item())
            //adp使用网格布局
            adp.layoutManager = androidx.recyclerview.widget.GridLayoutManager(this,3)

            aadp.adapter = AppAdapter(browserList)
            //adp使用卡片布局
            aadp.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(
                this,
                RecyclerView.HORIZONTAL,
                false
            )



            button1.setOnClickListener {
                val hy = ContentValues().apply {
                    put("name", i.text.toString())
                    put("host", i5.text.toString())
                    put("package", i1.text.toString())
                    put("activity", i2.text.toString())
                    put("keys", i3.text.toString())
                    put("datas", i4.text.toString())
                }
                db.insert("list", null, hy)
                i.setText("")
                i1.setText("")
                i2.setText("")
                i3.setText("")
                i4.setText("")
                i5.setText("")
                adp.adapter = ListAdapter(item())
            }

            button.setOnClickListener {
                var ii = "am start -n ${i1.text}/${i2.text}"
                val ii3 = i3.text?.split("\n")
                val ii4 = i4.text?.split("\n")
                if (ii4 != null && ii3 != null) {
                    if (ii3.size == ii4.size && ii3[0] != "") {
                        for (fgi in ii3.indices) ii += " --e${
                            ii3[fgi].replaceRange(
                                1,
                                2,
                                " '"
                            )
                        }' '${ii4[fgi]}'"
                    }
                }
                ProcessBuilder("su", "-c", ii).start()
            }
        }
    }