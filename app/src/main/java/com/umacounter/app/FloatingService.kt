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
import android.view.*
import android.widget.Button
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
        val initWidth = (260 * density).toInt()
        val initHeight = (180 * density).toInt()

        params = WindowManager.LayoutParams(
            initWidth,
            initHeight,
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
        setupResize()

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

    @SuppressLint("ClickableViewAccessibility")
    private fun setupResize() {
        val btnToggle = floatingView.findViewById<TextView>(R.id.btnToggleResize)
        val handle = floatingView.findViewById<TextView>(R.id.resizeHandle)

        var isResizeMode = false
        btnToggle.setOnClickListener {
            isResizeMode = !isResizeMode
            if (isResizeMode) {
                handle.visibility = View.VISIBLE
                btnToggle.setTextColor(Color.parseColor("#4CAF50"))
                btnToggle.text = "완료"
            } else {
                handle.visibility = View.GONE
                btnToggle.setTextColor(Color.parseColor("#FFEB3B"))
                btnToggle.text = "크기"
            }
        }

        var startW = 0
        var startH = 0
        var startTouchX = 0f
        var startTouchY = 0f
        val minW = (180 * resources.displayMetrics.density).toInt()
        val minH = (120 * resources.displayMetrics.density).toInt()

        handle.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startW = params.width
                    startH = params.height
                    startTouchX = event.rawX
                    startTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - startTouchX).toInt()
                    val dy = (event.rawY - startTouchY).toInt()

                    params.width = (startW + dx).coerceAtLeast(minW)
                    params.height = (startH + dy).coerceAtLeast(minH)
                    windowManager.updateViewLayout(floatingView, params)
                    true
                }
                else -> false
            }
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
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(updateReceiver)
        if (::floatingView.isInitialized) {
            windowManager.removeView(floatingView)
        }
    }
}
