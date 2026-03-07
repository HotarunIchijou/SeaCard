package ru.merrcurys.seacard.widget

import android.content.Intent
import android.widget.RemoteViewsService

class SeaCardWidgetService : RemoteViewsService() {

    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        return SeaCardRemoteViewsFactory(applicationContext, intent)
    }
}
