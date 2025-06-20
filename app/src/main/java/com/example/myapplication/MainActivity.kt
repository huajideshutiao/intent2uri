package com.example.myapplication

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.GridView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast


class MainActivity : Activity() {

    class GridAdapter(
        private val context0: Activity,
        private val data0: List<String>
    ) : BaseAdapter() {

        override fun getCount() = data0.size / 2

        override fun getItem(position: Int) = data0[position]

        override fun getItemId(position: Int) = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val textView = TextView(context0)
            textView.apply {
                text = data0[position * 2]
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
                    val intent = Intent(context, MainActivity2::class.java)
                    intent.putExtra("item", data0[2 * position + 1])
                    //io.launch(intent)
                    context0.startActivityForResult(intent, 1)
                }
            }
            return textView
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val list = getSharedPreferences("list", MODE_PRIVATE)

        val browserList: List<ResolveInfo> = run {
            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.bing.com"))
            val browserList =
                packageManager.queryIntentActivities(browserIntent, PackageManager.MATCH_ALL)
                    .filter { it.activityInfo.packageName != packageName }

            if (list.getString("browser", "") == "") list.edit()
                .putString("browser", browserList[0].activityInfo.packageName).apply()
            browserList
        }

        val db = DbHelper(this).writableDatabase
        setContentView(R.layout.activity_main)

        val results = findViewById<ScrollView>(R.id.main)
        results.setOnApplyWindowInsetsListener { v, insets ->
            val statusBarHeight = insets.systemWindowInsetTop
            v.setPadding(
                v.paddingLeft,
                v.paddingTop + statusBarHeight,
                v.paddingRight,
                v.paddingBottom
            )
            insets
        }
        results.requestApplyInsets()


        val button1 = findViewById<Button>(R.id.button1)
        val i1 = findViewById<EditText>(R.id.i1)
        val i2 = findViewById<EditText>(R.id.i2)
        val i3 = findViewById<EditText>(R.id.i3)
        val i4 = findViewById<EditText>(R.id.i4)
        val i5 = findViewById<EditText>(R.id.i5)
        val i6 = findViewById<EditText>(R.id.i6)
        val i7 = findViewById<EditText>(R.id.i7)
        val i = findViewById<EditText>(R.id.i)
        val adp = findViewById<GridView>(R.id.startlist)
        val aadp = findViewById<LinearLayout>(R.id.applist)

        for (idfec in browserList) {
            val tyt = TextView(this)
            val oo = idfec.activityInfo
            tyt.text = oo.applicationInfo.loadLabel(packageManager)
            tyt.layoutParams = ViewGroup.LayoutParams(200, 200)
            tyt.gravity = Gravity.CENTER
            val borderDrawable = GradientDrawable()
            borderDrawable.shape = GradientDrawable.RECTANGLE
            borderDrawable.setStroke(2, Color.BLACK)
            borderDrawable.cornerRadius = 8f
            tyt.background = borderDrawable
            tyt.setPadding(8, 8, 8, 8)
            tyt.setOnClickListener {
                list.edit().putString("browser", oo.packageName).apply()
                Toast.makeText(
                    this,
                    "已设置${oo.applicationInfo.loadLabel(packageManager)}为默认打开方式",
                    Toast.LENGTH_SHORT
                ).show()
            }
            aadp.addView(tyt)
        }

        adp.adapter = GridAdapter(this, item(db, "name"))

        button1.setOnClickListener {
            OpenLink.toDb(
                OpenLink(
                    i.text.toString(),
                    i5.text.toString(),
                    i1.text.toString(),
                    i2.text.toString(),
                    i3.text.toString(),
                    i4.text.toString(),
                    i6.text.toString(),
                    i7.text.toString()
                ), db
            )
            i.setText("")
            i1.setText("")
            i2.setText("")
            i3.setText("")
            i4.setText("")
            i5.setText("")
            i6.setText("")
            i7.setText("")
            adp.adapter = GridAdapter(this, item(db, "name"))
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val adp = findViewById<GridView>(R.id.startlist)


        val db = DbHelper(this).writableDatabase
        adp.adapter = GridAdapter(this, item(db, "name"))

    }
}