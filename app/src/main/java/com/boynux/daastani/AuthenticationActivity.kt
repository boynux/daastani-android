package com.boynux.daastani

import android.support.v7.app.AppCompatActivity

import android.os.Bundle;

import com.amazonaws.mobile.auth.ui.SignInUI;
import com.amazonaws.mobile.client.AWSMobileClient;

class AuthenticationActivity: AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)


        AWSMobileClient.getInstance().initialize(this) {
            val signInUI = AWSMobileClient.getInstance().getClient(
                    this@AuthenticationActivity,
                    SignInUI::class.java) as SignInUI?
            signInUI?.login(
                    this@AuthenticationActivity,
                    MainActivity::class.java)?.execute()
        }.execute()

    }
}