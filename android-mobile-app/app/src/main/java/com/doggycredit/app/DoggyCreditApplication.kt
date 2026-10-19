package com.doggycredit.app

import android.app.Application
import com.doggycredit.app.auth.AuthManager
import com.doggycredit.app.data.LiveDoggyCreditApi

class DoggyCreditApplication : Application() {
    val auth by lazy { AuthManager(this) }
    val api by lazy { LiveDoggyCreditApi(auth) }
}
