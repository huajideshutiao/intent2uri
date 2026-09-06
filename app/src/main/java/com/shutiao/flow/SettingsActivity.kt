package com.shutiao.flow

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.widget.Button
import android.widget.EditText
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
            showBrowserSelector(this)
        }

        findViewById<Button>(R.id.btn_set_default_assistant).setOnClickListener {
            openDefaultAssistantSettings()
        }

        findViewById<Button>(R.id.btn_set_max_lines).setOnClickListener {
            showMaxLinesDialog()
        }
    }

    private fun showMaxLinesDialog() {
        val currentMaxLines = App.sharedPreferences.getInt("assistant_max_lines", 5)
        val editText = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(currentMaxLines.toString())
            setSelection(text.length)
        }

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.max_lines_title))
            .setView(editText)
            .setPositiveButton(getString(R.string.ok)) { _, _ ->
                val value = editText.text.toString().toIntOrNull()
                if (value == null || value <= 0) {
                    Toast.makeText(this, getString(R.string.positive_integer_hint), Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                App.sharedPreferences.edit().putInt("assistant_max_lines", value).apply()
                Toast.makeText(this, getString(R.string.saved), Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
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

    private fun openDefaultAssistantSettings() {
        try {
            val intent = Intent(Settings.ACTION_VOICE_INPUT_SETTINGS)
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.cannot_open_assistant_settings, e.message), Toast.LENGTH_SHORT).show()
        }
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
                jsonArray.put(item.toJson())
            }

            val backupJson = JSONObject().apply {
                put("version", 1)
                put("browser", App.sharedPreferences.getString("browser", ""))
                put("assistant_max_lines", App.sharedPreferences.getInt("assistant_max_lines", 5))
                put("rules", jsonArray)
            }

            contentResolver.openOutputStream(uri)?.use { outputStream ->
                outputStream.write(backupJson.toString(2).toByteArray())
            }

            Toast.makeText(this, getString(R.string.backup_success), Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.backup_failed, e.message), Toast.LENGTH_SHORT).show()
        }
    }

    private fun importFromFile(uri: Uri) {
        try {
            val jsonString = contentResolver.openInputStream(uri)?.use { inputStream ->
                inputStream.bufferedReader().readText()
            } ?: throw Exception(getString(R.string.cannot_read_file))

            val backupJson = JSONObject(jsonString)
            val jsonArray = backupJson.getJSONArray("rules")

            for (i in 0 until jsonArray.length()) {
                OpenLink.fromJson(jsonArray.getJSONObject(i)).save()
            }

            if (backupJson.has("browser")) {
                val browser = backupJson.getString("browser")
                if (browser.isNotEmpty()) {
                    App.sharedPreferences.edit().putString("browser", browser).apply()
                }
            }

            if (backupJson.has("assistant_max_lines")) {
                App.sharedPreferences.edit().putInt("assistant_max_lines", backupJson.getInt("assistant_max_lines"))
                    .apply()
            }

            Toast.makeText(this, getString(R.string.import_success), Toast.LENGTH_SHORT).show()
            setResult(RESULT_OK)
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.import_failed_with_msg, e.message), Toast.LENGTH_SHORT).show()
        }
    }
}
