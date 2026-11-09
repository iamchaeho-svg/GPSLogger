package kr.go.pyeongtaek.gpslogger

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {
    private lateinit var intervalInput: EditText
    private lateinit var statusView: TextView
    private lateinit var countView: TextView
    private lateinit var locationView: TextView
    private lateinit var startButton: Button
    private lateinit var stopButton: Button
    private val handler = Handler(Looper.getMainLooper())
    private val refreshTask = object : Runnable {
        override fun run() {
            refreshData()
            handler.postDelayed(this, 1500)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildScreen()
        val savedMinutes = getSharedPreferences(LocationRecordService.PREFS, MODE_PRIVATE)
            .getLong(LocationRecordService.KEY_INTERVAL, 60_000L) / 60_000L
        intervalInput.setText(savedMinutes.coerceAtLeast(1).toString())
        refreshData()
    }

    private fun buildScreen() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(22), dp(22), dp(18))
            setBackgroundColor(0xFFF7F9FC.toInt())
        }
        val scroll = ScrollView(this)
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(content)

        content.addView(text("GPS 기록장", 27, true, 0xFF172B4D.toInt()).apply {
            setPadding(0, dp(8), 0, dp(5))
        })
        content.addView(text("원하는 간격으로 위치를 휴대폰에 저장합니다.", 14, false, 0xFF526078.toInt()))
        content.addView(gap(26))

        content.addView(text("기록 간격", 16, true, 0xFF172B4D.toInt()))
        val intervalRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        intervalInput = EditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            singleLine = true
            textSize = 19f
            setText("1")
            setPadding(dp(12), dp(8), dp(12), dp(8))
            background = roundedBackground(0xFFFFFFFF.toInt(), 12)
        }
        intervalRow.addView(intervalInput, LinearLayout.LayoutParams(dp(110), dp(52)))
        intervalRow.addView(text("분 (최소 1분)", 15, false, 0xFF526078.toInt()).apply {
            setPadding(dp(12), 0, 0, 0)
        })
        content.addView(intervalRow)
        content.addView(gap(18))

        val buttonRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        startButton = button("기록 시작", 0xFF1769AA.toInt()) { startRecording() }
        stopButton = button("기록 중지", 0xFF687386.toInt()) { stopRecording() }
        buttonRow.addView(startButton, LinearLayout.LayoutParams(0, dp(52), 1f).apply { marginEnd = dp(8) })
        buttonRow.addView(stopButton, LinearLayout.LayoutParams(0, dp(52), 1f).apply { marginStart = dp(8) })
        content.addView(buttonRow)
        content.addView(gap(26))

        content.addView(text("기록 상태", 16, true, 0xFF172B4D.toInt()))
        statusView = panelText("대기 중")
        content.addView(statusView)
        content.addView(gap(17))
        content.addView(text("저장된 위치", 16, true, 0xFF172B4D.toInt()))
        countView = panelText("0건")
        content.addView(countView)
        content.addView(gap(17))
        content.addView(text("최근 GPS 좌표", 16, true, 0xFF172B4D.toInt()))
        locationView = panelText("아직 저장된 위치가 없습니다.")
        content.addView(locationView)
        content.addView(gap(22))

        content.addView(button("CSV 파일로 내보내기", 0xFF20845A.toInt()) { exportCsv() })
        content.addView(gap(9))
        content.addView(button("저장된 기록 전체 삭제", 0xFFB54747.toInt()) { confirmDelete() })
        content.addView(gap(18))
        content.addView(text(
            "안내: 기록 중에는 상단 알림에 ‘GPS 기록장’이 표시됩니다. 화면을 꺼도 기록을 계속하려면 알림을 유지하고, 휴대폰 위치 기능과 GPS를 켜 두세요. 배터리 절약 설정에 따라 제조사별로 기록이 지연될 수 있습니다.",
            12, false, 0xFF687386.toInt()
        ))

        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
    }

    private fun startRecording() {
        val minutes = intervalInput.text.toString().toLongOrNull()
        if (minutes == null || minutes < 1) {
            intervalInput.error = "1분 이상 입력하세요."
            return
        }
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            val permissions = mutableListOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
            if (Build.VERSION.SDK_INT >= 33) permissions.add(Manifest.permission.POST_NOTIFICATIONS)
            requestPermissions(permissions.toTypedArray(), REQUEST_PERMISSIONS)
            return
        }
        val intent = Intent(this, LocationRecordService::class.java)
            .putExtra(LocationRecordService.EXTRA_INTERVAL, minutes * 60_000L)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent) else startService(intent)
            Toast.makeText(this, "GPS 기록을 시작했습니다.", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "서비스 시작 실패: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }

    private fun stopRecording() {
        startService(Intent(this, LocationRecordService::class.java).setAction(LocationRecordService.ACTION_STOP))
        Toast.makeText(this, "GPS 기록 중지를 요청했습니다.", Toast.LENGTH_SHORT).show()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_PERMISSIONS) {
            val fineGranted = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            if (fineGranted) startRecording()
            else Toast.makeText(this, "정확한 GPS 기록을 위해 위치 권한을 허용해야 합니다.", Toast.LENGTH_LONG).show()
        }
    }

    private fun refreshData() {
        val db = LocationDb(this)
        countView.text = "${db.countRecords()}건 저장됨"
        val latest = db.latestRecord()
        locationView.text = if (latest == null) {
            "아직 저장된 위치가 없습니다."
        } else {
            val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.KOREA).format(Date(latest.time))
            "기록 시각: $time\n위도: ${latest.latitude}\n경도: ${latest.longitude}\n정확도: ${latest.accuracy?.let { "%.1f m".format(it) } ?: "정보 없음"}"
        }
        val recording = getSharedPreferences(LocationRecordService.PREFS, MODE_PRIVATE)
            .getBoolean(LocationRecordService.KEY_RECORDING, false)
        statusView.text = if (recording) "기록 중 · 백그라운드 GPS 수집" else "대기 중"
        startButton.isEnabled = !recording
        stopButton.isEnabled = recording
        db.close()
    }

    private fun exportCsv() {
        val db = LocationDb(this)
        val recordCount = try { db.countRecords() } finally { db.close() }
        if (recordCount == 0L) {
            Toast.makeText(this, "내보낼 기록이 없습니다.", Toast.LENGTH_SHORT).show()
            return
        }
        val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.KOREA).format(Date())
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "text/csv"
            putExtra(Intent.EXTRA_TITLE, "GPS기록_$stamp.csv")
        }
        startActivityForResult(intent, REQUEST_CREATE_FILE)
    }

    @Deprecated("Deprecated in Android, kept for broad device compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CREATE_FILE && resultCode == RESULT_OK) {
            val uri: Uri = data?.data ?: return
            try {
                val stream = contentResolver.openOutputStream(uri) ?: throw IllegalStateException("파일을 열 수 없습니다.")
                val db = LocationDb(this)
                try { db.exportCsv(stream) } finally { db.close() }
                Toast.makeText(this, "CSV 파일을 저장했습니다.", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(this, "내보내기 실패: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun confirmDelete() {
        AlertDialog.Builder(this)
            .setTitle("모든 GPS 기록 삭제")
            .setMessage("저장된 위치 기록을 모두 삭제할까요? 삭제 후에는 복구할 수 없습니다.")
            .setNegativeButton("취소", null)
            .setPositiveButton("삭제") { _, _ ->
                val db = LocationDb(this)
                try { db.clearRecords() } finally { db.close() }
                refreshData()
                Toast.makeText(this, "모든 기록을 삭제했습니다.", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun text(value: String, size: Int, bold: Boolean, color: Int) = TextView(this).apply {
        text = value
        textSize = size.toFloat()
        setTextColor(color)
        if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
    }

    private fun panelText(value: String) = text(value, 15, false, 0xFF233044.toInt()).apply {
        setPadding(dp(15), dp(15), dp(15), dp(15))
        background = roundedBackground(0xFFFFFFFF.toInt(), 12)
    }

    private fun button(label: String, color: Int, action: () -> Unit) = Button(this).apply {
        text = label
        textSize = 14f
        isAllCaps = false
        setTextColor(0xFFFFFFFF.toInt())
        background = roundedBackground(color, 12)
        setOnClickListener { action() }
    }

    private fun gap(height: Int) = View(this).apply { layoutParams = LinearLayout.LayoutParams(1, dp(height)) }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private fun roundedBackground(color: Int, radius: Int): android.graphics.drawable.GradientDrawable =
        android.graphics.drawable.GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()
        }

    override fun onResume() {
        super.onResume()
        handler.removeCallbacks(refreshTask)
        handler.post(refreshTask)
    }

    override fun onPause() {
        handler.removeCallbacks(refreshTask)
        super.onPause()
    }

    companion object {
        private const val REQUEST_PERMISSIONS = 200
        private const val REQUEST_CREATE_FILE = 201
    }
}
