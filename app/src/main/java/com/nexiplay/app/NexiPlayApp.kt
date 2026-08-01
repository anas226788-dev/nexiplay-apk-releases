package com.nexiplay.app

import android.app.Application
import com.onesignal.OneSignal
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class NexiPlayApp : Application() {
    override fun onCreate() {
        super.onCreate()
        
        // Initialize AdManager & Start.io (if configured)
        CoroutineScope(Dispatchers.IO).launch {
            com.nexiplay.app.data.util.AdManager.loadConfig(com.nexiplay.app.data.SupabaseClient.main)
            if (com.nexiplay.app.data.util.AdManager.startioAppId.isNotEmpty()) {
                withContext(Dispatchers.Main) {
                    com.nexiplay.app.data.util.AdManager.initStartIo(this@NexiPlayApp, com.nexiplay.app.data.util.AdManager.startioAppId)
                }
            }
        }
        
        // Initialize OneSignal
        OneSignal.initWithContext(this, "69822dc3-4533-4b09-b105-d0ddaeb22c1f")
    }
}
