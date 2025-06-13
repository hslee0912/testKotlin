package com.example.testapp_kotlin.FCM

import android.app.ActivityManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import androidx.annotation.OptIn
// 로그가 통일되게 수정
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.media3.common.util.UnstableApi
import com.example.testapp_kotlin.MainActivity
import com.example.testapp_kotlin.R
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class MyFirebaseMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "New FCM token: $token")

        // 토큰을 서버에 전송
        sendRegistrationToServer(token)
    }

    @OptIn(UnstableApi::class)
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        Log.d(TAG, "From: ${remoteMessage.from}")

        // 알림 메시지 처리
        remoteMessage.notification?.let {
            // 알림 표시
            Log.d(TAG, "Notification Message Body: ${it.body}")
            showNotification(it.title, it.body)

            // 앱이 포그라운드에 있을 때는 뷰모델에 데이터 전달
            if (isAppInForeground()) {
                updateViewModel(it.title, it.body)
            }
        }

        // 데이터 메시지 처리
        if (remoteMessage.data.isNotEmpty()) {
            Log.d(TAG, "Message data payload: ${remoteMessage.data}")
            // 필요한 경우 WorkManager로 장기 실행 작업 처리
//            scheduleJob(remoteMessage.data)
        }
    }

    private fun updateViewModel(title: String?, body: String?) {
        if (title == null || body == null) return

        // 싱글톤 또는 의존성 주입을 통해 ViewModel에 접근
        // 예시로는 싱글톤 패턴 사용
        NotificationDataHolder.updateNotification(title, body)
    }

    private fun isAppInForeground(): Boolean {
        val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val appProcesses = activityManager.runningAppProcesses ?: return false

        val packageName = packageName
        for (appProcess in appProcesses) {
            if (appProcess.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
                && appProcess.processName == packageName) {
                return true
            }
        }
        return false
    }

    private fun showNotification(title: String?, body: String?) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "fcm_default_channel"

        // 알림 채널 생성 (Android 8.0+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "FCM Notifications",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Firebase Cloud Messaging Notifications"
                enableLights(true)
                lightColor = Color.RED
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        // 알림 클릭 시 MainActivity로 이동
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra("notification", true)
        }

        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_ONE_SHOT
        )

        // 알림 빌드
        val notificationBuilder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_notification) // 알림 아이콘 설정
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)

        // 알림 표시
        val notificationId = System.currentTimeMillis().toInt()
        notificationManager.notify(notificationId, notificationBuilder.build())
    }

    private fun sendRegistrationToServer(token: String) {
        // 토큰을 서버에 전송하는 코드
        // 예: Retrofit을 사용한 API 호출
    }

    companion object {
        private const val TAG = "FCMService"
    }
}

// 앱 전체에서 알림 데이터를 공유하기 위한 싱글톤 객체
object NotificationDataHolder {
    private val _notificationData = MutableStateFlow<NotificationData?>(null)
    val notificationData: StateFlow<NotificationData?> = _notificationData

    fun updateNotification(title: String, body: String) {
        _notificationData.value = NotificationData(title, body)
    }
}
