package com.example.myapplication

import android.app.Application

class InitApp : Application() {
    val db = DbHelper(this).writableDatabase
    val list = getSharedPreferences("list", MODE_PRIVATE)
}