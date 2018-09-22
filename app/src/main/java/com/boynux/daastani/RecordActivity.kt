package com.boynux.daastani

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Bundle
import android.support.annotation.UiThread
import android.support.design.widget.Snackbar
import android.support.v4.app.ActivityCompat
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.widget.CompoundButton
import android.widget.Toast
import com.amazonaws.mobile.auth.core.IdentityManager
import com.amazonaws.mobile.client.AWSMobileClient
import com.amazonaws.mobile.client.AWSStartupHandler
import com.amazonaws.mobile.client.AWSStartupResult
import com.amazonaws.mobileconnectors.iot.AWSIotKeystoreHelper
import com.amazonaws.mobileconnectors.iot.AWSIotMqttClientStatusCallback
import com.amazonaws.mobileconnectors.s3.transferutility.TransferListener
import com.amazonaws.mobileconnectors.s3.transferutility.TransferState
import com.amazonaws.mobileconnectors.s3.transferutility.TransferUtility
import com.amazonaws.regions.Regions
import com.amazonaws.services.s3.AmazonS3Client

import kotlinx.android.synthetic.main.activity_record.*
import kotlinx.android.synthetic.main.content_record.*
import java.io.File
import java.io.IOException
import java.util.*
import kotlin.coroutines.experimental.buildSequence
import com.amazonaws.mobileconnectors.iot.AWSIotMqttManager
import com.amazonaws.mobileconnectors.iot.AWSIotMqttQos
import com.amazonaws.regions.Region
import com.amazonaws.services.cognitoidentity.AmazonCognitoIdentity
import com.amazonaws.services.cognitoidentity.AmazonCognitoIdentityClient
import com.amazonaws.services.cognitoidentity.model.GetIdRequest
import com.amazonaws.services.iot.AWSIotClient
import com.amazonaws.services.iot.model.AttachPolicyRequest
import com.amazonaws.services.iot.model.AttachPrincipalPolicyRequest
import com.amazonaws.services.iot.model.AttachThingPrincipalRequest
import com.amazonaws.services.iot.model.CreateKeysAndCertificateRequest
import java.security.KeyStore
import kotlin.concurrent.thread


private const val LOG_TAG = "AudioRecordTest"
private const val REQUEST_RECORD_AUDIO_PERMISSION = 200

class RecordActivity : AppCompatActivity() {
    // Requesting permission to RECORD_AUDIO
    private var permissionToRecordAccepted = false
    private var permissions: Array<String> = arrayOf(Manifest.permission.RECORD_AUDIO)

    private var mFileName: String = ""
    private var mLocalFilePath: String = ""
    private var mRecorder: MediaRecorder? = null
    private var mPlayer: MediaPlayer? = null

    var mStartPlaying = true

    override fun onRequestPermissionsResult(
            requestCode: Int,
            permissions: Array<String>,
            grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        permissionToRecordAccepted = if (requestCode == REQUEST_RECORD_AUDIO_PERMISSION) {
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        } else {
            false
        }
        if (!permissionToRecordAccepted) finish()
    }

    private fun startRecording() {
        mRecorder = MediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
            setOutputFile(mLocalFilePath)
            setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)

            try {
                prepare()
            } catch (e: IOException) {
                Log.e(LOG_TAG, "prepare() failed")
            }

            start()
        }
    }

    private fun stopRecording() {
        mRecorder?.apply {
            stop()
            release()
        }
        mRecorder = null
    }

    private fun startPlaying() {
        mPlayer = MediaPlayer().apply {
            try {
                setDataSource(mLocalFilePath)
                prepare()
                start()
            } catch (e: IOException) {
                Log.e(LOG_TAG, "prepare() failed")
            }
        }
    }

    private fun stopPlaying() {
        mPlayer?.release()
        mPlayer = null
    }

    private fun onPlay(start: Boolean) = if (start) {
        startPlaying()
    } else {
        stopPlaying()
    }

    private fun uploadWithTransferUtility(remote: String, local: File) {
        val txUtil = TransferUtility.builder()
                .context(applicationContext)
                .awsConfiguration(AWSMobileClient.getInstance().configuration)
                .s3Client(AmazonS3Client(AWSMobileClient.getInstance().credentialsProvider))
                .build()


        val userId = IdentityManager.getDefaultIdentityManager().cachedUserID

        val remotePath = "private/$userId/$remote"
        val txObserver = txUtil.upload(remotePath, local)
        txObserver.setTransferListener(object : TransferListener {
            override fun onStateChanged(id: Int, state: TransferState) {
                Log.d(LOG_TAG, "$state")
                if (state == TransferState.COMPLETED) {
                    // Handle a completed upload
                    Snackbar.make(window.decorView, "Successful", Snackbar.LENGTH_LONG)
                            .setAction("Action", null).show()
                }
            }

            override fun onProgressChanged(id: Int, current: Long, total: Long) {
                val done = ((current / total) * 100.0).toInt()
                Log.d(LOG_TAG, "ID: $id, percent done = $done")
            }

            override fun onError(id: Int, ex: Exception) {
                // Handle errors
                Snackbar.make(window.decorView, "Faild ${ex.message}", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show()
                Log.d(LOG_TAG, ex.message)
            }
        })

        // If you prefer to poll for the data, instead of attaching a
        // listener, check for the state and progress in the observer.
        if (txObserver.state == TransferState.COMPLETED) {
            // Handle a completed upload.
        }
    }

    fun generateFileSuffix(length: Int = 4): String {
        return buildSequence { val r = Random(); while(true) yield(r.nextInt(24)) }
                .take(length)
                .map{(it + 65).toChar()}
                .joinToString("")

    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        mFileName = "audiorecordtest-${generateFileSuffix()}.3gp"
        mLocalFilePath = "${externalCacheDir.absolutePath}/${mFileName}"

        AWSMobileClient.getInstance().initialize(this).execute()

        setContentView(R.layout.activity_record)
        setSupportActionBar(toolbar)

        filename_text.setText(mFileName)
        playItemButton.text = "Start playing"

        recordButton.setOnCheckedChangeListener { _: CompoundButton, b: Boolean ->
            if (b) {
                startRecording()
            } else {
                stopRecording()
            }
        }

        playItemButton.setOnClickListener {
            onPlay(mStartPlaying)
            playItemButton.text = when (mStartPlaying) {
                true -> "Stop playing"
                false -> "Start playing"
            }
            mStartPlaying = !mStartPlaying
        }

        saveButton.setOnClickListener {
            uploadWithTransferUtility(mFileName, File(mLocalFilePath))
        }

        ActivityCompat.requestPermissions(this, permissions, REQUEST_RECORD_AUDIO_PERMISSION)
    }

}