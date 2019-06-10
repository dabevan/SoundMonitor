package com.example.soundmonitor

import android.Manifest
import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.*
import android.support.v7.app.AppCompatActivity
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.telephony.SmsManager
import android.view.WindowManager
import android.widget.Toast
import kotlinx.android.synthetic.main.activity_main.*
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private var delay :Long = 60000
    private var delayLong :Long = 60000 * 15
    private var delayVeryLong :Long = 60000 * 60
    private var loudEventsCounter = 0
    private var threshold = 20000
    private var output: String? = null
    private var mediaRecorder: MediaRecorder? = null
    private var state: Boolean = false
    private var recordingStopped: Boolean = false
    private var maxSoundLevel: Int? = 0
    private lateinit var mHandler: Handler
    private lateinit var mRunnable:Runnable
    private var n = 0
    private var logFile = File(Environment.getExternalStorageDirectory().absolutePath + "/Dropbox/OnePlus5T/log.txt")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        n=0
        mHandler = Handler()


        button_start_recording.setOnClickListener {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED)
                {
                    val permissions = arrayOf(android.Manifest.permission.RECORD_AUDIO, android.Manifest.permission.WRITE_EXTERNAL_STORAGE, android.Manifest.permission.READ_EXTERNAL_STORAGE)
                    ActivityCompat.requestPermissions(this, permissions,0)
                } else {
                    startRecording()
                    textview_sound_level.text = "Recording Started ($n)"
            }
        }


        button_stop_recording.setOnClickListener{
            stopRecording()
        }


        button_monitor.setOnClickListener {
            textview_sound_level.text = "Monitoring Started"
            mRunnable = Runnable {
                monitor()
                mHandler.postDelayed(mRunnable,delay)
            }
            mHandler.postDelayed(mRunnable,delay)
        }
    }


    private fun startRecording() {
        try {
            n++
            mediaRecorder = MediaRecorder()
            output = getTempFileName()
            mediaRecorder?.setAudioSource(MediaRecorder.AudioSource.MIC)
            mediaRecorder?.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            mediaRecorder?.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            mediaRecorder?.setOutputFile(output)
            mediaRecorder?.prepare()
            mediaRecorder?.start()
            state = true
        } catch (e: IllegalStateException) {
            e.printStackTrace()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    @SuppressLint("RestrictedApi", "SetTextI18n")
    @TargetApi(Build.VERSION_CODES.N)
    private fun monitor() {
        maxSoundLevel = mediaRecorder?.maxAmplitude
        threshold = getThreshold()
        delay = getDelay()
        stopRecording()

        if (maxSoundLevel!! > threshold) {
            loudEventsCounter++
            sendSMS()
            File(output).renameTo(File(getPermFileName()))
        } else {
            File(output).delete()
        }
        textview_sound_level.text = getMultiLineText()
        logFile.appendText(getSingleLineText())
        startRecording()
        mediaRecorder?.maxAmplitude
    }

    fun sendSMS() {
        val smsManager = SmsManager.getDefault() as SmsManager
        smsManager.sendTextMessage("07903681502", null, "Loud noise detected!\n\n" + getMultiLineText(), null, null)
    }

    @SuppressLint("RestrictedApi", "SetTextI18n")
    @TargetApi(Build.VERSION_CODES.N)
    private fun resumeRecording() {
        Toast.makeText(this,"Resume!", Toast.LENGTH_SHORT).show()
        mediaRecorder?.resume()
        recordingStopped = false
    }

    private fun stopRecording(){
        if(state){
            mHandler.removeCallbacks(mRunnable)
            mediaRecorder?.stop()
            mediaRecorder?.release()
            state = false
        }else{
            Toast.makeText(this, "You are not recording right now!", Toast.LENGTH_SHORT).show()
        }

    }

    private fun getMultiLineText() :String {
        val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm:ss")
        val current = sdf.format(Date())
        return "Time:$current\nLevel:$maxSoundLevel\nThreshold:$threshold\nDelay:$delay\nNumber of loud events:$loudEventsCounter\nn:$n"
    }

    private fun getSingleLineText() :String {
        val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm:ss")
        val current = sdf.format(Date())
        return "$current Level:$maxSoundLevel THold:$threshold Delay:$delay #Loud:$loudEventsCounter n:$n\n"
    }


    private fun getDropboxFolder() :String {
        return getRootFolder() + "/Dropbox/OnePlus5T"
    }

    private fun getRootFolder() :String {
        return Environment.getExternalStorageDirectory().absolutePath
    }

    private fun getTempFileName() :String {
        return getRootFolder() + "/recording_$n.mp3"
    }


    private fun getPermFileName() :String {
        val sdf = SimpleDateFormat("MM-dd_HH-mm-ss")
        val current = sdf.format(Date())
        return getDropboxFolder() + "/${current}_($n).mp3"
    }


    private fun getDelay() :Long {
        var fileDelayValue :Long = 0
        File(getDropboxFolder() + "/delay.txt").forEachLine {
            if(it != "0") {
                fileDelayValue = it.toLong()
            }
        }
        if (fileDelayValue > 0) { return fileDelayValue }
        if (loudEventsCounter > 10) { return delayLong }
        if (loudEventsCounter > 20) { return delayVeryLong }
        return delay
    }

    private fun getThreshold() :Int {
        var fileThresholdValue = 0
        File(getDropboxFolder() + "/threshold.txt").forEachLine {
            if(it != "0") {
                fileThresholdValue = it.toInt()
            }
        }
        if (fileThresholdValue > 0) { return fileThresholdValue }
        return 20000
    }
}