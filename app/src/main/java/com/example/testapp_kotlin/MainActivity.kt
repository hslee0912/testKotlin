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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.testapp_kotlin.Screeen.*
import com.example.testapp_kotlin.ui.theme.Brown80
import com.example.testapp_kotlin.ui.theme.TestApp_KotlinTheme
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.launch

// 매피니스트 권한 가져오기
import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import androidx.core.app.ActivityCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.testapp_kotlin.FCM.NotificationDataHolder
import com.example.testapp_kotlin.FCM.NotificationViewModel
import com.example.testblutooth.BleManager

// 필요한 객체들 가져오기 (MVVM 모델 적용)
import com.example.testapp_kotlin.Screeen.LocalBleManager
import com.example.testapp_kotlin.Screeen.ViewModelManager

class MainActivity : ComponentActivity() {

    // FCM 알림 관찰하는 ViewModel 객체 세팅
    private val notificationViewModel = NotificationViewModel()
    // 블루투스 관리할 객체
    private lateinit var bleManager: BleManager

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

    // 블루투스 활성화 요청
    private val enableBluetoothLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            Toast.makeText(this, "블루투스가 활성화되었습니다.", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "블루투스 활성화가 필요합니다.", Toast.LENGTH_SHORT).show()
        }
    }

    // 권한 요청
    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            Toast.makeText(this, "모든 권한이 승인되었습니다.", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "일부 권한이 거부되었습니다. 앱이 제대로 작동하지 않을 수 있습니다.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
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
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                NotificationDataHolder.notificationData.collect { notificationData ->
                    notificationData?.let {
                        notificationViewModel.updateNotificationData(it.title, it.body)
                    }
                }
            }
        }

        // BleManager 초기화
        bleManager = BleManager(this)

        // BLE 지원 확인
        if (!bleManager.isBleSupported()) {
            Toast.makeText(this, "이 기기는 BLE를 지원하지 않습니다.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // 블루투스 활성화 확인
        if (!bleManager.isBluetoothEnabled()) {
            requestEnableBluetooth()
        }

        // 필요한 권한 요청
        requestRequiredPermissions()

        setContent {
            TestApp_KotlinTheme {
                AppScreen(bleManager, notificationViewModel)
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

            // 토큰을 서버에 등록하는 로직
            // sendTokenToServer(token)

            // 필요에 따라 토픽 구독
            // subscribeToTopic("news")
        }
    }

//    private fun subscribeToTopic(topic: String) {
//        FirebaseMessaging.getInstance().subscribeToTopic(topic)
//            .addOnCompleteListener { task ->
//                if (task.isSuccessful) {
//                    Log.d(TAG, "토픽 구독 성공: $topic")
//                } else {
//                    Log.e(TAG, "토픽 구독 실패: $topic", task.exception)
//                }
//            }
//    }

    private fun handleIntent(intent: Intent) {
        // 알림을 통해 앱이 시작된 경우의 데이터 처리
        intent.extras?.let { extras ->
            if (extras.getBoolean("notification", false)) {
                // 알림 데이터 처리
                for (key in extras.keySet()) {
                    val value = extras.getString(key)
                    Log.d(TAG, "키: $key 값: $value")
                }

                // 필요한 UI 업데이트 또는 네비게이션
            }
        }
    }

    private fun requestEnableBluetooth() {
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val bluetoothAdapter = bluetoothManager.adapter

        if (bluetoothAdapter != null && !bluetoothAdapter.isEnabled) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                enableBluetoothLauncher.launch(enableBtIntent)
            } else {
                // 권한이 없는 경우 권한부터 요청
                requestRequiredPermissions()
            }
        }
    }

    private fun requestRequiredPermissions() {
        val permissionsToRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        } else {
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        }
        requestPermissionsLauncher.launch(permissionsToRequest)
    }

    override fun onDestroy() {
        super.onDestroy()
        bleManager.close()
    }

    companion object {
        private const val TAG = "MainActivity"
    }
}

@Composable
fun AppScreen(bleManager: BleManager, notificationViewModel: NotificationViewModel) {
    // ============ 필요한 객체 선언 ===============
    // 탭 정보 정의
    val tabs = listOf(
        TabInfo(Icons.Default.Home, "Home"),
        TabInfo(Icons.Default.Map, "Map"),
        TabInfo(Icons.Default.Bluetooth, "Bluetooth"),
        TabInfo(Icons.Default.Email, "Message"),
        TabInfo(Icons.Default.Camera, "Camera")
    )

    val pagerState = rememberPagerState(pageCount = { tabs.size })
    val coroutineScope = rememberCoroutineScope()

    // 실제 위젯이 만들어지는 UI 코드
    Scaffold(
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFADD8E6)) // 연한 파란색 배경
            ) {
                Text(
                    text = "App 타이틀",
                    color = Color.Black,
                    fontSize = 20.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 32.dp, bottom = 8.dp)
                )

                // 상단 탭 바
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    // 함수형으로 수정
                    tabs.mapIndexed { index, tab ->
                    // tabs.forEachIndexed { index, tab ->
                        val selected = pagerState.currentPage == index

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .padding(4.dp)
                        ) {
                            IconButton(
                                onClick = {
                                    coroutineScope.launch {
                                        pagerState.animateScrollToPage(index)
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = tab.icon,
                                    contentDescription = tab.title,
                                    tint = if (selected) Color.Black else Color.Gray
                                )
                            }
                            Text(
                                text = tab.title,
                                fontSize = 12.sp,
                                color = if (selected) Color.Black else Color.Gray
                            )

                            // 선택된 탭 표시
                            if (selected) {
                                HorizontalDivider(
                                    modifier = Modifier
                                        .width(40.dp)
                                        .padding(top = 2.dp),
                                    thickness = 2.dp,
                                    color = Color.Black
                                )
                            }
                        }
                    }
                }
            }
        },

    ) { innerPadding ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) { page ->
            // 각 페이지별 컴포넌트 호출
            when (page) {
                0 -> HomeScreen()
                1 -> MapScreen()
                2 -> CompositionLocalProvider(LocalBleManager provides bleManager) {
                    BlueToothScreen()
                }
                3 -> CompositionLocalProvider(ViewModelManager provides notificationViewModel) {
                    MessageScreen()
                }
                4 -> CameraScreen()
                else -> Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Brown80),
                    contentAlignment = Alignment.Center
                ) {
                    Text("페이지를 찾을 수 없습니다", color = Color.Black)
                }
            }
        }
    }
}

// 탭 정보를 담는 데이터 클래스
data class TabInfo(
    val icon: ImageVector,
    val title: String
)
