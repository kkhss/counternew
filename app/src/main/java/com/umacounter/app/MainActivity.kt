package com.umacounter.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private val OVERLAY_PERMISSION_REQ_CODE = 1234
    private val typeValues = arrayOf("none", "speed", "stamina", "power", "guts", "intelligence")
    private val typeLabels = arrayOf("선택 안함", "스피드", "스태미너", "파워", "근성", "지능")

    private val nameInputs = ArrayList<EditText>()
    private val typeSpinners = ArrayList<Spinner>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initCardInputs()

        findViewById<Button>(R.id.btnSaveCards).setOnClickListener {
            saveCurrentCardsToPrefs()
            // 플로팅 서비스 인스턴스에 직접 전달 (Broadcast 미도달 문제 원천 차단)
            FloatingService.instance?.reloadCardsFromExternal()
            Toast.makeText(this, "카드 설정이 플로팅 창에 적용되었습니다.", Toast.LENGTH_SHORT).show()
        }

        findViewById<Button>(R.id.btnPreset1Save).setOnClickListener { savePreset(1) }
        findViewById<Button>(R.id.btnPreset1Load).setOnClickListener { loadPreset(1) }
        findViewById<Button>(R.id.btnPreset2Save).setOnClickListener { savePreset(2) }
        findViewById<Button>(R.id.btnPreset2Load).setOnClickListener { loadPreset(2) }

        findViewById<Button>(R.id.btnStart).setOnClickListener {
            saveCurrentCardsToPrefs()
            if (checkOverlayPermission()) {
                startFloatingService()
            } else {
                requestOverlayPermission()
            }
        }

        findViewById<Button>(R.id.btnStop).setOnClickListener {
            stopService(Intent(this, FloatingService::class.java))
            Toast.makeText(this, "카운터 종료", Toast.LENGTH_SHORT).show()
        }
    }

    private fun initCardInputs() {
        val inputContainer = findViewById<LinearLayout>(R.id.inputContainer)
        val prefs = getSharedPreferences("UmaCounterPrefs", Context.MODE_PRIVATE)

        for (i in 0 until 6) {
            val row = layoutInflater.inflate(R.layout.item_card_setting, inputContainer, false)
            val lbl = row.findViewById<TextView>(R.id.lblCardIndex)
            val input = row.findViewById<EditText>(R.id.editCardName)
            val spinner = row.findViewById<Spinner>(R.id.spinnerCardType)

            lbl.text = "${i + 1}번:"
            input.setText(prefs.getString("card_name_$i", "카드 ${i + 1}"))

            val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, typeLabels)
            spinner.adapter = adapter

            val savedType = prefs.getString("card_type_$i", "none") ?: "none"
            val selIndex = typeValues.indexOf(savedType).let { if (it >= 0) it else 0 }
            spinner.setSelection(selIndex)

            nameInputs.add(input)
            typeSpinners.add(spinner)
            inputContainer.addView(row)
        }
    }

    private fun saveCurrentCardsToPrefs() {
        val prefs = getSharedPreferences("UmaCounterPrefs", Context.MODE_PRIVATE).edit()
        for (i in 0 until 6) {
            prefs.putString("card_name_$i", nameInputs[i].text.toString())
            prefs.putString("card_type_$i", typeValues[typeSpinners[i].selectedItemPosition])
        }
        prefs.apply()
    }

    private fun savePreset(slot: Int) {
        val edit = EditText(this)
        val prefs = getSharedPreferences("UmaCounterPrefs", Context.MODE_PRIVATE)
        val defaultName = prefs.getString("preset_${slot}_name", "프리셋 $slot")
        edit.setText(defaultName)

        AlertDialog.Builder(this)
            .setTitle("프리셋 $slot 저장")
            .setMessage("저장할 덱 이름을 입력하세요:")
            .setView(edit)
            .setPositiveButton("저장") { _, _ ->
                val name = edit.text.toString().ifBlank { "프리셋 $slot" }
                val pEdit = prefs.edit()
                pEdit.putString("preset_${slot}_name", name)
                for (i in 0 until 6) {
                    pEdit.putString("preset_${slot}_name_$i", nameInputs[i].text.toString())
                    pEdit.putString("preset_${slot}_type_$i", typeValues[typeSpinners[i].selectedItemPosition])
                }
                pEdit.apply()
                updatePresetButtons()
                Toast.makeText(this, "[$name] 저장 완료", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun loadPreset(slot: Int) {
        val prefs = getSharedPreferences("UmaCounterPrefs", Context.MODE_PRIVATE)
        val name = prefs.getString("preset_${slot}_name", null)
        if (name == null) {
            Toast.makeText(this, "저장된 프리셋이 없습니다.", Toast.LENGTH_SHORT).show()
            return
        }

        for (i in 0 until 6) {
            nameInputs[i].setText(prefs.getString("preset_${slot}_name_$i", "카드 ${i + 1}"))
            val tVal = prefs.getString("preset_${slot}_type_$i", "none") ?: "none"
            val selIndex = typeValues.indexOf(tVal).let { if (it >= 0) it else 0 }
            typeSpinners[i].setSelection(selIndex)
        }
        saveCurrentCardsToPrefs()
        FloatingService.instance?.reloadCardsFromExternal()
        Toast.makeText(this, "[$name] 불러오기 완료", Toast.LENGTH_SHORT).show()
    }

    override fun onResume() {
        super.onResume()
        updatePresetButtons()
    }

    private fun updatePresetButtons() {
        val prefs = getSharedPreferences("UmaCounterPrefs", Context.MODE_PRIVATE)
        findViewById<Button>(R.id.btnPreset1Load).text = "[${prefs.getString("preset_1_name", "프리셋 1")}] 불러오기"
        findViewById<Button>(R.id.btnPreset2Load).text = "[${prefs.getString("preset_2_name", "프리셋 2")}] 불러오기"
    }

    private fun checkOverlayPermission(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Settings.canDrawOverlays(this) else true

    private fun requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            startActivityForResult(intent, OVERLAY_PERMISSION_REQ_CODE)
        }
    }

    private fun startFloatingService() {
        startService(Intent(this, FloatingService::class.java))
        finish()
    }
}
