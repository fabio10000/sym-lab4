package ch.heigvd.iict.sym_labo4

import ch.heigvd.iict.sym_labo4.abstractactivies.BaseTemplateActivity
import android.bluetooth.BluetoothAdapter
import ch.heigvd.iict.sym_labo4.viewmodels.BleOperationsViewModel
import ch.heigvd.iict.sym_labo4.adapters.ResultsAdapter
import android.os.Bundle
import android.bluetooth.BluetoothManager
import android.bluetooth.le.*
import androidx.lifecycle.ViewModelProvider
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.*
import java.util.*

/**
 * Project: Labo4
 * Created by fabien.dutoit on 11.05.2019
 * Updated by fabien.dutoit on 06.11.2020
 * (C) 2019 - HEIG-VD, IICT
 */
class BleActivity : BaseTemplateActivity() {
    //system services
    private lateinit var bluetoothAdapter: BluetoothAdapter

    //view model
    private lateinit var bleViewModel: BleOperationsViewModel

    //gui elements
    private lateinit var operationPanel: View
    private lateinit var scanPanel: View
    private lateinit var scanResults: ListView
    private lateinit var emptyScanResults: TextView

    // gui connected elements
    private lateinit var textClickCounter: TextView
    private lateinit var textTemperature: TextView
    private lateinit var btnTemperature: Button
    private lateinit var inputValue: EditText
    private lateinit var btnSendValue: Button
    private lateinit var textCurrentTime: TextView
    private lateinit var btnCurrentTime: Button

    //menu elements
    private var scanMenuBtn: MenuItem? = null
    private var disconnectMenuBtn: MenuItem? = null

    //adapters
    private lateinit var scanResultsAdapter: ResultsAdapter

    //states
    private var handler = Handler(Looper.getMainLooper())

    private var isScanning = false
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ble)

        //enable and start bluetooth - initialize bluetooth adapter
        val bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter

        //link GUI
        operationPanel = findViewById(R.id.ble_operation)
        scanPanel = findViewById(R.id.ble_scan)
        scanResults = findViewById(R.id.ble_scanresults)
        emptyScanResults = findViewById(R.id.ble_scanresults_empty)
        textClickCounter = findViewById(R.id.textClickCounter)
        textTemperature = findViewById(R.id.textTemperature)
        btnTemperature = findViewById(R.id.btnTemperature)
        inputValue = findViewById(R.id.inputValue)
        btnSendValue = findViewById(R.id.btnSendValue)
        textCurrentTime = findViewById(R.id.textCurrentTime)
        btnCurrentTime = findViewById(R.id.btnCurrentTime)

        //manage scanned item
        scanResultsAdapter = ResultsAdapter(this)
        scanResults.adapter = scanResultsAdapter
        scanResults.emptyView = emptyScanResults

        //connect to view model
        bleViewModel = ViewModelProvider(this).get(BleOperationsViewModel::class.java)

        updateGui()

        //events
        scanResults.setOnItemClickListener { _: AdapterView<*>?, _: View?, position: Int, _: Long ->
            runOnUiThread {
                //we stop scanning
                scanLeDevice(false)
                //we connect
                bleViewModel.connect(scanResultsAdapter.getItem(position).device)
            }
        }

        btnTemperature.setOnClickListener {
            bleViewModel.readTemperature()
        }

        btnSendValue.setOnClickListener {
            bleViewModel.sendValue(inputValue.text.toString().toInt())
        }

        btnCurrentTime.setOnClickListener {
            bleViewModel.setCurrentTime(Calendar.getInstance())
        }

        //ble events
        bleViewModel.isConnected.observe(this, { updateGui() })
        bleViewModel.nbClicks.observe(this, { updateGui() })
        bleViewModel.temperature.observe(this, { updateGui() })
        bleViewModel.currentTime.observe(this, { updateGui() })
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.ble_menu, menu)
        //we link the two menu items
        scanMenuBtn = menu.findItem(R.id.menu_ble_search)
        disconnectMenuBtn = menu.findItem(R.id.menu_ble_disconnect)
        //we update the gui
        updateGui()
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val id = item.itemId
        if (id == R.id.menu_ble_search) {
            if (isScanning) scanLeDevice(false) else scanLeDevice(true)
            return true
        } else if (id == R.id.menu_ble_disconnect) {
            bleViewModel.disconnect()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onPause() {
        super.onPause()
        if (isScanning) scanLeDevice(false)
        if (isFinishing) bleViewModel.disconnect()
    }

    /*
     * Method used to update the GUI according to BLE status:
     * - connected: display operation panel (BLE control panel)
     * - not connected: display scan result list
     */
    private fun updateGui() {
        val isConnected = bleViewModel.isConnected.value
        if (isConnected != null && isConnected) {

            scanPanel.visibility = View.GONE
            operationPanel.visibility = View.VISIBLE

            textClickCounter.text = bleViewModel.nbClicks.value.toString()
            textTemperature.text = bleViewModel.temperature.value.toString()
            textCurrentTime.text = bleViewModel.currentTime.value.toString()

            if (scanMenuBtn != null && disconnectMenuBtn != null) {
                scanMenuBtn!!.isVisible = false
                disconnectMenuBtn!!.isVisible = true
            }
        } else {
            operationPanel.visibility = View.GONE
            scanPanel.visibility = View.VISIBLE

            if (scanMenuBtn != null && disconnectMenuBtn != null) {
                disconnectMenuBtn!!.isVisible = false
                scanMenuBtn!!.isVisible = true
            }
        }
    }

    //this method needs user grant localisation and/or bluetooth permissions, our demo app is requesting them on MainActivity
    private fun scanLeDevice(enable: Boolean) {
        val bluetoothScanner = bluetoothAdapter.bluetoothLeScanner

        if (enable) {
            //config
            val builderScanSettings = ScanSettings.Builder()
            builderScanSettings.setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            builderScanSettings.setReportDelay(0)

            //we scan for any BLE device
            //we don't filter them based on advertised services...
            // le service "SYM" (UUID: "3c0a1000-281d-4b48-b2a7-f15579a1c38f")
            val filters: List<ScanFilter> = listOf(ScanFilter.Builder().setServiceUuid(ParcelUuid.fromString("3c0a1000-281d-4b48-b2a7-f15579a1c38f")).build())

            //reset display
            scanResultsAdapter.clear()
            bluetoothScanner.startScan(filters, builderScanSettings.build(), leScanCallback)
            Log.d(TAG, "Start scanning...")
            isScanning = true

            //we scan only for 15 seconds
            handler.postDelayed({ scanLeDevice(false) }, 15 * 1000L)
        } else {
            bluetoothScanner.stopScan(leScanCallback)
            isScanning = false
            Log.d(TAG, "Stop scanning (manual)")
        }
    }

    // Device scan callback.
    private val leScanCallback: ScanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            super.onScanResult(callbackType, result)
            runOnUiThread { scanResultsAdapter.addDevice(result) }
        }
    }

    companion object {
        private val TAG = BleActivity::class.java.simpleName
    }
}