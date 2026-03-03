package ru.merrcurys.seacard

import android.app.Application
import kotlinx.coroutines.runBlocking
import ru.merrcurys.seacard.db.PrefsToRoomMigration

class SeaCardApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        runBlocking {
            PrefsToRoomMigration.migrateIfNeeded(applicationContext)
        }
    }
}
