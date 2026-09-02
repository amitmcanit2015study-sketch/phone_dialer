package com.amitbharat.phonedialer

import android.app.Application
import com.amitbharat.phonedialer.utils.ThemeUtils

class DialerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ThemeUtils.applyTheme(this)
    }
}
