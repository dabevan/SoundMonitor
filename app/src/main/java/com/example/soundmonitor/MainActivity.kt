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

    private var delay :Long = 6000
    private var loudEventsCounter = 0
    private var threshold = 9000
    private var cycleCounter = 0
    private var numberOfLoudCyclesToTriggerNotifiction = 8
    private var numberOfLoudCyclesToTriggerKeepFile = 1
    private var maxSoundLevels = arrayOf(0,0,0,0,0,0,0,0,0,0)
    private var output: String? = null
    private var mediaRecorder: MediaRecorder? = null
    private var state: Boolean = false
    private lateinit var mHandler: Handler
    private lateinit var mRunnable:Runnable
    private var n = 0
    private var logFile = File(getDropboxFolder() + "/log.txt")

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
        maxSoundLevels[cycleCounter++] = mediaRecorder!!.maxAmplitude
        textview_sound_level.text = getMultiLineText()

        if(cycleCounter == maxSoundLevels.size) {
            cycleCounter = 0
            if (checkSendSMS()) { sendSMS() }
            if (checkKeepFile()) {
                File(output).renameTo(File(getPermFileName()))
            } else {
                File(output).delete()
            }
            log(getLogText())
            threshold = getThreshold()
            delay = getDelay()
            numberOfLoudCyclesToTriggerNotifiction = getNumberOfLoudCyclesToTriggerNotifiction()
            numberOfLoudCyclesToTriggerKeepFile = getNumberOfLoudCyclesToTriggerKeepFile()
            stopRecording()
            startRecording()
            mediaRecorder?.maxAmplitude
            maxSoundLevels = arrayOf(0,0,0,0,0,0,0,0,0,0)
        }
    }

    fun log(logText :String) {
        val sdf = SimpleDateFormat("MM-dd_HH-mm-ss")
        val current = sdf.format(Date())
        if(n > 360) {
            logFile = File(getDropboxFolder() + "/log_$current.txt")
            n=0
        }
        logFile.appendText(getLogText())
    }

    fun checkKeepFile(): Boolean {
        var thresholdExceededCounter = 0
        maxSoundLevels.forEach { level -> if (level > threshold) {thresholdExceededCounter++ } }
        if (thresholdExceededCounter >= numberOfLoudCyclesToTriggerKeepFile) { return true }
        return false
    }


    fun checkSendSMS(): Boolean {
        var thresholdExceededCounter = 0
        maxSoundLevels.forEach { level -> if (level > threshold) {
            thresholdExceededCounter++
        } }
        if (thresholdExceededCounter >= numberOfLoudCyclesToTriggerNotifiction) {
            loudEventsCounter++
            return true
        }
        return false
    }

    fun sendSMS() {
        val smsManager = SmsManager.getDefault() as SmsManager
        smsManager.sendTextMessage("07903681502", null, "ALERT!\n\n" + getMultiLineText(), null, null)
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
        val sdf = SimpleDateFormat("HH:mm:ss")
        val current = sdf.format(Date())
        var multiLineText = "Time:$current\n" +
                            "Thold:$threshold\n" +
                            "Delay:$delay\n" +
                            "Notifies:$loudEventsCounter\n" +
                            "n:$n\n\n" +
                            "Volumes:\n"
        maxSoundLevels.forEach{ maxSound -> multiLineText += "$maxSound\n"}

        return multiLineText
    }

    private fun getLogText() :String {
        val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm:ss")
        val current = sdf.format(Date())
        return "$current THold:$threshold Delay:$delay #ToTriggerNote:$numberOfLoudCyclesToTriggerNotifiction #ToKeepFile:$numberOfLoudCyclesToTriggerKeepFile #Notifications:$loudEventsCounter n:$n\n" +
                maxSoundLevels.map{ maxSound -> "$maxSound "} + "\n\n"

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
        var fileDelayValue :Long = delay
        File(getDropboxFolder() + "/delay.txt").forEachLine {
            if(it != "0") {
                fileDelayValue = it.toLong()
            }
        }
        return fileDelayValue
    }

    private fun getThreshold() :Int {
        var fileThresholdValue = 0
        File(getDropboxFolder() + "/threshold.txt").forEachLine {
            if(it != "0") {
                fileThresholdValue = it.toInt()
            }
        }
        return fileThresholdValue
    }


    private fun getNumberOfLoudCyclesToTriggerNotifiction() :Int {
        var fileNumberOfLoudCyclesToTriggerNotifictionValue = 0
        File(getDropboxFolder() + "/numberOfLoudCyclesToTriggerNotifiction.txt").forEachLine {
            if(it != "0") {
                fileNumberOfLoudCyclesToTriggerNotifictionValue = it.toInt()
            }
        }
        return fileNumberOfLoudCyclesToTriggerNotifictionValue
    }


    private fun getNumberOfLoudCyclesToTriggerKeepFile() :Int {
        var fileNumberOfLoudCyclesToTriggerKeepFileValue = 0
        File(getDropboxFolder() + "/numberOfLoudCyclesToTriggerKeepFile.txt").forEachLine {
            if(it != "0") {
                fileNumberOfLoudCyclesToTriggerKeepFileValue = it.toInt()
            }
        }
        return fileNumberOfLoudCyclesToTriggerKeepFileValue
    }
}