package com.example.myapplication

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle


class MainActivity3 : Activity() {
    private val db by lazy { DbHelper(this).readableDatabase }
    private val list by lazy {  getSharedPreferences("list", MODE_PRIVATE) }
    private val httpData by lazy { item(db, "host") }
    private fun open(data : Uri){
        when (data.scheme) {
            "kkp" -> {
                val i1 = data.authority!!
                val esd= (data.path?:"").drop(1)
                openLink(esd,OpenLink.fromDb(db, i1))
            }

            "http", "https" -> {
                var staut = true
                val key =data.toString()
                for(i in httpData.indices step 2) {
                    if( httpData[i]!=""  && key.contains(Regex(httpData[i]))) {
                        openLink(key,OpenLink.fromDb(db, httpData[i + 1]))
                        staut = false
                        break
                    }
                }
                if (staut){
                    val browser = Intent(Intent.ACTION_VIEW, data)
                    browser.setPackage(list.getString("browser", ""))
                    startActivity(browser)
                }
            }
        }
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        intent.data?.let { open(it) }
    }

    override fun onNewIntent(intent: Intent?) {
        intent?.data?.let { open(it) }
    }
}