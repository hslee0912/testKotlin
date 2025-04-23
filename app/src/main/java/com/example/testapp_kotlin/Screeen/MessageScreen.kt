package com.example.testapp_kotlin.Screeen

import android.icu.text.SimpleDateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Email
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.testapp_kotlin.FCM.NotificationData
import com.example.testapp_kotlin.FCM.NotificationViewModel
import java.util.Date
import java.util.Locale

// ViewModel 제공을 위한 CompositionLocal
val ViewModelManager = compositionLocalOf<NotificationViewModel> { error("ViewModel not provided") }

@Composable
fun MessageScreen() {
    // ============ 필요한 객체 선언 ===============
    // 배경색 정의
    val backgroundColor = Color.Blue
    // 보색 계산 (RGB 색상 반전)
    val complementaryColor = Color(
        red = 1f - backgroundColor.red,
        green = 1f - backgroundColor.green,
        blue = 1f - backgroundColor.blue
    )
    // FCM 데이처 리스트 객체 가져오기
    val _viewModel = ViewModelManager.current
    val notifications by _viewModel.notificationList.collectAsState() // 알림 목록 상태 수집

    // 실제 위젯이 만들어지는 UI 코드
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // 헤더
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Email,
                        contentDescription = "Message Icon",
                        tint = Color.White,
                        modifier = Modifier.size(50.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Messages",
                        color = Color.White,
                        fontSize = 24.sp,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            // 알림 목록 표시
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                // FCM 뷰모델 데이터들을 넣어주는 패턴 (MVVM 적용)
                items(
                    items = notifications,
                    key = { it.timestamp } // 성능 최적화를 위한 key 지정
                ) { notification ->
                    NotificationCard(
                        notificationData = notification,
                        onDismiss = { _viewModel.removeNotification(notification) },
                        cardColor = complementaryColor
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

// 메세지 카드 콤포넌트
@Composable
fun NotificationCard(
    notificationData: NotificationData,
    onDismiss: () -> Unit,
    cardColor: Color
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = cardColor,
            contentColor = Color.Black // 주황색 배경에는 검정색 텍스트가 잘 보임
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = notificationData.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "닫기"
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(text = notificationData.body)

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                    .format(Date(notificationData.timestamp)),
                style = MaterialTheme.typography.bodySmall,
                color = Color.DarkGray
            )
        }
    }
}

