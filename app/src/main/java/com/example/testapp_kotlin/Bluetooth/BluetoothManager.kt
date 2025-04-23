package com.example.testblutooth

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID

class BleManager(private val context: Context) {
    // ============ 필요한 객체 선언 ===============
    companion object {
        private const val TAG = "BleManager"
        private const val SCAN_PERIOD =  3000L // 3초 스캔 // 10000L // 10초 스캔

        // 블루투스 서비스 및 특성 UUID (필요에 따라 변경 가능)
        var NEW_SERVICE_UUID = ""
        var NEW_CHARACTERISTIC_UUID = ""
        var NEW_DESCRIPTOR_UUID = ""
    }

    // 리스너 인터페이스
    interface BleManagerListener {
        fun onStateChanged()
    }

    // 리스너 목록
    private val listeners = mutableListOf<BleManagerListener>()

    // 리스너 추가 및 제거 메소드
    fun addListener(listener: BleManagerListener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener)
        }
    }

    fun removeListener(listener: BleManagerListener) {
        listeners.remove(listener)
    }

    // 리스너에게 상태 변경 알림
    private fun notifyListeners() {
        listeners.forEach { it.onStateChanged() }
    }

    // 블루투스 상태와 관련된 데이터 클래스
    data class BleState(
        val isScanning: Boolean = false,
        val deviceList: List<BluetoothDevice> = emptyList(),
        val connectedDevice: BluetoothDevice? = null,
        val isConnected: Boolean = false,
        val dataLog: List<String> = emptyList(),
        val errorMessage: String? = null
    )

    // 내부 상태를 위한 MutableStateFlow
    private val _bleState = MutableStateFlow(BleState())

    // 외부에 노출할 읽기 전용 StateFlow
    val bleState: StateFlow<BleState> = _bleState.asStateFlow()

    // 블루투스 어댑터
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
    private var bluetoothLeScanner: BluetoothLeScanner? = null

    // GATT 연결
    private var bluetoothGatt: BluetoothGatt? = null

    // 스캐닝 타이머
    private val handler = Handler(Looper.getMainLooper())

    // 스캔 콜백
    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            // 이름 없는 것은 찾지 않음
            val deviceName = result.scanRecord?.deviceName
            if(deviceName == null)
                return
            val device = result.device
            // 중복 기기 필터링
            if (!_bleState.value.deviceList.contains(device)) {
                _bleState.update { currentState ->
                    currentState.copy(
                        deviceList = currentState.deviceList + device
                    )
                }
                if (ActivityCompat.checkSelfPermission(
                        context,
                        Manifest.permission.BLUETOOTH_CONNECT
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    // TODO: Consider calling
                    //    ActivityCompat#requestPermissions
                    // here to request the missing permissions, and then overriding
                    //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                    //                                          int[] grantResults)
                    // to handle the case where the user grants the permission. See the documentation
                    // for ActivityCompat#requestPermissions for more details.
                    return
                }
                addLog("장치 발견: ${device.name ?: device.address}")
            }
        }

        override fun onScanFailed(errorCode: Int) {
            _bleState.update { it.copy(
                isScanning = false,
                errorMessage = "스캔 실패: 오류 코드 $errorCode"
            ) }
            addLog("스캔 실패: 오류 코드 $errorCode")
            notifyListeners()
        }
    }

    // GATT 콜백
    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                when (newState) {
                    BluetoothProfile.STATE_CONNECTED -> {
                        addLog("GATT 서버에 연결됨")
                        // 연결됨으로 상태 업데이트
                        _bleState.update { it.copy(
                            isConnected = true,
                            connectedDevice = gatt.device,
                            errorMessage = null
                        ) }
                        notifyListeners()

                        // 서비스 검색 시작
                        if (ActivityCompat.checkSelfPermission(
                                context,
                                Manifest.permission.BLUETOOTH_CONNECT
                            ) != PackageManager.PERMISSION_GRANTED
                        ) {
                            return
                        }
                        gatt.discoverServices()
                    }
                    BluetoothProfile.STATE_DISCONNECTED -> {
                        addLog("GATT 서버에서 연결 해제됨")
                        // 연결 해제로 상태 업데이트
                        _bleState.update { it.copy(
                            isConnected = false,
                            connectedDevice = null
                        ) }
                        notifyListeners()
                        gatt.close()
                    }
                }
            } else {
                // 연결 실패 처리
                addLog("연결 오류 $status 발생 (${gatt.device.address})! 연결 해제 중...")
                _bleState.update { it.copy(
                    isConnected = false,
                    errorMessage = "연결 오류: 상태 코드 $status"
                ) }
                notifyListeners()
                gatt.close()
                bluetoothGatt = null
            }
        }

        private fun exploreServices(gatt: BluetoothGatt) {
            if (ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
            addLog("${gatt.device.name ?: gatt.device.address}의 사용 가능한 서비스:")

            for (service in gatt.services) {
                addLog("서비스: ${service.uuid}")
                NEW_SERVICE_UUID = service.uuid.toString()

                for (characteristic in service.characteristics) {
                    addLog("특성: ${characteristic.uuid}")
                    NEW_CHARACTERISTIC_UUID = characteristic.uuid.toString()

                    // 특성의 속성 확인 (읽기, 쓰기, 알림 등)
                    val properties = characteristic.properties
                    val propertyString = StringBuilder()

                    if ((properties and BluetoothGattCharacteristic.PROPERTY_READ) != 0) {
                        propertyString.append("READ ")
                    }
                    if ((properties and BluetoothGattCharacteristic.PROPERTY_WRITE) != 0) {
                        propertyString.append("WRITE ")
                    }
                    if ((properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0) {
                        propertyString.append("NOTIFY ")
                    }

                    addLog("속성: $propertyString")

                    // Descriptor UUID 탐색
                    for (descriptor in characteristic.descriptors) {
                        addLog("Descriptor: ${descriptor.uuid}")
                        NEW_DESCRIPTOR_UUID = descriptor.uuid.toString()
                    }
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                addLog("서비스 검색 완료")

                // 찾은 서비스의 데이터 뽑아내기
                exploreServices(gatt)

                /*
                // 필요한 서비스와 특성 찾기
                val service = gatt.getService(UUID.fromString(NEW_SERVICE_UUID))
                service?.let { svc ->
                    val characteristic = svc.getCharacteristic(UUID.fromString(NEW_CHARACTERISTIC_UUID))
                    characteristic?.let { char ->
                        // 알림 활성화
                        if (ActivityCompat.checkSelfPermission(
                                context,
                                Manifest.permission.BLUETOOTH_CONNECT
                            ) != PackageManager.PERMISSION_GRANTED
                        ) {
                            return
                        }
                        enableNotifications(char)
                    }
                }
                */
            } else {
                addLog("서비스 검색 실패: 상태 코드 $status")
                _bleState.update { it.copy(
                    errorMessage = "서비스 검색 실패: 상태 코드 $status"
                ) }
                notifyListeners()
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            // 데이터가 변경됨
            val data = characteristic.value
            val dataString = data.joinToString(separator = " ") { byte -> "%02X".format(byte) }

            // 로그에 데이터 추가
            addLog("수신: $dataString")
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            val data = characteristic.value
            val dataString = data.joinToString(separator = " ") { byte -> "%02X".format(byte) }

            if (status == BluetoothGatt.GATT_SUCCESS) {
                // 로그에 데이터 추가
                addLog("전송 성공: $dataString")
            } else {
                addLog("전송 실패: $dataString (오류 $status)")
                _bleState.update { it.copy(
                    errorMessage = "데이터 전송 실패: 상태 코드 $status"
                ) }
                notifyListeners()
            }
        }
    }

    // 로그 추가 메소드
    private fun addLog(message: String) {
        _bleState.update { currentState ->
            currentState.copy(dataLog = currentState.dataLog + message)
        }
        // 디버그 로그 유지 (선택적)
        Log.d(TAG, message)
        notifyListeners()
    }

    // BLE 지원 여부 확인
    fun isBleSupported(): Boolean {
        return bluetoothAdapter != null && context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)
    }

    // 블루투스 활성화 여부 확인
    fun isBluetoothEnabled(): Boolean {
        return bluetoothAdapter?.isEnabled == true
    }

    // 필요한 권한 체크
    fun hasRequiredPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            hasBlePermission(Manifest.permission.BLUETOOTH_SCAN) &&
                    hasBlePermission(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            hasBlePermission(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    private fun hasBlePermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    // 스캔 시작
    @RequiresPermission(allOf = [Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT])
    fun startScan() {
        if (!isBluetoothEnabled()) {
            addLog("블루투스가 비활성화 상태입니다.")
            _bleState.update { it.copy(errorMessage = "블루투스가 비활성화 상태입니다.") }
            notifyListeners()
            return
        }

        if (_bleState.value.isScanning) {
            return
        }

        // 기존 장치 목록 초기화
        _bleState.update { it.copy(
            deviceList = emptyList(),
            isScanning = true,
            errorMessage = null
        ) }
        notifyListeners()

        bluetoothLeScanner = bluetoothAdapter?.bluetoothLeScanner

        // 스캔 설정
        val scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        // 스캔 필터 (선택적)
        val scanFilters = listOf<ScanFilter>()

        addLog("스캔 시작...")

        // 스캔 시작
        bluetoothLeScanner?.startScan(scanFilters, scanSettings, scanCallback)

        // 지정된 시간 후 스캔 중지
        handler.postDelayed({ stopScan() }, SCAN_PERIOD)
    }

    // 스캔 중지
    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    fun stopScan() {
        if (_bleState.value.isScanning) {
            bluetoothLeScanner?.stopScan(scanCallback)
            _bleState.update { it.copy(isScanning = false) }
            addLog("스캔 중지됨")
            notifyListeners()
        }
    }

    // 장치 연결
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun connect(device: BluetoothDevice) {
        if (_bleState.value.isConnected) {
            disconnect()
        }

        addLog("${device.name ?: device.address}에 연결 중...")

        // GATT 서버에 연결
        bluetoothGatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        } else {
            device.connectGatt(context, false, gattCallback)
        }
    }

    // 연결 해제
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun disconnect() {
        bluetoothGatt?.let { gatt ->
            addLog("연결 해제 중...")
            gatt.disconnect()
            gatt.close()
            bluetoothGatt = null

            _bleState.update { it.copy(
                isConnected = false,
                connectedDevice = null
            ) }
            notifyListeners()
        }
    }

    // 데이터 전송
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun sendData(data: String): Boolean {
        if (!_bleState.value.isConnected || bluetoothGatt == null) {
            addLog("연결된 기기가 없습니다.")
            _bleState.update { it.copy(errorMessage = "연결된 기기가 없습니다.") }
            notifyListeners()
            return false
        }

        try {
            val service = bluetoothGatt?.getService(UUID.fromString(NEW_SERVICE_UUID)) ?: return false
            val characteristic = service.getCharacteristic(UUID.fromString(NEW_CHARACTERISTIC_UUID)) ?: return false

            // 데이터 설정
            characteristic.setValue(data)

            addLog("전송: $data")

            // 데이터 전송
            return bluetoothGatt?.writeCharacteristic(characteristic) == true
        }
        catch (e: Exception) {
            addLog("전송 에러 발생: $e")
            return false
        }
    }

    // 알림 활성화
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun enableNotifications(characteristic: BluetoothGattCharacteristic) {
        val bluetoothGatt = this.bluetoothGatt ?: return

        // 특성에 대한 알림 활성화
        bluetoothGatt.setCharacteristicNotification(characteristic, true)

        // CCCD(Client Characteristic Configuration Descriptor) 설정
        val descriptor = characteristic.getDescriptor(UUID.fromString(NEW_DESCRIPTOR_UUID))
        descriptor?.let {
            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            bluetoothGatt.writeDescriptor(descriptor)
            addLog("알림 활성화: ${characteristic.uuid}")
        }
    }

    // 로그 지우기
    fun clearLogs() {
        _bleState.update { it.copy(
            dataLog = emptyList()
        ) }
        addLog("로그가 지워졌습니다")
        notifyListeners()
    }

    // 리소스 정리
    fun close() {
        try {
            if (ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
            bluetoothGatt?.close()
            bluetoothGatt = null
        } catch (e: Exception) {
            Log.e(TAG, "Error closing GATT connection", e)
        }
    }
}