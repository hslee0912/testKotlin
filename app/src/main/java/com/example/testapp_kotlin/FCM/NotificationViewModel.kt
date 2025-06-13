package com.example.testapp_kotlin.FCM

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class NotificationViewModel : ViewModel() {
    // ============ 필요한 객체 선언 ===============
    /// 단일 알림 데이터
    private val _notificationData = MutableStateFlow<NotificationData?>(null)

    // 알림 목록을 관리할 리스트
    private val _notificationList = MutableStateFlow<List<NotificationData>>(emptyList())
    val notificationList: StateFlow<List<NotificationData>> = _notificationList

    // 알림 데이터 업데이트 함수
    fun updateNotificationData(title: String, body: String) {
        val newNotification = NotificationData(title, body)
        val currentList = _notificationList.value.toMutableList()

        // 같은 데이터가 있는지 체크 한다
        val isDuplicate = currentList.contains(newNotification)
        if (isDuplicate)
            return

        _notificationData.value = newNotification

        // 목록에도 추가
        currentList.add(0, newNotification) // 새 알림을 목록 맨 위에 추가
        _notificationList.value = currentList
    }

    // 특정 알림 삭제
    fun removeNotification(notification: NotificationData) {
        val currentList = _notificationList.value.toMutableList()
        currentList.remove(notification)
        _notificationList.value = currentList
    }

    // 알림 데이터 초기화
    fun clearNotificationData() {
        _notificationData.value = null
    }
}

