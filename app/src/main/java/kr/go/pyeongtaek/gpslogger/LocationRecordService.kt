package kr.go.pyeongtaek.gpslogger

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.widget.Toast

class LocationRecordService : Service(), LocationListener {
    private lateinit var locationManager: LocationManager
    private lateinit var db: LocationDb
    private var intervalMillis: Long = 60_000L

    override fun onCreate() {
        super.onCreate()
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        db = LocationDb(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopRecording()
            return START_NOT_STICKY
        }

        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        intervalMillis = intent?.getLongExtra(EXTRA_INTERVAL, prefs.getLong(KEY_INTERVAL, 60_000L))
            ?: prefs.getLong(KEY_INTERVAL, 60_000L)
        intervalMillis = intervalMillis.coerceAtLeast(60_000L)
        prefs.edit().putLong(KEY_INTERVAL, intervalMillis).putBoolean(KEY_RECORDING, true).apply()

        val notification = buildNotification("기록 간격: ${intervalMillis / 60_000}분 · GPS 위치 수집 중")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        beginLocationUpdates()
        return START_STICKY
    }

    private fun beginLocationUpdates() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            stopRecording()
            return
        }
        try {
            if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                updateNotification("GPS가 꺼져 있습니다. 위치 설정을 확인하세요.")
            }
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER, intervalMillis, 0f, this, Looper.getMainLooper()
            )
        } catch (_: SecurityException) {
            stopRecording()
        } catch (_: IllegalArgumentException) {
            updateNotification("GPS 위치 제공자를 사용할 수 없습니다.")
        }
    }

    override fun onLocationChanged(location: Location) {
        db.insert(
            LocationRecord(
                time = location.time,
                latitude = location.latitude,
                longitude = location.longitude,
                accuracy = if (location.hasAccuracy()) location.accuracy else null,
                altitude = if (location.hasAltitude()) location.altitude else null,
                speed = if (location.hasSpeed()) location.speed else null,
                provider = location.provider
            )
        )
        updateNotification("GPS 위치 저장 중 · 정확도 ${if (location.hasAccuracy()) "%.0f m".format(location.accuracy) else "확인 중"}")
    }

    @Suppress("DEPRECATION")
    override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) = Unit
    override fun onProviderEnabled(provider: String) = Unit
    override fun onProviderDisabled(provider: String) {
        if (provider == LocationManager.GPS_PROVIDER) updateNotification("GPS가 꺼져 있습니다. 위치 설정을 확인하세요.")
    }

    private fun stopRecording() {
        try { locationManager.removeUpdates(this) } catch (_: Exception) { }
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(KEY_RECORDING, false).apply()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "GPS 기록", NotificationManager.IMPORTANCE_LOW)
            channel.description = "GPS 위치를 기록하는 동안 표시됩니다."
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(message: String): Notification {
        val stopIntent = Intent(this, LocationRecordService::class.java).setAction(ACTION_STOP)
        val pendingStop = PendingIntent.getService(
            this, 10, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            Notification.Builder(this, CHANNEL_ID) else Notification.Builder(this)
        return builder
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle("GPS 기록장")
            .setContentText(message)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_media_pause, "기록 중지", pendingStop)
            .build()
    }

    private fun updateNotification(message: String) {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification(message))
    }

    override fun onDestroy() {
        try { locationManager.removeUpdates(this) } catch (_: Exception) { }
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(KEY_RECORDING, false).apply()
        db.close()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_STOP = "kr.go.pyeongtaek.gpslogger.STOP"
        const val EXTRA_INTERVAL = "interval_millis"
        const val PREFS = "gps_logger_prefs"
        const val KEY_INTERVAL = "interval_millis"
        const val KEY_RECORDING = "recording"
        private const val CHANNEL_ID = "gps_record_channel"
        private const val NOTIFICATION_ID = 1001
    }
}
