package com.shutiao.flow

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.GridView
import android.widget.Toast
import java.io.ByteArrayOutputStream

class SoutuActivity : Activity() {
    @SuppressLint("WrongThread")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_soutu)
        window.setLayout(-1, -2)
        val imageView = findViewById<android.widget.ImageView>(R.id.imageView)
        val progressBar = findViewById<android.widget.ProgressBar>(R.id.progressBar)
        val uri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)!!
        val output = ByteArrayOutputStream()
        val file: ByteArray
        val fileSize = this.contentResolver.openFileDescriptor(uri, "r")?.use {
            it.statSize
        }!!
        val (width, height) = BitmapFactory.Options().run {
            inJustDecodeBounds = true
            BitmapFactory.decodeStream(
                this@SoutuActivity.contentResolver.openInputStream(uri),
                null,
                this
            )
            Pair(outWidth, outHeight)
        }
        if (fileSize > 262_144) {
            val maxSide = maxOf(width, height)
            var sampleSize = 1
            while (maxSide / (sampleSize * 2) >= 800) sampleSize *= 2
            val bitmap = BitmapFactory.decodeStream(
                this.contentResolver.openInputStream(uri),
                null,
                BitmapFactory.Options().apply { inSampleSize = sampleSize }
            )!!
            imageView.setImageBitmap(bitmap)
            bitmap.compress(Bitmap.CompressFormat.JPEG, 80, output)
            file = output.toByteArray()
        } else {
            imageView.setImageURI(uri)
            file = this.contentResolver.openInputStream(uri)!!.readBytes()
        }

        Soutu.file = file
        val sites = findViewById<GridView>(R.id.sites)
        val items =
            listOf("google", "百度", "saucenao", "搜图酱", "yandex", "ascii2d", "animetrace")
        sites.adapter = object : BaseAdapter() {
            override fun getCount(): Int = 7
            override fun getItem(position: Int) = items[position]
            override fun getItemId(position: Int) = position.toLong()
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = if (convertView == null) Button(parent.context) else convertView as Button
                val item = getItem(position)
                view.text = item
                view.setOnClickListener {
                    progressBar.visibility = View.VISIBLE
                    sites.visibility = View.GONE
                    view.isEnabled = false
                    Soutu.upload(item) { data ->
                        this@SoutuActivity.runOnUiThread {
                            progressBar.visibility = View.GONE
                            sites.visibility = View.VISIBLE
                            if (data.successful) {
                                if (data.itemList.isNotEmpty()) {
                                    startActivity(Intent().apply {
                                        setClass(this@SoutuActivity, SoutuResultActivity::class.java)
                                        putExtra("site", item)
                                        putExtra("url", data.url)
                                    })
                                } else if (data.jump) {
                                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(data.url)))
                                } else {
                                    Toast.makeText(
                                        this@SoutuActivity,
                                        "没有结果！",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            } else {
                                Toast.makeText(
                                    this@SoutuActivity,
                                    "网络出问题了！",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }
                }

                return view
            }
        }
        sites.numColumns = 2
    }
}