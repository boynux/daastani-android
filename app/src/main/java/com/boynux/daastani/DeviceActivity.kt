package com.boynux.daastani

import android.content.Intent
import android.media.MediaPlayer
import android.os.Bundle
import android.support.design.widget.Snackbar
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.CardView
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Toast
import com.amazonaws.mobile.auth.core.IdentityManager
import com.amazonaws.mobile.client.AWSMobileClient
import com.amazonaws.mobile.client.AWSStartupHandler
import com.amazonaws.mobile.client.AWSStartupResult
import com.amazonaws.mobile.config.AWSConfiguration
import com.amazonaws.mobileconnectors.iot.AWSIotKeystoreHelper
import com.amazonaws.mobileconnectors.iot.AWSIotMqttClientStatusCallback
import com.amazonaws.mobileconnectors.iot.AWSIotMqttManager
import com.amazonaws.mobileconnectors.iot.AWSIotMqttQos
import com.amazonaws.regions.Region
import com.amazonaws.regions.Regions
import com.amazonaws.services.iot.AWSIotClient
import com.amazonaws.services.iot.model.AttachPrincipalPolicyRequest
import com.amazonaws.services.iot.model.CreateKeysAndCertificateRequest
import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.services.s3.model.S3ObjectSummary
import kotlinx.android.synthetic.main.activity_device.*
import kotlinx.android.synthetic.main.record_item.view.*
import java.io.IOException
import java.security.KeyStore
import java.util.*
import kotlin.concurrent.thread

private const val LOG_TAG = "DeviceActivity"

class Recording(val name: String)

class DeviceActivity : AppCompatActivity() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var viewAdapter: RecordingsAdapter
    private lateinit var viewManager: RecyclerView.LayoutManager
    private lateinit var s3Client: AmazonS3Client
    private lateinit var mPlayer: MediaPlayer

    private lateinit var mqttManager: MQTTManager


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_device)

        mqttManager = MQTTManager(this)
        // init()

        mPlayer = MediaPlayer()

        viewManager = LinearLayoutManager(this)
        viewAdapter = RecordingsAdapter(emptyList(), object : RecordingsAdapter.OnItemClickListener {
            override fun onItemClick(item: S3ObjectSummary) {
                Log.e(LOG_TAG, "Device id is: ${item.key}")

                val calendar = Calendar.getInstance()
                calendar.add(Calendar.HOUR, 1)

                val file = s3Client.generatePresignedUrl("daastani-assets", item.key, calendar.time)

                onPlay(file.toString())
            }

        })

        recyclerView = findViewById<RecyclerView>(R.id.recordings_list).apply {
            // use this setting to improve performance if you know that changes
            // in content do not change the layout size of the RecyclerView
            setHasFixedSize(true)

            // use a linear layout manager
            layoutManager = viewManager

            // specify an viewAdapter (see also next example)
            adapter = viewAdapter

        }

        fab.setOnClickListener { view ->
            Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                    .setAction("Action", null).show()

            val recordIntent = Intent(this, RecordActivity::class.java)
            startActivity(recordIntent)
        }

        val s3Client = AmazonS3Client(AWSMobileClient.getInstance().credentialsProvider)
        onS3ClientReady(s3Client)

        // sendMessageToDevice()
        thread {
            mqttManager.connect(object : MQTTManager.ConnectHandler() {
                override fun onConnected() {
                    runOnUiThread {
                        Toast.makeText(this@DeviceActivity, "Connted to device!", Toast.LENGTH_SHORT).show()
                    }

                    // iotManager.publishString("{\"msg\": \"test string published!\"}", "test topic", AWSIotMqttQos.QOS1);
                }

                override fun onDisconnected() {
                    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
                }

                override fun onError(e: Throwable?) {
                    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
                }
            })
        }
    }

    private fun startPlaying(filename: String) {
        mPlayer.apply {
            try {
                reset()

                setDataSource(filename)
                prepare()
                start()
            } catch (e: IOException) {
                Log.e(LOG_TAG, "prepare() failed")
            }
        }
    }


    private fun stopPlaying() {
        mPlayer.stop()
    }

    private fun onPlay(filename: String) = if (mPlayer.isPlaying) {
        stopPlaying()
    } else {
        startPlaying(filename)
    }

    fun onS3ClientReady(s3Client: AmazonS3Client) {
        this.s3Client = s3Client

        val userId = IdentityManager.getDefaultIdentityManager().cachedUserID
        thread {
            val items = s3Client.listObjects("daastani-assets", "private/$userId/")
            runOnUiThread {
                viewAdapter.updateDataSet(items.objectSummaries)
            }
        }
    }
}

class RecordingsAdapter(private var myDataset: List<S3ObjectSummary>, private val listener: OnItemClickListener) :
        RecyclerView.Adapter<RecordingsAdapter.MyViewHolder>() {

    interface OnItemClickListener {
        fun onItemClick(item: S3ObjectSummary)
    }

    // Provide a reference to the views for each data item
    // Complex data items may need more than one view per item, and
    // you provide access to all the views for a data item in a view holder.
    // Each data item is just a string in this case that is shown in a TextView.
    class MyViewHolder(val card: CardView) : RecyclerView.ViewHolder(card) {
        fun bind(item: S3ObjectSummary, listener: OnItemClickListener ) {
            card.playItemButton.setOnClickListener { listener.onItemClick(item); };
        }
    }


    // Create new views (invoked by the layout manager)
    override fun onCreateViewHolder(parent: ViewGroup,
                                    viewType: Int): RecordingsAdapter.MyViewHolder {
        // create a new view
        val textView = LayoutInflater.from(parent.context)
                .inflate(R.layout.record_item, parent, false) as CardView
        // set the view's size, margins, paddings and layout parameters

        return MyViewHolder(textView)
    }

    // Replace the contents of a view (invoked by the layout manager)
    override fun onBindViewHolder(holder: MyViewHolder, position: Int) {
        // - get element from your dataset at this position
        // - replace the contents of the view with that element
        Log.e(LOG_TAG, "New record item: ${myDataset[position]} : $position")
        holder.card.recording_text.text = myDataset[position].key.substringAfterLast('/')
        holder.bind(myDataset[position], listener)
    }

    // Return the size of your dataset (invoked by the layout manager)
    override fun getItemCount() = myDataset.size

    fun updateDataSet(set: List<S3ObjectSummary>) {
        myDataset = set
        notifyDataSetChanged()

    }
}

