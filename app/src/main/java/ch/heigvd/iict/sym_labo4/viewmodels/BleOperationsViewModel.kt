package ch.heigvd.iict.sym_labo4.viewmodels

import android.app.Application
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import no.nordicsemi.android.ble.BleManager
import no.nordicsemi.android.ble.annotation.WriteType
import no.nordicsemi.android.ble.data.Data
import no.nordicsemi.android.ble.observer.ConnectionObserver
import java.util.*

/**
 * Project: Labo4
 * Created by fabien.dutoit on 11.05.2019
 * Updated by fabien.dutoit on 18.10.2021
 * (C) 2019 - HEIG-VD, IICT
 */
class BleOperationsViewModel(application: Application) : AndroidViewModel(application) {

    private var ble = SYMBleManager(application.applicationContext)
    private var mConnection: BluetoothGatt? = null

    //live data - observer
    val isConnected = MutableLiveData(false)
    val temperature = MutableLiveData("N/A")
    val nbClicks = MutableLiveData("N/A")
    val currentTime = MutableLiveData("N/A")

    //Services and Characteristics of the SYM Pixl
    private var timeService: BluetoothGattService? = null
    private var symService: BluetoothGattService? = null
    private var currentTimeChar: BluetoothGattCharacteristic? = null
    private var integerChar: BluetoothGattCharacteristic? = null
    private var temperatureChar: BluetoothGattCharacteristic? = null
    private var buttonClickChar: BluetoothGattCharacteristic? = null

    //UUIDs
    private var TIME_SERVICE_UUID = "00001805-0000-1000-8000-00805f9b34fb"
    private var SYM_SERVICE_UUID = "3c0a1000-281d-4b48-b2a7-f15579a1c38f"
    private var CURRENT_TIME_CHAR = "00002a2b-0000-1000-8000-00805f9b34fb"
    private var INTEGER_CHAR = "3c0a1001-281d-4b48-b2a7-f15579a1c38f"
    private var TEMPERATURE_CHAR = "3c0a1002-281d-4b48-b2a7-f15579a1c38f"
    private var BTN_CLICK_CHAR = "3c0a1003-281d-4b48-b2a7-f15579a1c38f"

    override fun onCleared() {
        super.onCleared()
        Log.d(TAG, "onCleared")
        ble.disconnect()
    }

    fun connect(device: BluetoothDevice) {
        Log.d(TAG, "User request connection to: $device")
        if (!isConnected.value!!) {
            ble.connect(device)
                    .retry(1, 100)
                    .useAutoConnect(false)
                    .enqueue()
        }
    }

    fun disconnect() {
        Log.d(TAG, "User request disconnection")
        ble.disconnect()
        mConnection?.disconnect()
    }

    /* TODO
        vous pouvez placer ici les différentes méthodes permettant à l'utilisateur
        d'interagir avec le périphérique depuis l'activité
     */

    fun readTemperature(): Boolean {
        if (!isConnected.value!! || temperatureChar == null)
            return false
        else
            return ble.readTemperature()
    }

    fun sendValue(value: Int): Boolean {
        if (!isConnected.value!! || integerChar == null)
            return false
        else
            return ble.sendValue(value)
    }

    fun setCurrentTime(time: Calendar): Boolean {
        if (!isConnected.value!! || currentTimeChar == null)
            return false
        else
            return ble.setCurrentTime(time)
    }

    private val bleConnectionObserver: ConnectionObserver = object : ConnectionObserver {
        override fun onDeviceConnecting(device: BluetoothDevice) {
            Log.d(TAG, "onDeviceConnecting")
            isConnected.value = false
        }

        override fun onDeviceConnected(device: BluetoothDevice) {
            Log.d(TAG, "onDeviceConnected")
            isConnected.value = true
        }

        override fun onDeviceDisconnecting(device: BluetoothDevice) {
            Log.d(TAG, "onDeviceDisconnecting")
            isConnected.value = false
        }

        override fun onDeviceReady(device: BluetoothDevice) {
            Log.d(TAG, "onDeviceReady")
        }

        override fun onDeviceFailedToConnect(device: BluetoothDevice, reason: Int) {
            Log.d(TAG, "onDeviceFailedToConnect")
        }

        override fun onDeviceDisconnected(device: BluetoothDevice, reason: Int) {
            if(reason == ConnectionObserver.REASON_NOT_SUPPORTED) {
                Log.d(TAG, "onDeviceDisconnected - not supported")
                Toast.makeText(getApplication(), "Device not supported - implement method isRequiredServiceSupported()", Toast.LENGTH_LONG).show()
            }
            else
                Log.d(TAG, "onDeviceDisconnected")
            isConnected.value = false
        }

    }

    private inner class SYMBleManager(applicationContext: Context) : BleManager(applicationContext) {
        /**
         * BluetoothGatt callbacks object.
         */
        private var mGattCallback: BleManagerGattCallback? = null

        public override fun getGattCallback(): BleManagerGattCallback {
            //we initiate the mGattCallback on first call, singleton
            if (mGattCallback == null) {
                mGattCallback = object : BleManagerGattCallback() {

                    public override fun isRequiredServiceSupported(gatt: BluetoothGatt): Boolean {
                        mConnection = gatt //trick to force disconnection

                        gatt.services.forEach {
                            val serviceFound = when (it.uuid.toString()) {
                                TIME_SERVICE_UUID -> {
                                    timeService = it
                                    true
                                }
                                SYM_SERVICE_UUID -> {
                                    symService = it
                                    true
                                }
                                else -> false
                            }
                            if (serviceFound) {
                                it.characteristics.forEach { jt ->
                                    when (jt.uuid.toString()) {
                                        CURRENT_TIME_CHAR -> currentTimeChar = jt
                                        INTEGER_CHAR -> integerChar = jt
                                        TEMPERATURE_CHAR -> temperatureChar = jt
                                        BTN_CLICK_CHAR -> buttonClickChar = jt
                                    }
                                }
                            }
                        }

                        return timeService != null &&
                                symService != null &&
                                currentTimeChar != null &&
                                integerChar != null &&
                                temperatureChar != null &&
                                buttonClickChar != null
                    }

                    override fun initialize() {
                        /*  TODO
                            Ici nous somme sûr que le périphérique possède bien tous les services et caractéristiques
                            attendus et que nous y sommes connectés. Nous pouvous effectuer les premiers échanges BLE:
                            Dans notre cas il s'agit de s'enregistrer pour recevoir les notifications proposées par certaines
                            caractéristiques, on en profitera aussi pour mettre en place les callbacks correspondants.
                         */
                        setNotificationCallback(buttonClickChar).with { _, data ->
                            nbClicks.postValue(data.getIntValue(Data.FORMAT_UINT8, 0).toString())
                        }
                        enableNotifications(buttonClickChar).enqueue()

                        setNotificationCallback(currentTimeChar).with { _, data ->
                            val year = data.getIntValue(Data.FORMAT_UINT16, 0)
                            val month = data.getIntValue(Data.FORMAT_UINT8, 2)
                            val day = data.getIntValue(Data.FORMAT_UINT8, 3)
                            val hour = data.getIntValue(Data.FORMAT_UINT8, 4)
                            val minute = data.getIntValue(Data.FORMAT_UINT8, 5)
                            val second = data.getIntValue(Data.FORMAT_UINT8, 6)

                            currentTime.postValue("$year-$month-$day $hour:$minute:$second")
                        }
                        enableNotifications(currentTimeChar).enqueue()
                    }

                    override fun onServicesInvalidated() {
                        //we reset services and characteristics
                        timeService = null
                        currentTimeChar = null
                        symService = null
                        integerChar = null
                        temperatureChar = null
                        buttonClickChar = null
                    }
                }
            }
            return mGattCallback!!
        }

        fun readTemperature(): Boolean {
            /*  TODO
                on peut effectuer ici la lecture de la caractéristique température
                la valeur récupérée sera envoyée à l'activité en utilisant le mécanisme
                des MutableLiveData
                On placera des méthodes similaires pour les autres opérations
            */
            if (temperatureChar === null) {
                return false
            }
            readCharacteristic(temperatureChar).with { _, data ->
                temperature.postValue(data.getIntValue(Data.FORMAT_UINT16, 0)?.div(10).toString())
            }.enqueue()
            return true
        }

        fun sendValue(value: Int): Boolean {
            if (integerChar === null) {
                return false
            }

            integerChar!!.setValue(value, Data.FORMAT_UINT32, 0)
            writeCharacteristic(integerChar, integerChar!!.value, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT).enqueue()
            return true
        }

        fun setCurrentTime(time: Calendar): Boolean {
            if (currentTimeChar == null) {
                return false
            }

            currentTimeChar!!.setValue(time.get(Calendar.YEAR), Data.FORMAT_UINT16, 0)
            currentTimeChar!!.setValue(time.get(Calendar.MONTH), Data.FORMAT_UINT8, 2)
            currentTimeChar!!.setValue(time.get(Calendar.DAY_OF_MONTH) + 1, Data.FORMAT_UINT8, 3)
            currentTimeChar!!.setValue(time.get(Calendar.HOUR_OF_DAY), Data.FORMAT_UINT8, 4)
            currentTimeChar!!.setValue(time.get(Calendar.MINUTE), Data.FORMAT_UINT8, 5)
            currentTimeChar!!.setValue(time.get(Calendar.SECOND), Data.FORMAT_UINT8, 6)
            writeCharacteristic(currentTimeChar, currentTimeChar!!.value, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT).enqueue()
            return true
        }
    }

    companion object {
        private val TAG = BleOperationsViewModel::class.java.simpleName
    }

    init {
        ble.setConnectionObserver(bleConnectionObserver)
    }

}