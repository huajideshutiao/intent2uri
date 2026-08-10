package com.shutiao.flow

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.GridView
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.Toast
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors

class SoutuActivity : Activity() {

    companion object {
        private val executor = Executors.newSingleThreadExecutor()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_soutu)
        window.setLayout(-1, -2)
        val imageView = findViewById<ImageView>(R.id.imageView)
        val progressBar = findViewById<ProgressBar>(R.id.progressBar)
        val sites = findViewById<GridView>(R.id.sites)
        val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
        } ?: run { finish(); return }

        progressBar.visibility = View.VISIBLE
        sites.visibility = View.GONE

        executor.execute {
            val file = prepareImage(uri, imageView) ?: run {
                runOnUiThread {
                    Toast.makeText(this, getString(R.string.image_read_failed), Toast.LENGTH_SHORT).show()
                    finish()
                }
                return@execute
            }
            runOnUiThread {
                progressBar.visibility = View.GONE
                sites.visibility = View.VISIBLE
                bindSites(sites, progressBar, Soutu(file, this))
            }
        }
    }

    private fun prepareImage(uri: Uri, imageView: ImageView): ByteArray? = try {
        val fileSize = contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize } ?: -1L
        val (width, height) = BitmapFactory.Options().run {
            inJustDecodeBounds = true
            contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, this) }
            Pair(outWidth, outHeight)
        }
        if (fileSize > 262_144) {
            val maxSide = maxOf(width, height)
            var sampleSize = 1
            while (maxSide / (sampleSize * 2) >= 800) sampleSize *= 2
            val bitmap = contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sampleSize })
            } ?: return null
            runOnUiThread { imageView.setImageBitmap(bitmap) }
            val output = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 80, output)
            output.toByteArray()
        } else {
            runOnUiThread { imageView.setImageURI(uri) }
            contentResolver.openInputStream(uri)?.use { it.readBytes() }
        }
    } catch (_: Exception) {
        null
    }

    private fun bindSites(sites: GridView, progressBar: ProgressBar, soutu: Soutu) {
        val items = listOf("google", "百度", "saucenao", "搜图酱", "yandex", "ascii2d", "animetrace")
        sites.adapter = object : BaseAdapter() {
            override fun getCount(): Int = items.size
            override fun getItem(position: Int) = items[position]
            override fun getItemId(position: Int) = position.toLong()
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = (convertView as? Button) ?: Button(parent.context)
                val item = getItem(position)
                view.text = item
                view.setOnClickListener {
                    progressBar.visibility = View.VISIBLE
                    sites.visibility = View.GONE
                    view.isEnabled = false
                    soutu.upload(item) { data ->
                        runOnUiThread {
                            progressBar.visibility = View.GONE
                            sites.visibility = View.VISIBLE
                            view.isEnabled = true
                            handleResult(item, data)
                        }
                    }
                }
                return view
            }
        }
        sites.numColumns = 2
    }

    private fun handleResult(site: String, data: Data) {
        if (!data.successful) {
            Toast.makeText(this, getString(R.string.network_error), Toast.LENGTH_SHORT).show()
            return
        }
        if (data.itemList.isNotEmpty()) {
            startActivity(Intent(this, SoutuResultActivity::class.java).apply {
                putExtra("site", site)
                putExtra("url", data.url)
                putExtra("items", data.itemList.toList().encodeToIntent())
            })
        } else if (data.jump) {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(data.url)))
        } else {
            Toast.makeText(this, getString(R.string.no_results), Toast.LENGTH_SHORT).show()
        }
    }
}
