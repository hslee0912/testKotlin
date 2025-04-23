package com.example.testapp_kotlin.Screeen

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaActionSound
import android.provider.MediaStore
import android.util.Log
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executors

@Composable
fun CameraScreen() {
    // ============ 필요한 객체 선언 ===============
    val context = LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

    // 카메라 상태 관리
    var lensFacing by remember { mutableIntStateOf(CameraSelector.LENS_FACING_BACK) }
    var hasPermission by remember { mutableStateOf(false) }

    // 카메라 소리를 위한 MediaActionSound 객체
    val cameraSound = remember { MediaActionSound() }

    // 이미지 캡처 객체 생성
    val imageCapture = remember { ImageCapture.Builder().build() }
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }

    // 필요한 권한 체크
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasPermission = isGranted
    }

    // 카메라 세팅
    fun initCameraSetting() {
        // CameraX Preview 설정
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            // 카메라 선택 (전면/후면)
            val cameraSelector = CameraSelector.Builder()
                .requireLensFacing(lensFacing)
                .build()

            try {
                // 기존 바인딩 해제 후 새로 바인딩
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    imageCapture
                )
            } catch (e: Exception) {
                Log.e("CameraPreview", "바인딩 실패", e)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    // 갤러리 실행을 위한 launcher
    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        // 갤러리에서 이미지 선택 후 처리 (필요시 구현)
    }

    // 필요한 권한 확인 및 요청
    LaunchedEffect(Unit) {
        hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasPermission) {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
        // 카메라 관련 초기화
        initCameraSetting()
    }

    // 실제 위젯이 만들어지는 UI 코드
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFA52A2A)) // 브라운 색상
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // 카메라 프리뷰 영역
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(16.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Black.copy(alpha = 0.7f)),
                contentAlignment = Alignment.Center
            ) {
                if (hasPermission) {
                    CameraPreview(
                        modifier = Modifier.fillMaxSize(),
                        imageCapture = imageCapture,
                        lensFacing = lensFacing,
                        lifecycleOwner = lifecycleOwner
                    )
                } else {
                    // 권한이 없는 경우 표시
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Camera,
                            contentDescription = "Camera Icon",
                            tint = Color.White.copy(alpha = 0.5f),
                            modifier = Modifier.size(80.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Camera permission required",
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 18.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            // 카메라 컨트롤 영역
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .background(Color.Black.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 갤러리 버튼
                    IconButton(
                        onClick = {
                            // 갤러리 열기
                            val intent = Intent(
                                Intent.ACTION_PICK,
                                MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                            )
                            galleryLauncher.launch(intent)
                        },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PhotoLibrary,
                            contentDescription = "Gallery",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    // 촬영 버튼
                    IconButton(
                        onClick = {
                            if (hasPermission) {
                                // 카메라 소리 재생
                                cameraSound.play(MediaActionSound.SHUTTER_CLICK)

                                // 사진 촬영
                                takePhoto(
                                    context = context,
                                    imageCapture = imageCapture,
                                    executor = cameraExecutor
                                )

                                // Toast 메시지 표시
                                Toast.makeText(
                                    context,
                                    "카메라가 찍혔습니다",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        },
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.2f))
                            .padding(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(60.dp)
                                .clip(CircleShape)
                                .background(Color.White)
                                .border(width = 2.dp, color = Color.LightGray, shape = CircleShape)
                        )
                    }

                    // 카메라 컨트롤 버튼
                    Row {
                        // 카메라 전환 버튼
                        IconButton(
                            onClick = {
                                // 카메라 전환
                                lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                                    CameraSelector.LENS_FACING_FRONT
                                } else {
                                    CameraSelector.LENS_FACING_BACK
                                }
                            },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.FlipCameraAndroid,
                                contentDescription = "Switch Camera",
                                tint = Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CameraPreview(
    modifier: Modifier = Modifier,
    imageCapture: ImageCapture,
    lensFacing: Int,
    lifecycleOwner: LifecycleOwner
) {
    val context = LocalContext.current
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }

    AndroidView(
        factory = { ctx ->
            val previewView = PreviewView(ctx).apply {
                this.scaleType = PreviewView.ScaleType.FILL_CENTER
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }

            // CameraX Preview 설정
            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()

                // Preview 객체 생성
                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                }

                // 카메라 선택 (전면/후면)
                val cameraSelector = CameraSelector.Builder()
                    .requireLensFacing(lensFacing)
                    .build()

                try {
                    // 기존 바인딩 해제 후 새로 바인딩
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        preview,
                        imageCapture
                    )
                } catch (e: Exception) {
                    Log.e("CameraPreview", "바인딩 실패", e)
                }
            }, ContextCompat.getMainExecutor(ctx))

            previewView
        },
        modifier = modifier,
        update = { previewView ->
            // 카메라 렌즈 방향 변경 시 업데이트
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.surfaceProvider = previewView.surfaceProvider
            }

            val cameraSelector = CameraSelector.Builder()
                .requireLensFacing(lensFacing)
                .build()

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    imageCapture
                )
            } catch (e: Exception) {
                Log.e("CameraPreview", "업데이트 중 바인딩 실패", e)
            }
        }
    )
}

private fun takePhoto(
    context: Context,
    imageCapture: ImageCapture,
    executor: java.util.concurrent.Executor
) {
    // 저장될 파일 생성
    val contentValues = ContentValues().apply {
        put(
            MediaStore.MediaColumns.DISPLAY_NAME,
            SimpleDateFormat("yyyy-MM-dd-HH-mm-ss", Locale.US).format(System.currentTimeMillis())
        )
        put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
    }

    // 저장 옵션 설정
    val outputOptions = ImageCapture.OutputFileOptions.Builder(
        context.contentResolver,
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
        contentValues
    ).build()

    // 사진 촬영
    imageCapture.takePicture(
        outputOptions,
        executor,
        object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                // 이미지 저장 성공
                val savedUri = outputFileResults.savedUri
                Log.d("CameraScreen", "이미지 저장 성공: $savedUri")

                // 갤러리에 알림
                savedUri?.let {
                    context.sendBroadcast(Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, it))
                }
            }

            override fun onError(exception: ImageCaptureException) {
                // 이미지 저장 실패
                Log.e("CameraScreen", "이미지 저장 실패", exception)

                // 에러 발생 시 Toast 메시지 표시
                Toast.makeText(
                    context,
                    "사진 저장에 실패했습니다: ${exception.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    )
}

// CameraX가 필요한 의존성 목록:
// 1. implementation("androidx.camera:camera-core:1.3.1")
// 2. implementation("androidx.camera:camera-camera2:1.3.1")
// 3. implementation("androidx.camera:camera-lifecycle:1.3.1")
// 4. implementation("androidx.camera:camera-view:1.3.1")