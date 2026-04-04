package com.shutiao.flow

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject

class SettingsActivity : Activity() {

    private val BACKUP_REQUEST_CODE = 1001
    private val IMPORT_REQUEST_CODE = 1002

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        findViewById<Button>(R.id.btn_backup).setOnClickListener {
            backupConfig()
        }

        findViewById<Button>(R.id.btn_import).setOnClickListener {
            importConfig()
        }

        findViewById<Button>(R.id.btn_select_browser).setOnClickListener {
            showBrowserSelector(context = this)
        }
    }

    private fun backupConfig() {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/json"
            putExtra(Intent.EXTRA_TITLE, "flow_backup_${System.currentTimeMillis()}.json")
        }
        startActivityForResult(intent, BACKUP_REQUEST_CODE)
    }

    private fun importConfig() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/json"
        }
        startActivityForResult(intent, IMPORT_REQUEST_CODE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK || data == null) return

        when (requestCode) {
            BACKUP_REQUEST_CODE -> {
                data.data?.let { uri ->
                    backupToFile(uri)
                }
            }
            IMPORT_REQUEST_CODE -> {
                data.data?.let { uri ->
                    importFromFile(uri)
                }
            }
        }
    }

    private fun backupToFile(uri: Uri) {
        try {
            val jsonArray = JSONArray()
            OpenLink.getDatas()
            for (item in OpenLink.datas) {
                val json = JSONObject().apply {
                    put("name", item.name)
                    put("description", item.description)
                    put("matchRule", item.matchRule)
                    put("replaceRule", item.replaceRule)
                    put("packageName", item.packageName)
                    put("activity", item.activity)
                    put("uri", item.uri)
                    put("extraKey", item.extraKey)
                    put("extraValue", item.extraValue)
                }
                jsonArray.put(json)
            }

            val backupJson = JSONObject().apply {
                put("version", 1)
                put("browser", App.sharedPreferences.getString("browser", ""))
                put("rules", jsonArray)
            }

            contentResolver.openOutputStream(uri)?.use { outputStream ->
                outputStream.write(backupJson.toString(2).toByteArray())
            }

            Toast.makeText(this, "备份成功", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "备份失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun importFromFile(uri: Uri) {
        try {
            val jsonString = contentResolver.openInputStream(uri)?.use { inputStream ->
                inputStream.bufferedReader().readText()
            } ?: throw Exception("无法读取文件")

            val backupJson = JSONObject(jsonString)
            val jsonArray = backupJson.getJSONArray("rules")

            for (i in 0 until jsonArray.length()) {
                val json = jsonArray.getJSONObject(i)
                val openLink = OpenLink(
                    name = json.getString("name"),
                    description = json.getString("description"),
                    matchRule = json.getString("matchRule"),
                    replaceRule = json.getString("replaceRule"),
                    packageName = json.getString("packageName"),
                    activity = json.getString("activity"),
                    uri = json.getString("uri"),
                    extraKey = json.getString("extraKey"),
                    extraValue = json.getString("extraValue")
                )
                openLink.save()
            }

            if (backupJson.has("browser")) {
                val browser = backupJson.getString("browser")
                if (browser.isNotEmpty()) {
                    App.sharedPreferences.edit().putString("browser", browser).apply()
                }
            }

            Toast.makeText(this, "导入成功", Toast.LENGTH_SHORT).show()
            setResult(RESULT_OK)
        } catch (e: Exception) {
            Toast.makeText(this, "导入失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
