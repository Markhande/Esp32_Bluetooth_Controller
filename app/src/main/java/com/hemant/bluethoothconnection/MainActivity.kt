package com.hemant.bluethoothconnection

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.hemant.bluethoothconnection.databinding.ActivityMainBinding
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    companion object {
        private const val TAG = "BluetoothController"
        private const val REQUEST_ENABLE_BT = 1001
        private const val REQUEST_BLUETOOTH_PERMISSIONS = 1002
        private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }

    // UI Components
    private val readExecutor = Executors.newSingleThreadExecutor()
    private lateinit var statusIndicator: View
    private lateinit var statusText: TextView
    private lateinit var deviceSpinner: Spinner
    private lateinit var connectButton: Button
    private lateinit var disconnectButton: Button
   // private lateinit var editTextData: EditText
    private lateinit var sendButton: Button
    private lateinit var receivedDataText: TextView
    private lateinit var progressBar: ProgressBar

    // Bluetooth Components
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothSocket: BluetoothSocket? = null
    private var outputStream: OutputStream? = null
    private var inputStream: InputStream? = null
    private var isConnected = false

    // Threading
    private val executorService: ExecutorService = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    // Device Management
    private val pairedDevicesList = mutableListOf<BluetoothDevice>()
    private lateinit var deviceAdapter: ArrayAdapter<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initializeViews()
        setupBluetoothAdapter()
        checkBluetoothPermissions()
        setupClickListeners()
    }

    private fun initializeViews() {
        statusIndicator = findViewById(R.id.statusIndicator)
        statusText = findViewById(R.id.statusText)
        deviceSpinner = findViewById(R.id.deviceSpinner)
        connectButton = findViewById(R.id.connectButton)
        disconnectButton = findViewById(R.id.disconnectButton)
        //editTextData = findViewById(R.id.editTextData)
        sendButton = findViewById(R.id.sendButton)
        receivedDataText = findViewById(R.id.receivedDataText)
        progressBar = findViewById(R.id.progressBar)

        // Setup device spinner
        deviceAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, mutableListOf<String>())
        deviceAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        deviceSpinner.adapter = deviceAdapter

        updateConnectionStatus(false)
    }

    private fun setupBluetoothAdapter() {
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        if (bluetoothAdapter == null) {
            showError("Bluetooth not supported on this device")
            finish()
        }
    }

    private fun checkBluetoothPermissions() {
        val permissions = arrayOf(
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_SCAN
        )

        val missingPermissions = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missingPermissions.toTypedArray(), REQUEST_BLUETOOTH_PERMISSIONS)
        } else {
            checkBluetoothEnabled()
        }
    }

    private fun checkBluetoothEnabled() {
        bluetoothAdapter?.let { adapter ->
            if (!adapter.isEnabled) {
                val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                    startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT)
                }
            } else {
                loadPairedDevices()
            }
        }
    }

    private fun loadPairedDevices() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            return
        }

        bluetoothAdapter?.bondedDevices?.let { devices ->
            pairedDevicesList.clear()
            val deviceNames = mutableListOf<String>()

            for (device in devices) {
                pairedDevicesList.add(device)
                deviceNames.add("${device.name ?: "Unknown"} (${device.address})")
            }

            deviceAdapter.clear()
            deviceAdapter.addAll(deviceNames)
            deviceAdapter.notifyDataSetChanged()

            if (deviceNames.isEmpty()) {
                showInfo("No paired devices found. Please pair your ESP32 device first.")
            }
        }
    }

    private fun setupClickListeners() {
        connectButton.setOnClickListener {
            if (deviceSpinner.selectedItemPosition >= 0) {
                connectToDevice()
            } else {
                showError("Please select a device to connect")
            }
        }

        disconnectButton.setOnClickListener {
            disconnectFromDevice()
        }
        val abba = binding.sentFirstBtn
        abba.setOnClickListener {
            val count: String? = binding.etCountFirst.text.toString().trim()
            val value: String? = binding.etValueFirst.text.toString().trim()
            val index = "1"
            sendData(count, value, index)

        }

        binding.sentSecondBtn.setOnClickListener {
            val count: String? = binding.etCountSecond.text.toString().trim()
            val value: String? = binding.etValueSecond.text.toString().trim()
            val index = "2"
            sendData(count, value, index)
        }

        binding.sentThirdBtn.setOnClickListener {
            val count: String? = binding.etCountThird.text.toString().trim()
            val value: String? = binding.etValueThird.text.toString().trim()
            val index = "3"
            sendData(count, value, index)
        }

        binding.sentFourthBtn.setOnClickListener {
            val count: String? = binding.etCountFourth.text.toString().trim()
            val value: String? = binding.etValueFourth.text.toString().trim()
            val index = "4"
            sendData(count, value, index)
        }

        binding.sentFifthBtn.setOnClickListener {
            val count: String? = binding.etCountFifth.text.toString().trim()
            val value: String? = binding.etValueFifth.text.toString().trim()
            val index = "5"
            sendData(count, value, index)
        }

    }

    private fun connectToDevice() {
        if (pairedDevicesList.isEmpty()) {
            showError("No paired devices available")
            return
        }

        val selectedDevice = pairedDevicesList[deviceSpinner.selectedItemPosition]
        showProgress(true)
        updateConnectionStatus(false, "Connecting...")

        executorService.execute {
            try {
                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    mainHandler.post {
                        showError("Bluetooth permission not granted")
                        showProgress(false)
                    }
                    return@execute
                }

                bluetoothSocket = selectedDevice.createRfcommSocketToServiceRecord(SPP_UUID)
                bluetoothSocket?.connect()

                outputStream = bluetoothSocket?.outputStream
                inputStream = bluetoothSocket?.inputStream

                mainHandler.post {
                    isConnected = true
                    updateConnectionStatus(true, "Connected to ${selectedDevice.name}")
                    showProgress(false)
                    showSuccess("Successfully connected to ${selectedDevice.name}")
                    startDataReception()
                }

            } catch (e: IOException) {
                Log.e(TAG, "Connection failed", e)
                mainHandler.post {
                    showError("Connection failed: ${e.message}")
                    showProgress(false)
                    updateConnectionStatus(false)
                }
            }
        }
    }

    private fun disconnectFromDevice() {
        executorService.execute {
            try {
                isConnected = false
                outputStream?.close()
                inputStream?.close()
                bluetoothSocket?.close()

                mainHandler.post {
                    updateConnectionStatus(false, "Disconnected")
                    showInfo("Disconnected from device")
                }

            } catch (e: IOException) {
                Log.e(TAG, "Error during disconnection", e)
                mainHandler.post {
                    updateConnectionStatus(false, "Disconnection error")
                }
            }
        }
    }

    private fun sendData(count: String?, value:String?, index:String) {

        if (!isConnected || outputStream == null) {
            showError("Not connected to any device")
            return
        }

        val formattedData = "\"$index\"-\"$count\"-\"$value\"\r\n"

        executorService.execute {
            try {
                outputStream?.write(formattedData.toByteArray())
                outputStream?.flush()

                mainHandler.post {
                    showSuccess("Data sent: $count and $value")
//                    binding.etCountFirst.text
               //     editTextData.text.clear()
                }

            } catch (e: IOException) {
                Log.e(TAG, "Error sending data", e)
                mainHandler.post {
                    showError("Failed to send data: ${e.message}")
                }
            }
        }
    }

    private fun startDataReception() {
//        readExecutor.execute {
//            val buffer = ByteArray(1024)
//            while (isConnected && inputStream != null) {
//                try {
//                    val bytesRead = inputStream?.read(buffer) ?: 0
//                    if (bytesRead > 0) {
//                        val receivedData = String(buffer, 0, bytesRead)
//                        mainHandler.post {
//                            val currentText = receivedDataText.text.toString()
//                            receivedDataText.text = "$currentText\n$receivedData"
//                        }
//                    }
//                } catch (e: IOException) {
//                    Log.e(TAG, "Error reading data", e)
//                    mainHandler.post {
//                        if (isConnected) {
//                            showError("Connection lost")
//                            updateConnectionStatus(false)
//                        }
//                    }
//                    break
//                }
//            }
//        }
    }


    private fun updateConnectionStatus(connected: Boolean, message: String = "") {
        isConnected = connected

        if (connected) {
//            statusIndicator.setBackgroundColor(ContextCompat.getColor(this, android.R.color.holo_green_light))
            statusIndicator.setBackgroundResource(R.drawable.status_indicator_connected)
            statusText.text = if (message.isEmpty()) "Connected" else message
            connectButton.isEnabled = false
            disconnectButton.isEnabled = true
            sendButton.isEnabled = true
            deviceSpinner.isEnabled = false
        } else {
//            statusIndicator.setBackgroundColor(ContextCompat.getColor(this, android.R.color.holo_red_light))
            statusIndicator.setBackgroundResource(R.drawable.status_indicator)
            statusText.text = if (message.isEmpty()) "Disconnected" else message
            connectButton.isEnabled = true
            disconnectButton.isEnabled = false
            sendButton.isEnabled = false
            deviceSpinner.isEnabled = true
        }
    }

    private fun showProgress(show: Boolean) {
        progressBar.visibility = if (show) View.VISIBLE else View.GONE
        connectButton.isEnabled = !show
    }

    private fun showError(message: String) {
        Toast.makeText(this, "Error: $message", Toast.LENGTH_LONG).show()
        Log.e(TAG, message)
    }

    private fun showSuccess(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        Log.i(TAG, message)
    }

    private fun showInfo(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        Log.i(TAG, message)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            REQUEST_ENABLE_BT -> {
                if (resultCode == RESULT_OK) {
                    loadPairedDevices()
                } else {
                    showError("Bluetooth must be enabled to use this app")
                }
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            REQUEST_BLUETOOTH_PERMISSIONS -> {
                if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                    checkBluetoothEnabled()
                } else {
                    showError("Bluetooth permissions are required for this app to function")
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isConnected) {
            disconnectFromDevice()
        }
        readExecutor.shutdownNow()
        executorService.shutdown()
    }
}



