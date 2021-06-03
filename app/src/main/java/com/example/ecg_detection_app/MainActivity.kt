package com.example.ecg_detection_app

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.example.ecg_detection_app.BluetoothLeService.LocalBinder
import kotlinx.android.synthetic.main.activity_main.*


interface ItemClick {
    fun OnItemClick(v: View,position:Int)
}

class MyRecycleViewAdapter(dataset: ArrayList<BluetoothDevice>, context: Context, itemClick: ItemClick) : RecyclerView.Adapter<MyRecycleViewAdapter.ViewHolder>() {
    var mDataset = dataset
    var item = itemClick
    val contxt = context

    inner class ViewHolder(listItemView: View) : RecyclerView.ViewHolder(listItemView) {
        var bleDeviceItem = itemView.findViewById<TextView>(R.id.textView2)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val view = inflater.inflate(R.layout.ble_device_item, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val string = mDataset[position].name + mDataset[position].address
        holder.bleDeviceItem.setText(string)
        holder.bleDeviceItem.setTag(position)
        holder.bleDeviceItem.setOnClickListener(onClick())
    }

    override fun getItemCount(): Int {
        return mDataset.size
    }

    // click callback function
    inner class onClick:View.OnClickListener{
        override fun onClick(v: View?) {
            val view = v
            var positon:Int= view!!.getTag() as Int
            item!!.OnItemClick(view, positon)
        }

    }
}


class MainActivity : AppCompatActivity() {
    // constant for bluetooth service
    val REQUEST_ENABLE_BT:Int = 12

    var mSwipeRefreshLayout:SwipeRefreshLayout? = null
    var mRecyclerView:RecyclerView? = null

    // store the scanned ble devices
    var deviceList: ArrayList<BluetoothDevice> = ArrayList<BluetoothDevice>()


    // Initializes Bluetooth adapter.
    var bluetoothManager: BluetoothManager? = null
    var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothLeScanner: BluetoothLeScanner? = null
    private var scanning = false
    private var isEnableScanning = false
    // Stops scanning after 10 seconds.
    private val SCAN_PERIOD: Long = 1000

    // Bluetooth connection service
    var mBluetoothLeService:BluetoothLeService? = null
    var mServiceConnection:ServiceConnection? = null

    // ble device item onClick function
    inner class itemClick: ItemClick{
        override fun OnItemClick(v: View, position: Int) {

            val selectedDevice = deviceList[position]

            mServiceConnection = object : ServiceConnection {
                override fun onServiceConnected(componentName: ComponentName, service: IBinder) {
                    mBluetoothLeService = (service as LocalBinder).service
                    if (!mBluetoothLeService!!.initialize()) {
                        finish()
                    }
                    // Automatically connects to the device upon successful start-up initialization.
                    mBluetoothLeService!!.connect(selectedDevice.address)
                }

                override fun onServiceDisconnected(componentName: ComponentName) {
                    mBluetoothLeService!!.disconnect()
                }
            }

            val gattServiceIntent = Intent(this@MainActivity, BluetoothLeService::class.java)
            bindService(gattServiceIntent, mServiceConnection!!, BIND_AUTO_CREATE)

            Toast.makeText(this@MainActivity, "連線中", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // SwipeRefreshLayout set on listener
        mSwipeRefreshLayout =
            super.findViewById<View>(R.id.mian_swipeRefreshLayout) as SwipeRefreshLayout
        mSwipeRefreshLayout!!.setOnRefreshListener {
            isEnableScanning = true
            deviceList.clear()
            mRecyclerView!!.adapter?.notifyDataSetChanged()
            Handler().postDelayed(Runnable {
                mSwipeRefreshLayout!!.isRefreshing = false
            }, 4000)
        }

        // RecyclerView initialize
        mRecyclerView = super.findViewById<View>(R.id.main_recylerlist) as RecyclerView
        mRecyclerView!!.adapter = MyRecycleViewAdapter(deviceList, this, itemClick())
        mRecyclerView!!.layoutManager = LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false)
        mRecyclerView!!.addItemDecoration(DividerItemDecoration(this, DividerItemDecoration.VERTICAL))

        // bluetooth initialize
        bluetoothManager = getSystemService(BluetoothManager::class.java)
        bluetoothAdapter = bluetoothManager?.adapter
        bluetoothLeScanner = bluetoothAdapter?.bluetoothLeScanner

        // Ensures Bluetooth is available on the device and it is enabled. If not,
        // displays a dialog requesting user permission to enable Bluetooth.
        if (bluetoothAdapter != null && !bluetoothAdapter!!.isEnabled) {
            Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT)
        }

        // permission
        val permissionCheck =
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
        if (permissionCheck != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this@MainActivity, arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION),
                REQUEST_ENABLE_BT
            )
        }

        // start scanning bluetooth device
        isEnableScanning = true
        val doTask: Runnable = object : Runnable {
            override fun run() {
                scanLeDevice()
                Handler(Looper.getMainLooper()).postDelayed(this, 1000)
            }
        }
        Handler(Looper.getMainLooper()).post(doTask)
    }

    override fun onDestroy() {
        super.onDestroy()
        if(mBluetoothLeService == null) {
            return
        }
        mBluetoothLeService!!.disconnect()
        mServiceConnection?.let { unbindService(it) }
        mBluetoothLeService = null
        isEnableScanning = false
    }



    // Device scan callback.
    private val leScanCallback: ScanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            super.onScanResult(callbackType, result)

            val deviceName = result.device.name
            val deviceAddress = result.device.address
            if(deviceName != null && deviceName.isNotEmpty()) {
                if(!deviceList.contains(result.device)) {
                    deviceList.add(result.device)
                    mRecyclerView!!.adapter?.notifyItemInserted(deviceList.size-1)
                }
            }

        }
    }

    private fun scanLeDevice() {
        if(isEnableScanning) {
            bluetoothLeScanner?.let { scanner ->
                if (!scanning) { // Stops scanning after a pre-defined scan period.
                    Handler(Looper.getMainLooper()).postDelayed({
                        scanning = false
                        scanner.stopScan(leScanCallback)
                    }, SCAN_PERIOD)
                    scanning = true
                    scanner.startScan(leScanCallback)
                } else {
                    scanning = false
                    scanner.stopScan(leScanCallback)
                }
            }
        } else {
            scanning = false
            bluetoothLeScanner?.stopScan(leScanCallback)
        }

    }
}
