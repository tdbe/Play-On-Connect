package com.tdbe.playonconnect

import android.app.Application
import com.tdbe.playonconnect.data.RuleStore

class PlayOnConnectApp : Application() {
    override fun onCreate() {
        super.onCreate()
        RuleStore.init(this)
    }
}
