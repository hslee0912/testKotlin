package com.example.testapp_kotlin.FCM

// 알림 데이터 클래스 (모델에 해당)
data class NotificationData(
    val title: String,
    val body: String,
    val timestamp: Long = System.currentTimeMillis()
)