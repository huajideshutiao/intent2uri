package com.example.myapplication

import android.app.Activity
import android.content.Intent
import android.os.Bundle

class MainActivity3 : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val list = getSharedPreferences("list", MODE_PRIVATE)
        val data = intent.data
        val db = DbHelper(this).writableDatabase
        when (data!!.scheme) {
                "kkp" -> {
                    val i1 = data.authority
                    var esd = data.path
                    if (esd == null || esd.length < 2) esd = ""
                    else {
                        while (esd!!.startsWith("/")) esd = esd.substring(1)
                        while (esd!!.endsWith("/")) esd = esd.substring(0, esd.length - 1)
                    }
                    OpenLink.fromDb(db, i1!!).apply {
                    if (uri != "") uri = uri.replace("{key}", esd)
                    if (keys != "") datas = datas.replace("{key}", esd).replace("{photo}", getLatestImagePath(this@MainActivity3))
                        openLink(this)
                }

                }

                "http", "https" -> {

                    var staut = true
                    val ksh = item(db, "host")
                    for(i in ksh.indices step 2) {
                        var key =data.toString()
                        if( ksh[i]!=""  && key.contains(Regex(ksh[i]))) {
                            OpenLink.fromDb(db, ksh[i + 1]).apply {
                                if (change2 != "") key = key.replace(Regex(ksh[i]),change2)
                                if (uri != "") uri = uri.replace("{key}", key)
                                if (keys != "") datas = datas.replace("{key}",key).replace("{photo}", getLatestImagePath(this@MainActivity3))
                                openLink(this)
                            }
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
}