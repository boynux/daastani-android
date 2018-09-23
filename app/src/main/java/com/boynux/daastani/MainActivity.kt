package com.boynux.daastani

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.support.design.widget.Snackbar
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.CardView
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.amazonaws.mobile.auth.core.IdentityManager
import com.amazonaws.mobile.client.AWSMobileClient
import com.amazonaws.mobile.client.AWSStartupHandler
import com.amazonaws.mobile.client.AWSStartupResult
import com.amazonaws.mobileconnectors.dynamodbv2.dynamodbmapper.*
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient
import kotlinx.android.synthetic.main.activity_main.*
import org.w3c.dom.Text
import kotlin.concurrent.thread

private const val LOG_TAG = "AudioRecordTest"

interface DynamoDbInitializerListerner {
    fun OnInstanceReady(client: DynamoDBMapper)
}

@DynamoDBTable(tableName = "daastani")
class UserDO {
    @DynamoDBHashKey(attributeName = "userid")
    @DynamoDBAttribute(attributeName = "userid")
    var userid: String? = null

    @DynamoDBRangeKey(attributeName = "device_id")
    @DynamoDBAttribute(attributeName = "device_id")
    var deviceId: String? = null
}

class MainActivity : AppCompatActivity(), DynamoDbInitializerListerner {
    private lateinit var recyclerView: RecyclerView
    private lateinit var viewAdapter: MyAdapter
    private lateinit var viewManager: RecyclerView.LayoutManager

    override fun OnInstanceReady(client: DynamoDBMapper) {
        ddbMapper = client

        readBooks()
    }

    private var ddbMapper: DynamoDBMapper? = null

    private fun readBooks() {
        val userId = IdentityManager.getDefaultIdentityManager().cachedUserID

        thread(start = true) {
            val expression = DynamoDBQueryExpression<UserDO>()

            expression.hashKeyValues = UserDO().apply {
                userid = userId
            }

            val devicesList = ddbMapper?.query(UserDO::class.java, expression)
            var devices = mutableListOf<UserDO>()

            devicesList?.forEach {
                devices.add(it)
                Log.d(LOG_TAG, "Books Item: UserId: ${it.userid}, DeviceId: ${it.deviceId}")
            }

            runOnUiThread {
                viewAdapter.updateDataSet(devices)
            }
        }
    }


    override fun onCreate(icicle: Bundle?) {
        super.onCreate(icicle)

        setContentView(R.layout.activity_main)
        setSupportActionBar(toolbar)

        viewManager = LinearLayoutManager(this)
        viewAdapter = MyAdapter(mutableListOf<UserDO>(), object: MyAdapter.OnItemClickListener {
            override fun onItemClick(item: UserDO) {
                Log.e(LOG_TAG, "Device id is: ${item.deviceId}")

                val deviceActivity = Intent(applicationContext, DeviceActivity::class.java)
                deviceActivity.putExtra("device_id", item.deviceId)
                startActivity(deviceActivity)
            }

        })

        recyclerView = findViewById<RecyclerView>(R.id.device_list).apply {
            // use this setting to improve performance if you know that changes
            // in content do not change the layout size of the RecyclerView
            setHasFixedSize(true)

            // use a linear layout manager
            layoutManager = viewManager

            // specify an viewAdapter (see also next example)
            adapter = viewAdapter

        }

        val ddbClient = AmazonDynamoDBClient(AWSMobileClient.getInstance().credentialsProvider)
        val ddbMapper = DynamoDBMapper.builder()
                .dynamoDBClient(ddbClient)
                .awsConfiguration(AWSMobileClient.getInstance().configuration)
                .build()

        OnInstanceReady(ddbMapper)


    }
}

class MyAdapter(private var myDataset: MutableList<UserDO>, private val listener: OnItemClickListener) :
        RecyclerView.Adapter<MyAdapter.MyViewHolder>() {

    interface OnItemClickListener {
        fun onItemClick(item: UserDO)
    }

    // Provide a reference to the views for each data item
    // Complex data items may need more than one view per item, and
    // you provide access to all the views for a data item in a view holder.
    // Each data item is just a string in this case that is shown in a TextView.
    class MyViewHolder(val card: CardView) : RecyclerView.ViewHolder(card) {
        fun bind(item: UserDO, listener: OnItemClickListener ) {
            card.setOnClickListener(object: View.OnClickListener {
                override fun onClick(v: View) {
                    listener.onItemClick(item);
                }
            });
        }
    }


    // Create new views (invoked by the layout manager)
    override fun onCreateViewHolder(parent: ViewGroup,
                                    viewType: Int): MyAdapter.MyViewHolder {
        // create a new view
        val textView = LayoutInflater.from(parent.context)
                .inflate(R.layout.device_card, parent, false) as CardView
        // set the view's size, margins, paddings and layout parameters

        return MyViewHolder(textView)
    }

    // Replace the contents of a view (invoked by the layout manager)
    override fun onBindViewHolder(holder: MyViewHolder, position: Int) {
        // - get element from your dataset at this position
        // - replace the contents of the view with that element
        holder.card.findViewById<TextView>(R.id.device_id).text = myDataset[position].deviceId
        holder.bind(myDataset[position], listener)
    }

    // Return the size of your dataset (invoked by the layout manager)
    override fun getItemCount() = myDataset.size

    fun updateDataSet(set: List<UserDO>) {
        myDataset = set.toMutableList()
        notifyDataSetChanged()

    }
}
