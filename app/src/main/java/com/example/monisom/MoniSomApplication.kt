package com.example.monisom

import android.app.Application
import com.example.monisom.data.NetworkMonitor

class MoniSomApplication : Application() {

    val networkMonitor: NetworkMonitor by lazy { NetworkMonitor(this) }
}
