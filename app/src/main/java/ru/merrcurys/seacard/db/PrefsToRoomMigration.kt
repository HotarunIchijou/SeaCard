package ru.merrcurys.seacard.db

import android.content.Context
import androidx.room.Room

/**
 * Однократная миграция данных из SharedPreferences в Room.
 * После обновления приложения карточки пользователя переносятся в БД и не теряются.
 */
object PrefsToRoomMigration {

    private const val PREFS_NAME = "cards"
    private const val KEY_CARD_LIST = "card_list"
    private const val KEY_MIGRATED = "migrated_to_room"
    private const val PREFIX_COVER_FRONT = "cover_front_"
    private const val PREFIX_COVER_BACK = "cover_back_"
    private const val PREFIX_NOTE = "note_"

    /**
     * Выполняет миграцию, если она ещё не выполнялась.
     * Вызывать при первом обращении к БД (например, в DatabaseProvider.get).
     */
    suspend fun migrateIfNeeded(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_MIGRATED, false)) return

        val cardSet = prefs.getStringSet(KEY_CARD_LIST, null) ?: emptySet()
        if (cardSet.isEmpty()) {
            prefs.edit().putBoolean(KEY_MIGRATED, true).apply()
            return
        }

        val db = DatabaseProvider.get(context)
        val dao = db.cardDao()

        for (cardString in cardSet) {
            val parts = cardString.split("|")
            val entity = when (parts.size) {
                2 -> CardEntity(
                    name = parts[0],
                    code = parts[1],
                    type = "barcode",
                    addTime = System.currentTimeMillis(),
                    usageCount = 0,
                    color = 0xFFFFFFFF.toInt(),
                    frontCoverPath = null,
                    backCoverPath = null,
                    note = null
                )
                3 -> CardEntity(
                    name = parts[0],
                    code = parts[1],
                    type = parts[2],
                    addTime = System.currentTimeMillis(),
                    usageCount = 0,
                    color = 0xFFFFFFFF.toInt(),
                    frontCoverPath = null,
                    backCoverPath = null,
                    note = null
                )
                5 -> CardEntity(
                    name = parts[0],
                    code = parts[1],
                    type = parts[2],
                    addTime = parts[3].toLongOrNull() ?: System.currentTimeMillis(),
                    usageCount = parts[4].toIntOrNull() ?: 0,
                    color = 0xFFFFFFFF.toInt(),
                    frontCoverPath = null,
                    backCoverPath = null,
                    note = null
                )
                6 -> CardEntity(
                    name = parts[0],
                    code = parts[1],
                    type = parts[2],
                    addTime = parts[3].toLongOrNull() ?: System.currentTimeMillis(),
                    usageCount = parts[4].toIntOrNull() ?: 0,
                    color = parts[5].toIntOrNull() ?: 0xFFFFFFFF.toInt(),
                    frontCoverPath = null,
                    backCoverPath = null,
                    note = null
                )
                7 -> CardEntity(
                    name = parts[0],
                    code = parts[1],
                    type = parts[2],
                    addTime = parts[3].toLongOrNull() ?: System.currentTimeMillis(),
                    usageCount = parts[4].toIntOrNull() ?: 0,
                    color = parts[5].toIntOrNull() ?: 0xFFFFFFFF.toInt(),
                    frontCoverPath = parts[6].takeIf { it.isNotBlank() },
                    backCoverPath = null,
                    note = null
                )
                else -> continue
            }

            val id = dao.insert(entity)

            val frontCover = prefs.getString("${PREFIX_COVER_FRONT}${entity.name}_${entity.code}", null)
            val backCover = prefs.getString("${PREFIX_COVER_BACK}${entity.name}_${entity.code}", null)
            val note = prefs.getString("${PREFIX_NOTE}${entity.name}_${entity.code}_${entity.type}", null)

            if (frontCover != null || backCover != null || !note.isNullOrBlank()) {
                val updated = entity.copy(
                    id = id,
                    frontCoverPath = frontCover ?: entity.frontCoverPath,
                    backCoverPath = backCover ?: entity.backCoverPath,
                    note = note ?: entity.note
                )
                dao.update(updated)
            }
        }

        prefs.edit().putBoolean(KEY_MIGRATED, true).apply()
    }
}
