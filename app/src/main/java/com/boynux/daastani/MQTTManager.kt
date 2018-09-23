package com.boynux.daastani

import android.content.Context
import android.util.Log
import android.widget.Toast
import com.amazonaws.auth.AWSCredentialsProvider
import com.amazonaws.mobile.client.AWSMobileClient
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
import java.net.ConnectException
import java.security.KeyStore
import java.util.*
import kotlin.concurrent.thread

private val LOG_TAG = MQTTManager::class.simpleName

class MQTTManager constructor(val context: Context) {
    // Region of AWS IoT
    private val MY_REGION = Region.getRegion(Regions.EU_CENTRAL_1)
    // IoT endpoint
    // AWS Iot CLI describe-endpoint call returns: XXXXXXXXXX.iot.<region>.amazonaws.com
    private val CUSTOMER_SPECIFIC_ENDPOINT: String

    // Name of the AWS IoT policy to attach to a newly created certificate
    private val AWS_IOT_POLICY_NAME: String

    // Filename of KeyStore file on the filesystem
    private val KEYSTORE_NAME: String
    // Password for the private key in the KeyStore
    private val KEYSTORE_PASSWORD: String
    // Certificate and key aliases in the KeyStore
    private val CERTIFICATE_ID = "default"

    private var clientKeyStore: KeyStore? = null
    private lateinit var iotManager: AWSIotMqttManager

    init {
        val config = AWSConfiguration(context)

        val jsonObject = config.optJsonObject("IoT")
        if (jsonObject == null) {
            Log.e(LOG_TAG, "Could not load IoT config from aws configuration")
        }

        CUSTOMER_SPECIFIC_ENDPOINT = jsonObject.getString("Endpoint")
        AWS_IOT_POLICY_NAME = jsonObject.getString("Policy")
        KEYSTORE_NAME = jsonObject.getString("KeystoreName")
        KEYSTORE_PASSWORD = jsonObject.getString("KeystorePassword")
    }

    private fun getCredentialsProvider(): AWSCredentialsProvider? {
        return AWSMobileClient.getInstance().credentialsProvider
    }

    private fun loadKeystore() {
        val keystorePath = context.filesDir.getPath();
        val keystoreName = KEYSTORE_NAME;
        val keystorePassword = KEYSTORE_PASSWORD;
        val certificateId = CERTIFICATE_ID;

        // IoT Client (for creation of certificate if needed)
        val mIotAndroidClient = AWSIotClient(getCredentialsProvider());
        mIotAndroidClient.setRegion(MY_REGION);

        // To load cert/key from keystore on filesystem
        try {
            if (AWSIotKeystoreHelper.isKeystorePresent(keystorePath, keystoreName)) {
                if (AWSIotKeystoreHelper.keystoreContainsAlias(certificateId, keystorePath,
                                keystoreName, keystorePassword)) {
                    Log.i(LOG_TAG, "Certificate " + certificateId
                            + " found in keystore - using for MQTT.");
                    // load keystore from file into memory to pass on connection
                    clientKeyStore = AWSIotKeystoreHelper.getIotKeystore(certificateId,
                            keystorePath, keystoreName, keystorePassword);
                } else {
                    Log.i(LOG_TAG, "Key/cert " + certificateId + " not found in keystore.");
                }
            } else {
                Log.i(LOG_TAG, "Keystore " + keystorePath + "/" + keystoreName + " not found.");
            }
        } catch (e: java.lang.Exception) {
            Log.e(LOG_TAG, "An error occurred retrieving cert/key from keystore.", e);
        }

        if (clientKeyStore == null) {
            Log.i(LOG_TAG, "Cert/key was not found in keystore - creating new key and certificate.");

            thread {
                try {
                    // Create a new private key and certificate. This call
                    // creates both on the server and returns them to the
                    // device.
                    val createKeysAndCertificateRequest =
                            CreateKeysAndCertificateRequest();
                    createKeysAndCertificateRequest.setSetAsActive(true);
                    val createKeysAndCertificateResult =
                            mIotAndroidClient.createKeysAndCertificate(createKeysAndCertificateRequest);
                    Log.i(LOG_TAG,
                            "Cert ID: " +
                                    createKeysAndCertificateResult.getCertificateId() +
                                    " created.");

                    // store in keystore for use in MQTT client
                    // saved as alias "default" so a new certificate isn't
                    // generated each run of this application
                    AWSIotKeystoreHelper.saveCertificateAndPrivateKey(certificateId,
                            createKeysAndCertificateResult.getCertificatePem(),
                            createKeysAndCertificateResult.getKeyPair().getPrivateKey(),
                            keystorePath, keystoreName, keystorePassword);

                    // load keystore from file into memory to pass on
                    // connection
                    clientKeyStore = AWSIotKeystoreHelper.getIotKeystore(certificateId,
                            keystorePath, keystoreName, keystorePassword);

                    // Attach a policy to the newly created certificate.
                    // This flow assumes the policy was already created in
                    // AWS IoT and we are now just attaching it to the
                    // certificate.
                    val policyAttachRequest = AttachPrincipalPolicyRequest();
                    policyAttachRequest.setPolicyName(AWS_IOT_POLICY_NAME);
                    policyAttachRequest.setPrincipal(createKeysAndCertificateResult
                            .getCertificateArn());
                    mIotAndroidClient.attachPrincipalPolicy(policyAttachRequest);
                } catch (e: Exception) {
                    Log.e(LOG_TAG,
                            "Exception occurred when generating new private key and certificate.",
                            e);
                }
            }
        }
    }

    fun connect(handler: ConnectHandler): String {
        Log.e(LOG_TAG, "Connecting to IoT Message Q ...")
        val clientId = UUID.randomUUID().toString()

        Log.e(LOG_TAG, "Identity ID: ${clientId}")

        loadKeystore()

        if (clientKeyStore != null) {
            iotManager = AWSIotMqttManager(clientId, CUSTOMER_SPECIFIC_ENDPOINT)
            // iotManager.isAutoReconnect = false
            // iotManager.keepAlive = 0

            iotManager.connect(clientKeyStore, object : AWSIotMqttClientStatusCallback {
                override fun onStatusChanged(status: AWSIotMqttClientStatusCallback.AWSIotMqttClientStatus?, throwable: Throwable?) {
                    Log.d(LOG_TAG, "Status = $status")

                    if (throwable != null) {
                        handler.onError(throwable)

                        return
                        // throwable.printStackTrace()
                    }

                    when(status) {
                        AWSIotMqttClientStatusCallback.AWSIotMqttClientStatus.Connected -> handler.onConnected()
                        AWSIotMqttClientStatusCallback.AWSIotMqttClientStatus.ConnectionLost -> handler.onDisconnected()
                        AWSIotMqttClientStatusCallback.AWSIotMqttClientStatus.Connecting -> handler.onConnecting()
                        else -> handler.onError(throwable)
                    }
                    if (status == AWSIotMqttClientStatusCallback.AWSIotMqttClientStatus.Connected) {
                        handler.onConnected()

                        iotManager.publishString("{\"msg\": \"test string published!\"}", "test topic", AWSIotMqttQos.QOS1);
                    }
                }
            })
        } else {
            throw ConnectException("Could not load keystore. Giving up!")
        }

        return clientId
    }

    abstract class ConnectHandler {
        abstract fun onConnected()
        abstract fun onDisconnected()
        fun onConnecting() {

        }
        abstract fun onError(e: Throwable?)
    }
}
