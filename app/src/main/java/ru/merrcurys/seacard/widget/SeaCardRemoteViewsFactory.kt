package ru.merrcurys.seacard.widget

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.TypedValue
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

    /** Макс. сторона превью в px: мало для ячейки ~100dp, зато укладываемся в лимит Binder на элемент RemoteViews. */
    private val thumbnailMaxSidePx: Int by lazy {
        val dm = context.resources.displayMetrics
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 160f, dm).toInt().coerceAtLeast(128)
    }

    override fun onCreate() {}

    override fun onDataSetChanged() {
        runBlocking {
            val dao = DatabaseProvider.get(context).cardDao()
            val raw = dao.getAll().map { it.toCard() }
            val prefs = context.getSharedPreferences("settings", android.content.Context.MODE_PRIVATE)
            val sortTypeName = prefs.getString("sort_type", null)
            cards = CardSortUtil.sorted(raw, sortTypeName)
        }
    }

    private fun decodeCoverThumbnail(card: Card): Bitmap? {
        val path = card.frontCoverPath ?: return null
        return try {
            if (path.startsWith("cards/")) {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.assets.open(path).use { BitmapFactory.decodeStream(it, null, bounds) }
                val opts = BitmapFactory.Options().apply {
                    inSampleSize = computeInSampleSize(bounds, thumbnailMaxSidePx)
                    inJustDecodeBounds = false
                }
                context.assets.open(path).use { BitmapFactory.decodeStream(it, null, opts) }
            } else {
                val file = File(path)
                if (!file.exists()) return null
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(file.absolutePath, bounds)
                val opts = BitmapFactory.Options().apply {
                    inSampleSize = computeInSampleSize(bounds, thumbnailMaxSidePx)
                    inJustDecodeBounds = false
                }
                BitmapFactory.decodeFile(file.absolutePath, opts)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun computeInSampleSize(bounds: BitmapFactory.Options, maxSidePx: Int): Int {
        var h = bounds.outHeight
        var w = bounds.outWidth
        if (h <= 0 || w <= 0) return 1
        var inSampleSize = 1
        while (h > maxSidePx || w > maxSidePx) {
            inSampleSize *= 2
            h /= 2
            w /= 2
        }
        return inSampleSize.coerceAtLeast(1)
    }

    override fun onDestroy() {
        cards = emptyList()
    }

    override fun getCount(): Int = cards.size

    override fun getViewAt(position: Int): RemoteViews? {
        if (position !in cards.indices) return null
        val card = cards[position]
        val views = RemoteViews(context.packageName, R.layout.widget_seacard_item)
        val bitmap = decodeCoverThumbnail(card)
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
