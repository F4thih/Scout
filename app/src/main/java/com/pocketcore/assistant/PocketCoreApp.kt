package com.pocketcore.assistant

import android.app.Application
import android.util.Log
import com.pocketcore.assistant.core.FeatureRegistry
import com.pocketcore.assistant.modules.reminder.ReminderModule
import com.pocketcore.assistant.modules.utility.AppLauncherModule
import com.pocketcore.assistant.modules.utility.NoteModule

class PocketCoreApp : Application() {

    override fun onCreate() {
        super.onCreate()
        Log.i("PocketCoreApp", "Initializing PocketCore Assistant (Offline & Zero-Battery)")

        // Modular Plugin Registration:
        // Adding new features takes just 1 line of code here!
        FeatureRegistry.registerModule(ReminderModule())
        FeatureRegistry.registerModule(NoteModule())
        FeatureRegistry.registerModule(AppLauncherModule())
    }
}
