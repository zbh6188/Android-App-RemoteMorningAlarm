package uw.eep523.remotemoringalarm

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.icu.util.Calendar

import android.os.Build
import android.os.Handler
import android.os.ResultReceiver
import android.preference.PreferenceManager
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.TimePicker
import android.widget.Toast
import androidx.core.app.ActivityCompat
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.select_tone.*
import kotlinx.android.synthetic.main.select_tone.view.*

const val DEVICE_NAME = "Maserati"

const val KEY_RECEIVER = "KEY-RECEIVER"
const val KEY_SEND_BLE = "KEY-SEND-BLE"
const val KEY_CONNECT_BLE = "KEY-CONNECT-BLE"
const val KEY = "KEY"
const val KEY_OK = "KEY-OK"
const val KEY_RESULT_OK = 0


class MainActivity : AppCompatActivity() {

    private var runningService: Boolean = false
    // notification
    lateinit var notificationManager : NotificationManager
    lateinit var notificationChannel : NotificationChannel
    lateinit var builder : Notification.Builder
    private val channelId = "i.apps.notifications"
    private val description = "Test notification"

    // Bluetooth
    private var ble: BLEControl? = null
    var interval = 0
    var selectedTone = 1;

    // saved variables
    var savedHourSet:Int = 0
    var savedMinuteSet:Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        Log.d("BLE","start")

        resultReceiver = BLEResultReceiver(Handler())

        // Ensures Bluetooth is available on the device and it is enabled. If not,
        // displays a dialog requesting user permission to enable Bluetooth.

        val adapter: BluetoothAdapter?
        adapter = BluetoothAdapter.getDefaultAdapter()
        if (adapter != null) {
            if (!adapter.isEnabled) {
                val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT)

            }        }

        // Get Bluetooth
        ble = BLEControl(applicationContext, DEVICE_NAME)

        // permission
        ActivityCompat.requestPermissions(this,
            arrayOf( Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.FOREGROUND_SERVICE), 1)

        // notification
        notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationChannel = NotificationChannel(
                channelId,description,NotificationManager.IMPORTANCE_HIGH)
            notificationChannel.enableLights(true)
            notificationChannel.lightColor = Color.GREEN
            notificationChannel.enableVibration(false)
            notificationManager.createNotificationChannel(notificationChannel)

            builder = Notification.Builder(this,channelId)
                .setSmallIcon(R.drawable.ic_stat_name)
                .setContentTitle("Good Morning!")
                .setContentText("Data changed. Tap here to see result")

                .setAutoCancel(true)
                .setColor(Color.GREEN)
        }else{

            builder = Notification.Builder(this)
                .setSmallIcon(R.drawable.ic_stat_name)

                .setContentTitle("Good Morning!")
                .setContentText("Data changed. Tap here to see result")

                .setAutoCancel(true)
                .setColor(Color.GREEN)
        }

        // restore saved variable
        val now = Calendar.getInstance()
        val nowHour : Int = now.get(Calendar.HOUR_OF_DAY)
        val nowMin : Int = now.get(Calendar.MINUTE)

        savedHourSet = nowHour
        savedMinuteSet = nowMin

        val prefs = getSharedPreferences("myprefs", Context.MODE_PRIVATE)
        savedHourSet = prefs.getInt("HourSet", nowHour)
        savedMinuteSet = prefs.getInt("MinuteSet", nowMin)
        selectedTone = prefs.getInt("Tone",1)
        interval = prefs.getInt("Interval",0)
        var tonename:String = when(selectedTone){
            1 -> "Brett Hagman"
            2 -> "Christmas theme"
            3 -> "Mario theme"
            else -> "Brett Hagman"
        }
        textView2.text= "The selected tone now is: " + tonename
        ringtime.text = prefs.getString("RingtimeText","")

        updateConnectionStatus(view = this)
        OnClickTime()

    }


    // Update UI
    fun updateConnectionStatus(view: MainActivity){
        var c = runningService

        Log.d("BEL",c.toString())
        if(c){
            textView4.text = "Connected"
            button6.isChecked = true
        }
        else if(!c){
            textView4.text = "Not Connected"
            button6.isChecked = false
        }
        else{}

    }

    private fun startService() {
        runningService = true;


        var intent: Intent  = Intent(this, BLEService::class.java);
        intent.putExtra(KEY,resultReceiver)
        intent.putExtra(KEY_RECEIVER,resultReceiver)
        intent.action=KEY_CONNECT_BLE

        startService(intent); // create background service
        //foreground services only added in API level 26
        if (Build.VERSION.SDK_INT >= 26) {
            startForegroundService(intent); // create foreground service
        } else {
            startService(intent)}

        updateConnectionStatus(this)


    }

    private fun stopService() {
        runningService = false;

        var intent: Intent  = Intent(this, BLEService::class.java);
        stopService(intent);
        updateConnectionStatus(this)
    }

    fun connect(v: View) {
        Log.d("BLE","connect")
        if (!runningService) {
            startService();

        } else {
            stopService();
        }
    }


    private fun OnClickTime() {
        var setHour: Int
        var setMin: Int
        val timePicker = findViewById<TimePicker>(R.id.timepicker)
        timepicker.hour = savedHourSet
        timepicker.minute = savedMinuteSet
        timePicker.setOnTimeChangedListener { _, hour, minute ->
            setHour = hour;
            setMin  = minute

            val now = Calendar.getInstance()
            val nowHour : Int = now.get(Calendar.HOUR_OF_DAY)
            val nowMin : Int = now.get(Calendar.MINUTE)
            val msg1 = "Time is: $nowHour : $nowMin"

            if (nowHour < setHour) {
                interval = (setHour * 60 + setMin) - (nowHour * 60 + nowMin)
                ringtime.text = "This Alarm will ring today"
            } else if (nowHour > setHour) {
                interval = ((setHour + 24) * 60 + setMin) - (nowHour * 60 + nowMin)
                ringtime.text = "This Alarm will ring tomorrow"
            } else {
                if (nowMin < setMin) {
                    interval = setMin - nowMin
                    ringtime.text = "This Alarm will ring today"

                }
                if (nowMin > setMin) {
                    interval = 24 * 60 - (nowMin - setMin)
                    ringtime.text = "This Alarm will ring tomorrow"
                }
                if (nowMin == setMin) {
                    interval = 24*60;

                    ringtime.text = "This Alarm will ring tomorrow"
                }

            }

            val prefs = getSharedPreferences("myprefs", Context.MODE_PRIVATE)
            val prefsEditor = prefs.edit()
            prefsEditor.putInt("HourSet", setHour)
            prefsEditor.putInt("MinuteSet", setMin)
            prefsEditor.putInt("Interval", interval)
            prefsEditor.putString("RingtimeText", ringtime.text.toString())
            prefsEditor.commit()
        }

        updateConnectionStatus(this)

    }

    fun startAlarm (v:View) {
        if(interval == 0){
            button3.isChecked = false
            Toast.makeText(applicationContext,"You havn't set up the time, please choose alarm time!", Toast.LENGTH_SHORT).show()
        }

        else {
            if (button3.isChecked) { // start alarm

                val serviceIntent = Intent(this, BLEService::class.java)
                serviceIntent.action = KEY_SEND_BLE
                serviceIntent.putExtra(KEY_RECEIVER, resultReceiver)
                serviceIntent.putExtra(KEY, selectedTone.toString() + interval.toString())
                startService(serviceIntent)
                Log.d("BLE", "send start")
            }

            else{ // cancel alarm
                val serviceIntent = Intent(this, BLEService::class.java)
                serviceIntent.action = KEY_SEND_BLE
                serviceIntent.putExtra(KEY_RECEIVER, resultReceiver)
                serviceIntent.putExtra(KEY, "s")
                startService(serviceIntent)
                Log.d("BLE", "send stop")
            }

        }
    }

    @SuppressLint("SetTextI18n")
    fun changeTone (v:View) {

        val mDialogView = LayoutInflater.from(this).inflate(R.layout.select_tone, null)
        val mBuilder = AlertDialog.Builder(this)
            .setView(mDialogView)
            .setTitle("Choose Tone")
            .setIcon(R.drawable.ic_action_note)
        val mAlertDialog = mBuilder.show()
        mDialogView.button.setOnClickListener {
            var tonename:String = when(selectedTone){
                1 -> "Brett Hagman"
                2 -> "Christmas theme"
                3 -> "Mario theme"
                else -> "Brett Hagman"
            }
            textView2.text = "The selected tone now is: " + tonename
            mAlertDialog.dismiss()
        }
        mDialogView.Tone1.setOnClickListener{
            selectedTone = 1
            val prefs = getSharedPreferences("myprefs", Context.MODE_PRIVATE)
            val prefsEditor = prefs.edit()
            prefsEditor.putInt("Tone", selectedTone)
            prefsEditor.commit()
        }
        mDialogView.Tone2.setOnClickListener{
            selectedTone = 2
            val prefs = getSharedPreferences("myprefs", Context.MODE_PRIVATE)
            val prefsEditor = prefs.edit()
            prefsEditor.putInt("Tone", selectedTone)
            prefsEditor.commit()
        }
        mDialogView.Tone3.setOnClickListener{
            selectedTone = 3
            val prefs = getSharedPreferences("myprefs", Context.MODE_PRIVATE)
            val prefsEditor = prefs.edit()
            prefsEditor.putInt("Tone", selectedTone)
            prefsEditor.commit()
        }


    }


    private lateinit var resultReceiver: BLEResultReceiver

    private inner class BLEResultReceiver internal constructor(
        handler: Handler
    ) : ResultReceiver(handler) {
        /**
         * Receives data sent from BLEService and updates the UI in MainActivity.
         */
        override fun onReceiveResult(resultCode: Int, resultData: Bundle) {
            Log.d("BLE","received result from broadcast sercie!!")
            // Display the address string or an error message sent from the intent service.
            var temp = resultData.getString(KEY_OK)

            if (resultCode == KEY_RESULT_OK) {

                button3.isChecked = false // toggle switch to off

                interval = 0; // reset interval
                val now = Calendar.getInstance()
                val nowHour : Int = now.get(Calendar.HOUR_OF_DAY)
                if(nowHour<12){
                    builder.setContentTitle("Good Morning!")}
                else{
                    builder.setContentTitle("Good Afternoon!")
                }
                builder.setContentText("The temperature now is: $temp degree Celsius")
                notificationManager.notify(1234,builder.build())
            }

        }
    }

    companion object {
        private val REQUEST_ENABLE_BT = 0

    }

}
