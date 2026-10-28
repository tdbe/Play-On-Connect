package com.tdbe.playonconnect

import android.app.Application
import com.tdbe.playonconnect.data.AppSettings
import com.tdbe.playonconnect.data.RuleStore

class PlayOnConnectApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppSettings.init(this)
        RuleStore.init(this)
    }
}
