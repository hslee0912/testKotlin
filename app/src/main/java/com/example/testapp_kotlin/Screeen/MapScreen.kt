package com.example.testapp_kotlin.Screeen

import android.Manifest
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.os.Bundle
import android.view.MotionEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.*

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun MapScreen() {
    // ============ 필요한 객체 선언 ===============
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // 위치 검색 상태
    var searchQuery by remember { mutableStateOf("") }
    var currentLocation by remember { mutableStateOf<LatLng?>(null) }
    var selectedLocationName by remember { mutableStateOf("") }

    // Google Maps 관련 상태
    var mapView by remember { mutableStateOf<MapView?>(null) }
    var googleMap by remember { mutableStateOf<GoogleMap?>(null) }

    // 지도 터치 상태 (스와이프 제어용)
    var isMapTouched by remember { mutableStateOf(false) }

    // 위치 서비스 클라이언트
    val fusedLocationClient: FusedLocationProviderClient = remember {
        LocationServices.getFusedLocationProviderClient(context)
    }

    // 초기 위치 설정 (서울 시청)
    LaunchedEffect(Unit) {
        val defaultLocation = LatLng(37.5665, 126.9780) // 서울 시청 좌표
        currentLocation = defaultLocation

        // 위치 권한이 있는 경우 현재 위치 가져오기
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
                location?.let {
                    currentLocation = LatLng(it.latitude, it.longitude)
                    selectedLocationName = "내 위치"

                    // 지도가 이미 초기화된 경우 현재 위치로 이동
                    googleMap?.let { map ->
                        updateMapLocation(map, LatLng(it.latitude, it.longitude), "내 위치")
                    }
                }
            }
        }
    }

    // 위치가 변경될 때 지도 업데이트
    LaunchedEffect(currentLocation, googleMap) {
        currentLocation?.let { location ->
            googleMap?.let { map ->
                updateMapLocation(map, location, selectedLocationName)
            }
        }
    }

    // 실제 위젯이 만들어지는 UI 코드
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Green)
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
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = "Location Icon",
                        tint = Color.White,
                        modifier = Modifier.size(50.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "위치 검색",
                        color = Color.Black,
                        fontSize = 24.sp,
                    )
                }
            }

            // 위치 검색창
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White,
                    focusedBorderColor = Color.DarkGray,
                    unfocusedBorderColor = Color.LightGray,
                    focusedTextColor = Color.Black,
                    unfocusedTextColor = Color.Black
                ),
                textStyle = TextStyle(color = Color.Black),
                placeholder = { Text("위치를 입력하세요", color = Color.Gray) },
                trailingIcon = {
                    IconButton(
                        onClick = {
                            scope.launch {
                                val result = searchLocation(searchQuery, context)
                                result?.let { (location, name) ->
                                    currentLocation = location
                                    selectedLocationName = name
                                }
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search"
                        )
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = {
                        scope.launch {
                            val result = searchLocation(searchQuery, context)
                            result?.let { (location, name) ->
                                currentLocation = location
                                selectedLocationName = name
                            }
                        }
                    }
                ),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 검색 결과가 있으면 표시
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            )
            {
                if (selectedLocationName.isNotEmpty()) {
                    Text(
                        text = "현재 위치: $selectedLocationName",
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = Color.Black
                        //textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Google Maps 표시
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(16.dp)
                    .background(Color.White)
            ) {
                // pointerInteropFilter를 사용하여 지도 터치 상태 감지
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInteropFilter { event ->
                            when (event.action) {
                                MotionEvent.ACTION_DOWN -> {
                                    // 터치 시작
                                    isMapTouched = true
                                    false // 이벤트를 소비하지 않고 계속 전달
                                }
                                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                                    // 터치 종료
                                    isMapTouched = false
                                    false // 이벤트를 소비하지 않고 계속 전달
                                }
                                else -> false // 다른 이벤트도 계속 전달
                            }
                        }
                ) {
                    AndroidView(
                        factory = { ctx ->
                            MapView(ctx).apply {
                                mapView = this
                                onCreate(Bundle())

                                // 부모 터치 가로채기 설정 (스와이프 방지)
                                this.setOnTouchListener { v, event ->
                                    // 지도가 터치되면 부모 스와이프를 비활성화하기 위해 이벤트 가로채기
                                    when (event.action) {
                                        MotionEvent.ACTION_DOWN -> {
                                            // 포인터가 지도에 닿았을 때 (터치 시작)
                                            v.parent.requestDisallowInterceptTouchEvent(true)
                                            isMapTouched = true
                                            false
                                        }
                                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                                            // 포인터가 지도에서 떨어졌을 때 (터치 종료)
                                            v.parent.requestDisallowInterceptTouchEvent(false)
                                            isMapTouched = false
                                            false
                                        }
                                        else -> false
                                    }
                                }

                                getMapAsync { map ->
                                    googleMap = map

                                    // 초기 설정
                                    map.uiSettings.apply {
                                        isZoomControlsEnabled = true
                                        isCompassEnabled = true
                                        isMyLocationButtonEnabled = false // 커스텀 버튼 사용
                                        isZoomGesturesEnabled = true // 확대/축소 제스처 활성화
                                        isRotateGesturesEnabled = true // 회전 제스처 활성화
                                        isScrollGesturesEnabled = true // 스크롤(드래그) 제스처 활성화
                                        isTiltGesturesEnabled = true // 기울기 제스처 활성화
                                    }

                                    // 현재 위치 활성화 (권한 있는 경우)
                                    if (ActivityCompat.checkSelfPermission(
                                            context,
                                            Manifest.permission.ACCESS_FINE_LOCATION
                                        ) == PackageManager.PERMISSION_GRANTED
                                    ) {
                                        map.isMyLocationEnabled = true
                                    }

                                    // 초기 위치로 이동
                                    currentLocation?.let { location ->
                                        updateMapLocation(map, location, selectedLocationName)
                                    }

                                    // 지도 클릭 이벤트
                                    map.setOnMapClickListener { latLng ->
                                        scope.launch {
                                            // 클릭한 위치의 주소 가져오기
                                            val geocoder = Geocoder(context, Locale.getDefault())

                                            try {
                                                @Suppress("DEPRECATION")
                                                val addresses = geocoder.getFromLocation(
                                                    latLng.latitude,
                                                    latLng.longitude,
                                                    1
                                                )

                                                var locationName = "선택한 위치"

                                                if (!addresses.isNullOrEmpty()) {
                                                    val address = addresses[0]
                                                    val sb = StringBuilder()

                                                    if (address.thoroughfare != null) {
                                                        sb.append(address.thoroughfare)
                                                    }

                                                    if (address.locality != null) {
                                                        if (sb.isNotEmpty()) sb.append(", ")
                                                        sb.append(address.locality)
                                                    }

                                                    if (address.countryName != null) {
                                                        if (sb.isNotEmpty()) sb.append(", ")
                                                        sb.append(address.countryName)
                                                    }

                                                    if (sb.isNotEmpty()) {
                                                        locationName = sb.toString()
                                                    }
                                                }

                                                currentLocation = latLng
                                                selectedLocationName = locationName
                                            } catch (e: Exception) {
                                                e.printStackTrace()
                                                currentLocation = latLng
                                                selectedLocationName = "선택한 위치"
                                            }
                                        }
                                    }
                                }
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
                        update = { view ->
                            // 맵뷰 라이프사이클 관리
                            mapView = view
                        }
                    )
                }

                // 현재 위치 버튼
                FloatingActionButton(
                    onClick = {
                        if (ActivityCompat.checkSelfPermission(
                                context,
                                Manifest.permission.ACCESS_FINE_LOCATION
                            ) == PackageManager.PERMISSION_GRANTED
                        ) {
                            fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
                                location?.let {
                                    currentLocation = LatLng(it.latitude, it.longitude)
                                    selectedLocationName = "내 위치"
                                }
                            }
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp),
                    containerColor = Color.White,
                    contentColor = Color.Blue
                ) {
                    Icon(
                        imageVector = Icons.Default.MyLocation,
                        contentDescription = "내 위치"
                    )
                }
            }
        }
    }

    // 지도 터치 상태에 따라 부모 스와이프 동작 제어
    DisposableEffect(isMapTouched) {
        // 안드로이드에서는 DisposableEffect 내에서 스와이프 동작을 직접 제어하기 어렵기 때문에
        // MapView의 터치 이벤트 내에서 처리합니다.
        onDispose { }
    }

    // MapView 라이프사이클 관리
    DisposableEffect(Unit) {
        mapView?.onStart()
        mapView?.onResume()

        onDispose {
            mapView?.onPause()
            mapView?.onStop()
            mapView?.onDestroy()
        }
    }
}

// 지도 위치 업데이트 함수
private fun updateMapLocation(map: GoogleMap, location: LatLng, title: String) {
    // 기존 마커 제거
    map.clear()

    // 새 마커 추가
    map.addMarker(
        MarkerOptions()
            .position(location)
            .title(title)
    )

    // 카메라 이동
    map.animateCamera(
        CameraUpdateFactory.newLatLngZoom(location, 15f)
    )
}

// 위치 검색 함수
private suspend fun searchLocation(
    query: String,
    context: android.content.Context
): Pair<LatLng, String>? {
    if (query.isBlank()) return null

    return withContext(Dispatchers.IO) {
        try {
            val geocoder = Geocoder(context, Locale.getDefault())

            @Suppress("DEPRECATION")
            val addresses: List<Address>? = geocoder.getFromLocationName(query, 1)

            if (!addresses.isNullOrEmpty()) {
                val address = addresses[0]
                val name = buildString {
                    if (address.featureName != null) append(address.featureName)
                    if (address.locality != null) {
                        if (isNotEmpty()) append(", ")
                        append(address.locality)
                    }
                    if (address.countryName != null) {
                        if (isNotEmpty()) append(", ")
                        append(address.countryName)
                    }
                }

                LatLng(address.latitude, address.longitude) to (name.ifEmpty { "검색한 위치" })
            } else {
                null
            }
        } catch (e: IOException) {
            e.printStackTrace()
            null
        }
    }
}