package com.umacounter.app

import android.annotation.SuppressLint
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.util.TypedValue
import android.view.*
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class FloatingService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var floatingView: View
    private lateinit var params: WindowManager.LayoutParams

    private data class CardData(var name: String, var type: String, var count: Int = 0, var isTerminated: Boolean = false)
    private val cards = Array(6) { i -> CardData("카드 ${i + 1}", "none") }

    private val cardButtonIds = arrayOf(
        R.id.btnCard1, R.id.btnCard2, R.id.btnCard3,
        R.id.btnCard4, R.id.btnCard5, R.id.btnCard6
    )

    // 크기 단계: 0(소형 230dp), 1(기본 270dp), 2(대형 310dp), 3(특대 350dp)
    private var scaleLevel = 1
    private val baseWidths = intArrayOf(230, 270, 310, 350)
    private val rowHeights = intArrayOf(42, 48, 56, 64)
    private val textSizes = floatArrayOf(8.5f, 9.5f, 11f, 12.5f)

    private val updateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            loadCardsFromPrefs()
            refreshAllUI()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate() {
        super.onCreate()

        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val inflater = getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        floatingView = inflater.inflate(R.layout.layout_floating_widget, null)

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val density = resources.displayMetrics.density
        val initWidth = (baseWidths[scaleLevel] * density).toInt()

        params = WindowManager.LayoutParams(
            initWidth,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 80
            y = 120
        }

        windowManager.addView(floatingView, params)

        loadCardsFromPrefs()
        setupDrag()
        setupCardButtons()
        setupControls()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(updateReceiver, IntentFilter("com.umacounter.UPDATE_CARDS"), RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(updateReceiver, IntentFilter("com.umacounter.UPDATE_CARDS"))
        }
    }

    private fun loadCardsFromPrefs() {
        val prefs = getSharedPreferences("UmaCounterPrefs", Context.MODE_PRIVATE)
        for (i in 0 until 6) {
            cards[i].name = prefs.getString("card_name_$i", "카드 ${i + 1}") ?: "카드 ${i + 1}"
            cards[i].type = prefs.getString("card_type_$i", "none") ?: "none"
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupDrag() {
        val header = floatingView.findViewById<View>(R.id.dragHeader)
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f

        header.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = initialX + (event.rawX - initialTouchX).toInt()
                    params.y = initialY + (event.rawY - initialTouchY).toInt()
                    windowManager.updateViewLayout(floatingView, params)
                    true
                }
                else -> false
            }
        }
    }

    private fun applyScale() {
        val density = resources.displayMetrics.density
        params.width = (baseWidths[scaleLevel] * density).toInt()
        params.height = WindowManager.LayoutParams.WRAP_CONTENT
        windowManager.updateViewLayout(floatingView, params)

        val row1 = floatingView.findViewById<LinearLayout>(R.id.row1)
        val row2 = floatingView.findViewById<LinearLayout>(R.id.row2)
        val newH = (rowHeights[scaleLevel] * density).toInt()

        row1.layoutParams.height = newH
        row1.requestLayout()
        row2.layoutParams.height = newH
        row2.requestLayout()

        val fontSize = textSizes[scaleLevel]
        for (id in cardButtonIds) {
            val btn = floatingView.findViewById<Button>(id)
            btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, fontSize)
        }
    }

    private fun setupCardButtons() {
        for (i in 0 until 6) {
            val btn = floatingView.findViewById<Button>(cardButtonIds[i])
            updateCardUI(btn, i)

            btn.setOnClickListener {
                cards[i].isTerminated = false
                cards[i].count = (cards[i].count + 1) % 4
                updateCardUI(btn, i)
            }

            btn.setOnLongClickListener {
                cards[i].isTerminated = true
                cards[i].count = (cards[i].count + 1) % 4
                updateCardUI(btn, i)
                true
            }
        }
    }

    private fun updateCardUI(btn: Button, index: Int) {
        val card = cards[index]
        btn.text = "${card.name}\n(${card.count}/3)"

        when {
            card.isTerminated -> {
                btn.setBackgroundColor(Color.parseColor("#5C1A1A"))
                btn.setTextColor(Color.WHITE)
            }
            card.count == 3 -> {
                btn.setBackgroundColor(Color.parseColor("#806600"))
                btn.setTextColor(Color.parseColor("#FFD700"))
            }
            else -> {
                when (card.type) {
                    "speed" -> { btn.setBackgroundColor(Color.parseColor("#87CEFA")); btn.setTextColor(Color.BLACK) }
                    "stamina" -> { btn.setBackgroundColor(Color.parseColor("#E47E72")); btn.setTextColor(Color.BLACK) }
                    "power" -> { btn.setBackgroundColor(Color.parseColor("#FFB142")); btn.setTextColor(Color.BLACK) }
                    "guts" -> { btn.setBackgroundColor(Color.parseColor("#FF9BC3")); btn.setTextColor(Color.BLACK) }
                    "intelligence" -> { btn.setBackgroundColor(Color.parseColor("#B8E994")); btn.setTextColor(Color.BLACK) }
                    else -> { btn.setBackgroundColor(Color.parseColor("#333333")); btn.setTextColor(Color.parseColor("#F0F0F0")) }
                }
            }
        }
    }

    private fun refreshAllUI() {
        for (i in 0 until 6) {
            val btn = floatingView.findViewById<Button>(cardButtonIds[i])
            updateCardUI(btn, i)
        }
    }

    private fun setupControls() {
        val btnClose = floatingView.findViewById<TextView>(R.id.btnClose)
        val btnMinimize = floatingView.findViewById<TextView>(R.id.btnMinimize)
        val cardContainer = floatingView.findViewById<View>(R.id.cardContainer)
        val btnReset = floatingView.findViewById<Button>(R.id.btnReset)
        val btnScaleUp = floatingView.findViewById<TextView>(R.id.btnScaleUp)
        val btnScaleDown = floatingView.findViewById<TextView>(R.id.btnScaleDown)

        btnClose.setOnClickListener { stopSelf() }

        var isCollapsed = false
        btnMinimize.setOnClickListener {
            isCollapsed = !isCollapsed
            cardContainer.visibility = if (isCollapsed) View.GONE else View.VISIBLE
            btnMinimize.text = if (isCollapsed) "+" else "-"
        }

        btnReset.setOnClickListener {
            for (i in 0 until 6) {
                cards[i].count = 0
                cards[i].isTerminated = false
            }
            refreshAllUI()
        }

        btnScaleUp.setOnClickListener {
            if (scaleLevel < baseWidths.size - 1) {
                scaleLevel++
                applyScale()
            }
        }

        btnScaleDown.setOnClickListener {
            if (scaleLevel > 0) {
                scaleLevel--
                applyScale()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(updateReceiver)
        if (::floatingView.isInitialized) {
            windowManager.removeView(floatingView)
        }
    }
}
