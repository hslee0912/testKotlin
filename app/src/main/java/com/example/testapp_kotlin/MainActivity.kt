package com.example.testapp_kotlin

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
// 로그가 통일되게 수정
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.testapp_kotlin.Screeen.*
import com.example.testapp_kotlin.ui.theme.TestApp_KotlinTheme
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.launch

// 매피니스트 권한 가져오기
import android.Manifest
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.testapp_kotlin.FCM.NotificationDataHolder
import com.example.testapp_kotlin.FCM.NotificationViewModel

// 필요한 객체들 가져오기 (MVVM 모델 적용)
import com.example.testapp_kotlin.Screeen.ViewModelManager

class MainActivity : ComponentActivity() {

    // FCM 알림 관찰하는 ViewModel 객체 세팅
    private val notificationViewModel = NotificationViewModel()

    // FCM 관련 함수
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    )
    {
        isGranted: Boolean ->
        if (isGranted) {
            Toast.makeText(this, "알림 권한이 허용되었습니다", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "알림을 받으려면 권한이 필요합니다", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // FCM 관련 함수
        // 푸시 알림 권한 요청
        requestNotificationPermission()

        // FCM 토큰 가져오기
        getFCMToken()

        // 알림으로 앱이 시작된 경우 처리
        handleIntent(intent)

        // 싱글톤 객체의 알림 데이터 관찰 (알람이 오면 이쪽으로 데이터가 들어온다)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.CREATED) {
                NotificationDataHolder.notificationData.collect { notificationData ->
                    notificationData?.let {
                        notificationViewModel.updateNotificationData(
                            it.title,
                            it.body
                        )
                    }
                }
            }
        }

        setContent {
            TestApp_KotlinTheme {
                AppScreen(notificationViewModel)
            }
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun getFCMToken() {
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (!task.isSuccessful) {
                Log.w(TAG, "FCM 등록 토큰 가져오기 실패", task.exception)
                return@addOnCompleteListener
            }

            val token = task.result
            Log.d(TAG, "FCM 토큰: $token")
        }
    }

    private fun handleIntent(intent: Intent) {
        // 알림을 통해 앱이 시작된 경우의 데이터 처리
        intent.extras?.let { extras ->
            if (extras.getBoolean("notification", false)) {
                // 알림 데이터 처리
                for (key in extras.keySet()) {
                    val value = extras.getString(key)
                    Log.d(TAG, "키: $key 값: $value")
                }
            }
        }
    }

    companion object {
        private const val TAG = "MainActivity"
    }
}

@Composable
fun AppScreen(notificationViewModel: NotificationViewModel) {
    // ============ 필요한 객체 선언 ===============
    val pagerState = rememberPagerState(pageCount = { 1 })

    // 실제 위젯이 만들어지는 UI 코드
    Scaffold(
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFADD8E6)) // 연한 파란색 배경
            ) {
                Text(
                    text = "Kotlin Test App",
                    color = Color.Black,
                    fontSize = 20.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 32.dp, bottom = 8.dp)
                )
            }
        },

    ) { innerPadding ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            CompositionLocalProvider(ViewModelManager provides notificationViewModel) {
            MessageScreen()
            }
        }
    }
}
