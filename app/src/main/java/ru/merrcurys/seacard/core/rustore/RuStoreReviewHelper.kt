package ru.merrcurys.seacard.core.rustore

import android.content.Context
import android.util.Log
import androidx.activity.ComponentActivity
import ru.rustore.sdk.review.RuStoreReviewManagerFactory

/**
 * RuStore In-app Review — см. [документацию](https://www.rustore.ru/help/sdk/reviews-ratings/kotlin-java/10-0-0).
 *
 * Частоту на стороне RuStore ограничивают они же (например, [RuStoreRequestLimitReached]); здесь дополнительно
 * не чаще чем раз в [MIN_INTERVAL_MS], чтобы не дергать SDK и не портить опыт.
 */
 
object RuStoreReviewHelper {
    private const val TAG = "RuStoreReview"
    private const val PREFS_NAME = "seacard_rustore"
    /** Новый ключ: время пишется только после успешного requestReviewFlow (старый ключ больше не читаем). */
    private const val KEY_LAST_REVIEW_SUCCESS_MS = "review_last_success_request_ms"

    /** Минимальный интервал между **успешными** requestReviewFlow (можно изменить под продукт). */
    private const val MIN_INTERVAL_MS = 14L * 24 * 60 * 60 * 1000 // 14 дней

    private fun formatElapsed(ms: Long): String {
        val minutes = (ms / 60_000).toInt().coerceAtLeast(1)
        val hours = ms / 3_600_000
        val days = ms / 86_400_000
        return when {
            days >= 1 -> "$days сут. ${(ms % 86_400_000) / 3_600_000} ч"
            hours >= 1 -> "$hours ч ${(ms % 3_600_000) / 60_000} мин"
            else -> "$minutes мин"
        }
    }

    fun tryLaunchReview(activity: ComponentActivity) {
        val prefs = activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val lastAttempt = prefs.getLong(KEY_LAST_REVIEW_SUCCESS_MS, 0L)
        if (lastAttempt != 0L && now - lastAttempt < MIN_INTERVAL_MS) {
            val elapsed = now - lastAttempt
            Log.i(
                TAG,
                "Пропуск: кулдаун 14 дней (прошло ${formatElapsed(elapsed)} с последнего успешного requestReviewFlow)",
            )
            return
        }
        Log.i(TAG, "Запрос review: requestReviewFlow()")

        val manager = RuStoreReviewManagerFactory.create(activity)
        manager.requestReviewFlow()
            .addOnSuccessListener { reviewInfo ->
                prefs.edit().putLong(KEY_LAST_REVIEW_SUCCESS_MS, System.currentTimeMillis()).apply()
                Log.i(TAG, "requestReviewFlow OK, launchReviewFlow()")
                manager.launchReviewFlow(reviewInfo)
                    .addOnSuccessListener {
                        Log.i(TAG, "launchReviewFlow: пользователь закрыл форму (onSuccess)")
                    }
                    .addOnFailureListener { t ->
                        Log.i(TAG, "launchReviewFlow onFailure (часто норма: лимит RuStore / не из RuStore)", t)
                    }
            }
            .addOnFailureListener { t ->
                Log.i(
                    TAG,
                    "requestReviewFlow onFailure: приложение не с RuStore, не авторизован, лимит, уже оценил и т.д.",
                    t,
                )
            }
    }
}
