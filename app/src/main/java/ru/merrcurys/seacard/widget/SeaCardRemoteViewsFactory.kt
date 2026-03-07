package ru.merrcurys.seacard.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import kotlinx.coroutines.runBlocking
import ru.merrcurys.seacard.R
import ru.merrcurys.seacard.core.db.DatabaseProvider
import ru.merrcurys.seacard.core.utils.CardSortUtil
import ru.merrcurys.seacard.domain.entity.Card
import java.io.File

class SeaCardRemoteViewsFactory(
    private val context: android.content.Context,
    private val intent: Intent
) : RemoteViewsService.RemoteViewsFactory {

    private var cards: List<Card> = emptyList()
    private var coverBitmaps: MutableList<Bitmap?> = mutableListOf()

    override fun onCreate() {}

    override fun onDataSetChanged() {
        runBlocking {
            val dao = DatabaseProvider.get(context).cardDao()
            val raw = dao.getAll().map { it.toCard() }
            val prefs = context.getSharedPreferences("settings", android.content.Context.MODE_PRIVATE)
            val sortTypeName = prefs.getString("sort_type", null)
            cards = CardSortUtil.sorted(raw, sortTypeName)
            coverBitmaps.clear()
            coverBitmaps.addAll(cards.map { card -> loadCoverBitmap(card) })
        }
    }

    private fun loadCoverBitmap(card: Card): Bitmap? {
        val path = card.frontCoverPath ?: return null
        return try {
            if (path.startsWith("cards/")) {
                context.assets.open(path).use { input ->
                    BitmapFactory.decodeStream(input)
                }
            } else {
                val file = File(path)
                if (file.exists()) BitmapFactory.decodeFile(path) else null
            }
        } catch (_: Exception) {
            null
        }
    }

    override fun onDestroy() {
        coverBitmaps.clear()
    }

    override fun getCount(): Int = cards.size

    override fun getViewAt(position: Int): RemoteViews? {
        if (position !in cards.indices) return null
        val card = cards[position]
        val views = RemoteViews(context.packageName, R.layout.widget_seacard_item)
        val bitmap = coverBitmaps.getOrNull(position)
        if (bitmap != null) {
            views.setImageViewBitmap(R.id.widget_card_cover, bitmap)
        } else {
            views.setImageViewResource(R.id.widget_card_cover, R.drawable.widget_card_placeholder)
        }
        val fillInIntent = Intent().putExtra("card_id", card.id)
        views.setOnClickFillInIntent(R.id.widget_card_cover, fillInIntent)
        return views
    }

    override fun getLoadingView(): RemoteViews? = null

    override fun getViewTypeCount(): Int = 1

    override fun getItemId(position: Int): Long = cards.getOrNull(position)?.id ?: position.toLong()

    override fun hasStableIds(): Boolean = true
}
