package com.example.ecg_detection_app

import android.app.Service
import android.bluetooth.*
import android.bluetooth.BluetoothAdapter.*
import android.bluetooth.BluetoothGatt.GATT_SUCCESS
import android.content.ContentValues.TAG
import android.content.Context
import android.content.Intent
import android.nfc.Tag
import android.os.Binder
import android.os.IBinder
import android.util.Log

class BluetoothLeService : Service() {

    private var mBluetoothManager: BluetoothManager? = null
    private var mBluetoothAdapter: BluetoothAdapter? = null
    private var mBluetoothDeviceAddress: String? = null
    private var mBluetoothGatt: BluetoothGatt? = null
    private var mConnectionState = STATE_DISCONNECTED

    private val mBinder = LocalBinder()

    inner class LocalBinder : Binder() {
        val service: BluetoothLeService
            get() = this@BluetoothLeService
    }

    override fun onBind(intent: Intent): IBinder {
        return mBinder
    }

    override fun onUnbind(intent: Intent?): Boolean {
        close()
        return super.onUnbind(intent)
    }

    private val mGattCallback: BluetoothGattCallback = object : BluetoothGattCallback() {

        /** 連線狀態發生改變 */
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            super.onConnectionStateChange(gatt, status, newState)

            if(newState == STATE_CONNECTED) {
                mConnectionState = STATE_CONNECTED;
                Log.d(TAG, "已連接至GATT服務");
                Log.d(TAG, "開始嘗試搜尋服務: "+mBluetoothGatt!!.discoverServices());
            } else if(newState == STATE_DISCONNECTED) {
                mConnectionState = STATE_DISCONNECTED;
                Log.d(TAG, "已斷開服務");
            }
        }

        /**當發現新的服務器*/
        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            super.onServicesDiscovered(gatt, status)

            if(status == GATT_SUCCESS) {
                val gattService = gatt?.getService(XenonHelper.UUID_XENON_SERVICE)
                if(gattService != null) {
                    setDeviceValueDataNotification(true)
                }
                Log.d(TAG, "發現服務");
            }
        }

        /**讀資料(只有從藍芽回傳的資料才會被顯示)*/
        override fun onCharacteristicRead(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?,
            status: Int
        ) {
            super.onCharacteristicRead(gatt, characteristic, status)
            val dataArray = characteristic?.value
            Log.d(TAG, "dataArray = " + dataArray.toString())
        }

        /**廣播更新(只有從藍芽回傳的資料才會被顯示)*/
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?
        ) {
            super.onCharacteristicChanged(gatt, characteristic)
            val dataArray = characteristic?.value
            Log.d(TAG, "dataArray = " + dataArray.toString())
        }
    }

    fun setDeviceValueDataNotification(enable:Boolean) {
        var gattChs = ArrayList<BluetoothGattCharacteristic>()
        val gattService = mBluetoothGatt!!.getService(XenonHelper.UUID_XENON_SERVICE)
        gattChs.add(gattService.getCharacteristic(XenonHelper.UUID_XENON_DATA))

        if(gattChs.size > 0) {
            for(gattCh in gattChs) {
                val charaProp = gattCh.properties
                //
                if((charaProp and BluetoothGattCharacteristic.PROPERTY_NOTIFY) > 0) {
                    mBluetoothGatt!!.setCharacteristicNotification(gattCh, enable)
                }
                val dps = gattCh.descriptors
                for(dp in dps) {
                    if(dp != null) {
                        if((charaProp and BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0) {
                            dp.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                        } else if((charaProp and BluetoothGattCharacteristic.PROPERTY_INDICATE) !=0) {
                            dp.setValue(BluetoothGattDescriptor.ENABLE_INDICATION_VALUE)
                        }
                        mBluetoothGatt!!.writeDescriptor(dp);
                    }
                }

            }
        }
    }

    fun initialize(): Boolean {
        // For API level 18 and above, get a reference to BluetoothAdapter through
        // BluetoothManager.
        if (mBluetoothManager == null) {
            mBluetoothManager = getSystemService(BluetoothManager::class.java)
            if (mBluetoothManager == null) {
                Log.e(TAG, "Unable to initialize BluetoothManager.")
                return false
            }
        }
        mBluetoothAdapter = mBluetoothManager!!.adapter
        if (mBluetoothAdapter == null) {
            Log.e(TAG, "Unable to obtain a BluetoothAdapter.")
            return false
        }
        return true
    }

    fun connect(address: String?): Boolean {
        if (mBluetoothAdapter == null || address == null) {
            Log.w(TAG, "BluetoothAdapter not initialized or unspecified address.")
            return false
        }

        // Previously connected device.  Try to reconnect.
        if (mBluetoothDeviceAddress != null && address == mBluetoothDeviceAddress && mBluetoothGatt != null) {
            Log.d(TAG, "Trying to use an existing mBluetoothGatt for connection.")
            return if (mBluetoothGatt!!.connect()) {
                mConnectionState = STATE_CONNECTING
                true
            } else {
                false
            }
        }
        val device = mBluetoothAdapter!!.getRemoteDevice(address)
        if (device == null) {
            Log.w(TAG, "Device not found.  Unable to connect.")
            return false
        }
        // We want to directly connect to the device, so we are setting the autoConnect
        // parameter to false.
        mBluetoothGatt = device.connectGatt(this, false, mGattCallback)
        Log.d(TAG, "Trying to create a new connection.")
        mBluetoothDeviceAddress = address
        mConnectionState = STATE_CONNECTING
        return true
    }

    fun disconnect() {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized")
            return
        }
        mBluetoothGatt!!.disconnect()
    }

    fun close() {
        if (mBluetoothGatt == null) {
            return
        }
        mBluetoothGatt!!.close()
        mBluetoothGatt = null
    }
}