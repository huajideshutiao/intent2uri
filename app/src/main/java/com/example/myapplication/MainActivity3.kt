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
                    val i1 = data.authority?:""
                    var esd= (data.path?:"").drop(1)
                    OpenLink.fromDb(db, i1).apply {
                        if (change2.isNotEmpty()) esd = esd.replace(Regex(change2[0]),change2[1])
                        if (uri != "") uri = uri.replace("{key}", esd)
                        if (keys != "") datas = datas.replace("{key}", esd)
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
                                if (change2.isNotEmpty()) key = key.replace(Regex(change2[0]),change2[1])
                                if (uri != "") uri = uri.replace("{key}", key)
                                if (keys != "") datas = datas.replace("{key}",key)
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