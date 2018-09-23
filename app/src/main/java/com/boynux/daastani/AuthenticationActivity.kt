package com.boynux.daastani

import android.content.Intent
import android.support.v7.app.AppCompatActivity

import android.os.Bundle;
import com.amazonaws.mobile.auth.core.IdentityManager
import com.amazonaws.mobile.auth.core.IdentityProvider
import com.amazonaws.mobile.auth.core.SignInStateChangeListener
import com.amazonaws.mobile.auth.core.signin.SignInManager
import com.amazonaws.mobile.auth.core.signin.SignInProviderResultHandler
import com.amazonaws.mobile.auth.ui.SignInUI;
import com.amazonaws.mobile.client.AWSMobileClient;
import java.lang.Exception


class AuthenticationActivity: AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        AWSMobileClient.getInstance().initialize(this).execute()

        val signInManager = SignInManager.getInstance(this)
        val provider = signInManager.getPreviouslySignedInProvider()

        IdentityManager.getDefaultIdentityManager().addSignInStateChangeListener(
                object : SignInStateChangeListener {
                    override fun onUserSignedIn() {
                        startActivity(Intent(applicationContext, MainActivity::class.java))
                    }

                    override fun onUserSignedOut() {
                        showSignIn()
                    }
                }
        )

        if(provider != null) {
            signInManager.refreshCredentialsWithProvider(this@AuthenticationActivity,
                    provider,  object: SignInProviderResultHandler {
                override fun onSuccess(provider: IdentityProvider?) {
                    startActivity(Intent(applicationContext, MainActivity::class.java))
                }

                override fun onCancel(provider: IdentityProvider?) {
                    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
                }

                override fun onError(provider: IdentityProvider?, ex: Exception?) {
                    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
                }
            })
        } else {
            showSignIn()
        }

        // Sign-in listener

    }

    fun showSignIn() {

        val signInUI = AWSMobileClient.getInstance().getClient(
                this@AuthenticationActivity,
                SignInUI::class.java) as SignInUI?

        signInUI?.login(
                this@AuthenticationActivity,
                MainActivity::class.java)?.execute()


    }
}