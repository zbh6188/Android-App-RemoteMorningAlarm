package uw.eep523.remotemoringalarm


import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGattCharacteristic
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.*
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat


class BLEService : Service(), BLEControl.Callback  {

    var connected: Boolean = false;
    private var ble: BLEControl? = null

    private var handler: Handler? = null
    private var runner: Runner? = null
    var CheckConnectionStatus = false


    init {
        handler = Handler();
        runner = Runner();
    }


    private var receiver: ResultReceiver? = null
    /**
     * Sends a resultCode and message to the receiver.
     */
    private fun deliverResultToReceiver(resultCode: Int, message: String) {
        val bundle = Bundle().apply { putString(KEY_OK, message) }
        receiver?.send(resultCode, bundle)
    }

    inner class Runner : Runnable {
        var ct: Int = 0;

        override fun run() {
            Log.d("BLE", "Running")
            ++ct;

        }
    }
    override fun onBind(intent: Intent): IBinder {
        TODO("Return the communication channel to the service.")
    }

    override fun onCreate() {
        super.onCreate()
        Log.d("BLE","on create service")
        if (Build.VERSION.SDK_INT >= 26) {
            val service = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channelId = createNotificationChannel()
            val notificationBuilder = NotificationCompat.Builder(this, channelId )
            val notification = notificationBuilder.setOngoing(true)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .setCategory(Notification.CATEGORY_SERVICE)
                .build()
            startForeground(101, notification)
        }
        ble = BLEControl(applicationContext, DEVICE_NAME);
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createNotificationChannel(): String{
        val channelId = "my_service"
        val channelName = "My Background Service"
        val chan = NotificationChannel(channelId,
            channelName, NotificationManager.IMPORTANCE_HIGH)
        chan.lightColor = Color.BLUE
        chan.importance = NotificationManager.IMPORTANCE_NONE
        chan.lockscreenVisibility = Notification.VISIBILITY_PRIVATE
        val service = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        service.createNotificationChannel(chan)
        return channelId
    }


    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent!!.action
        if (action == KEY_CONNECT_BLE)
            connectToArduino();
        else if (action==KEY_SEND_BLE) {
            val text = intent.getStringExtra(KEY)
            ble?.send(text)

            receiver = intent?.getParcelableExtra(KEY_RECEIVER)

        }

        return START_STICKY;
    }


    /**
     * Called when data is received by the UART
     * @param ble: the BLE UART object
     * @param rx: the received characteristic
     */
    override fun onReceive(ble: BLEControl, rx: BluetoothGattCharacteristic) {
        val received = rx.getStringValue(0)
        Log.d(tag,"receive:  $received")
        deliverResultToReceiver(KEY_RESULT_OK,received.toString())

    }



    override fun onDestroy() {
        Log.d(tag, "Destroy Service");
        super.onDestroy()
        handler?.removeCallbacks(runner);
        ble!!.unregisterCallback(this)
        ble!!.disconnect();


    }



    private fun connectToArduino() {
        if (!connected) {
            Log.d(tag,"scanning for devices...")
            ble!!.connectFirstAvailable();
        }
        ble!!.registerCallback(this)
    }



    /**
     * Called when a UART device is discovered (after calling startScan)
     * @param device: the BLE device
     */
    override fun onDeviceFound(device: BluetoothDevice) {
    }

    /**
     * Prints the devices information
     */
    override fun onDeviceInfoAvailable() {
    }

    /**
     * Called when UART device is connected and ready to send/receive data
     * @param ble: the BLE UART object
     */
    override fun onConnected(ble: BLEControl) {
        Log.d(tag,"connected-service !")
        CheckConnectionStatus = true
    }

    /**
     * Called when some error occurred which prevented UART connection from completing
     * @param ble: the BLE UART object
     */
    override fun onConnectFailed(ble: BLEControl) {
    }


    /**
     * Called when the UART device disconnected
     * @param ble: the BLE UART object
     */
    override fun onDisconnected(ble: BLEControl) {
        Log.d(tag,"disconnected")
        CheckConnectionStatus = false
    }




    companion object {


        private val DEVICE_NAME = "0_0_summer_is_comming_0_0"
        private val tag = "BLE"
    }
}
