package com.example.testapp_kotlin.Screeen

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.content.pm.PackageManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.testblutooth.BleManager

// BleManager 제공을 위한 CompositionLocal
val LocalBleManager = compositionLocalOf<BleManager> { error("BleManager not provided") }

@Composable
fun BlueToothScreen() {
    // ============ 필요한 객체 선언 ===============
    // 블루투스 관련 객체
    val bleManager = LocalBleManager.current
    val bleState by bleManager.bleState.collectAsState()
    val context = LocalContext.current
    val scrollState = rememberScrollState() // 스크롤 상태 추가

    // 로딩 창
    val snackbarHostState = remember { SnackbarHostState() }
    // 보낼 데이터 값
    var inputData by remember { mutableStateOf("") }

    // 에러 메시지 처리
    LaunchedEffect(key1 = bleState.errorMessage) {
        bleState.errorMessage?.let { error ->
            snackbarHostState.showSnackbar(message = error)
        }
    }

    // 권한 체크 (hasPermissions == true 여야 제대로 동작함)
    val hasPermissions = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
    } else {
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    // 컴포넌트들을 함수로 빼자
    // 1. 스캔 버튼 영역
    @Composable
    fun BuildScanCard() {
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "블루투스 장치 스캔",
                    style = MaterialTheme.typography.titleMedium
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (bleState.isScanning) "스캔 중..." else "장치를 스캔하려면 버튼을 누르세요",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    Button(
                        onClick = {
                            // 권한이 있을 때만 실행
                            if (hasPermissions) {
                                // 스캐닝 중일 때와 아닐때 나눠서 체크한다.
                                if (bleState.isScanning) {
                                    bleManager.stopScan()
                                } else {
                                    bleManager.startScan()
                                }
                            }
                        },
                        enabled = hasPermissions && bleManager.isBluetoothEnabled()
                    ) {
                        Text(if (bleState.isScanning) "중지" else "스캔")
                    }
                }

                if (bleState.isScanning) {
                    Spacer(modifier = Modifier.height(8.dp))
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                }
            }
        }
    }

    // 2. 발견된 장치 영역
    @Composable
    fun BuildDevicesCard() {
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "발견된 장치 (${bleState.deviceList.size})",
                    style = MaterialTheme.typography.titleMedium
                )

                Spacer(modifier = Modifier.height(8.dp))

                if (bleState.deviceList.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(80.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "발견된 장치가 없습니다",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    // 일반 Column으로 장치 목록 표시
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        bleState.deviceList.forEach { device ->
                            DeviceItem(
                                device = device,
                                isConnected = bleState.connectedDevice?.address == device.address,
                                onClick = {
                                    if (hasPermissions) {
                                        if (bleState.connectedDevice?.address == device.address) {
                                            bleManager.disconnect()
                                        } else {
                                            bleManager.connect(device)
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }

    }

    // 3. 데이터 로그 영역
    @Composable
    fun BuildDataLogCard() {
        Card(
            modifier = Modifier.fillMaxWidth()
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
                        text = "데이터 로그",
                        style = MaterialTheme.typography.titleMedium
                    )

                    IconButton(
                        onClick = {
                            // 로그 지우기
                            bleManager.clearLogs()
                        }
                    ) {
                        Icon(Icons.Default.Clear, contentDescription = "로그 지우기")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 로그 표시 영역 (LazyColumn 사용)
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp) // 고정 높이 설정
                ) {
                    if (bleState.dataLog.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "로그가 없습니다",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White
                            )
                        }
                    } else {
                        // LazyColumn으로 로그 표시
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black)
                                .padding(8.dp)
                        ) {
                            items(bleState.dataLog.reversed()) { logEntry ->
                                Column {
                                    Text(
                                        text = logEntry,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.White,
                                        modifier = Modifier.padding(vertical = 4.dp, horizontal = 8.dp)
                                    )
                                    HorizontalDivider(
                                        thickness = 0.5.dp,
                                        color = Color.White.copy(alpha = 0.3f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // 4. 데이터 전송 영역
    @Composable
    fun BuildDataTransferCard() {
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "데이터 전송",
                    style = MaterialTheme.typography.titleMedium
                )

                Spacer(modifier = Modifier.height(8.dp))

                // 현재 연결 상태
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = if (bleState.isConnected) Icons.Default.CheckCircle else Icons.Default.Lock,
                        contentDescription = "연결 상태",
                        tint = if (bleState.isConnected) MaterialTheme.colorScheme.primary else Color.Red
                    )

                    Spacer(modifier = Modifier.padding(8.dp))

                    Text(
                        text = if (bleState.isConnected)
                            "연결됨: ${bleState.connectedDevice?.name ?: "알 수 없는 장치"}"
                        else
                            "연결된 장치 없음",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (bleState.isConnected) MaterialTheme.colorScheme.onSurface else Color.Red
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 데이터 입력 필드
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = inputData,
                        onValueChange = { inputData = it },
                        label = { Text("전송할 데이터") },
                        enabled = bleState.isConnected,
                        modifier = Modifier.weight(1f),
                    )

                    Spacer(modifier = Modifier.padding(horizontal = 8.dp))

                    // 화살표 아이콘 버튼으로 변경
                    IconButton(
                        onClick = {
                            if (hasPermissions && inputData.isNotEmpty() && bleState.isConnected) {
                                bleManager.sendData(inputData)
                                inputData = ""
                            }
                        },
                        enabled = bleState.isConnected && inputData.isNotEmpty()
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "전송",
                            tint = if (bleState.isConnected && inputData.isNotEmpty())
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        )
                    }
                }
            }
        }
    }

    // =============== 화면 그리기 시작 부분 ===================
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Yellow)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(scrollState), // 스크롤 적용
            verticalArrangement = Arrangement.spacedBy(16.dp)
                //.verticalScroll(rememberScrollState())
        ) {
            // 스캔 버튼 영역
            BuildScanCard()

            // 발견된 장치 영역
            BuildDevicesCard()

            // 데이터 전송 영역
            BuildDataLogCard()

            // 데이터 로그 영역
            BuildDataTransferCard()
        }
    }
}

@Composable
fun DeviceItem(
    device: BluetoothDevice,
    isConnected: Boolean,
    onClick: () -> Unit
) {
    // 스크린샷에 맞게 DeviceItem UI 수정
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = if (isConnected) Color(0xFF1A4B81) else Color.Black, // 선택 시 파란색, 비선택 시 검은색
                shape = RoundedCornerShape(8.dp)
            )
            .border(
                width = 1.dp,
                color = if (isConnected) Color.Blue else Color.DarkGray,
                shape = RoundedCornerShape(8.dp)
            )
            .clickable(onClick = onClick)
            .padding(16.dp),
    ) {
        if (ActivityCompat.checkSelfPermission(
                LocalContext.current,
                Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = device.name ?: "알 수 없는 장치",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = device.address,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.LightGray
                )
            }

            // 선택된 경우 체크 아이콘 표시
            if (isConnected) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "선택됨",
                    tint = Color.White
                )
            }
        }
    }
}

