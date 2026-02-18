package com.shutiao.flow

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.ImageButton
import android.widget.ListView

class SoutuResultActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_soutu_result)
        val results = findViewById<ListView>(R.id.imageResults)
        val items = Soutu.data.itemList
        results.adapter = SoutuAdapter(this,items)
        val url = intent.getStringExtra("url")
        findViewById<ImageButton>(R.id.open).setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }
    }
}