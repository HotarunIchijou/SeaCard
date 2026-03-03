package ru.merrcurys.seacard

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ru.merrcurys.seacard.core.design.SeaCardTheme
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.IconButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.composed
import androidx.core.view.WindowCompat
import androidx.core.content.edit
import androidx.compose.ui.tooling.preview.Preview
import android.content.Intent
import androidx.compose.material3.Button
import androidx.compose.material.icons.filled.Delete
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import ru.merrcurys.seacard.core.design.GradientBackground
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ArrowDownward
import android.widget.Toast
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContracts
import java.io.OutputStreamWriter
import java.io.InputStreamReader
import java.io.BufferedReader
import java.nio.charset.StandardCharsets
import androidx.compose.foundation.layout.navigationBarsPadding
import ru.merrcurys.seacard.core.design.BerlinAzure
import ru.merrcurys.seacard.core.design.GradientColorOption
import ru.merrcurys.seacard.core.design.rememberGradientState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import ru.merrcurys.seacard.core.backup.BackupManager
import ru.merrcurys.seacard.core.db.CardEntity
import ru.merrcurys.seacard.core.db.DatabaseProvider
import ru.merrcurys.seacard.core.utils.CoverNames

class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        var exportCards: (() -> Unit)? = null
        var importCards: (() -> Unit)? = null
        val exportLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri: Uri? ->
            if (uri != null) {
                try {
                    runBlocking(Dispatchers.IO) {
                        val dao = DatabaseProvider.get(this@SettingsActivity).cardDao()
                        val cards = dao.getAll()
                        contentResolver.openOutputStream(uri)?.use { out ->
                            BackupManager.exportToZip(this@SettingsActivity, cards, out)
                        }
                    }
                    Toast.makeText(this, "Бэкап экспортирован (карточки и обложки)", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(this, "Ошибка экспорта: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                }
            }
        }
        val importLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            if (uri != null) {
                try {
                    val result = runBlocking(Dispatchers.IO) {
                        contentResolver.openInputStream(uri)?.use { input ->
                            val bytes = input.readBytes()
                            if (bytes.size >= 4 && bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte()) {
                                BackupManager.importFromZip(this@SettingsActivity, bytes.inputStream())
                            } else {
                                importLegacyTxt(this@SettingsActivity, String(bytes, StandardCharsets.UTF_8))
                            }
                        } ?: Pair(0, listOf("Не удалось открыть файл"))
                    }
                    val (imported, errors) = result
                    if (errors.isEmpty()) {
                        Toast.makeText(this, "Импортировано карт: $imported", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(this, "Импортировано: $imported. Ошибки: ${errors.take(3).joinToString()}", Toast.LENGTH_LONG).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(this, "Ошибка импорта: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                }
            }
        }
        exportCards = { exportLauncher.launch("seacard_backup.zip") }
        importCards = { importLauncher.launch("*/*") }
        setContent {
            val context = this
            // Always use dark theme
            val gradientState = rememberGradientState(context)
            LaunchedEffect(Unit) {
                val window = this@SettingsActivity.window
                WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = false
            }
            SeaCardTheme {
                GradientBackground(gradientColor = gradientState.gradientColor) {
                    SettingsScreen(
                        gradientColor = gradientState.gradientColor,
                        onGradientColorChange = { color ->
                            gradientState.updateGradientColor(color)
                            saveGradientColorPref(context, color)
                        },
                        onBack = { finish() },
                        topBarContainerColor = Color.Transparent,
                        onExport = { exportCards?.invoke() },
                        onImport = { importCards?.invoke() }
                    )
                }
            }
        }
    }

    private fun saveGradientColorPref(context: Context, color: Color) {
        val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        prefs.edit { putInt("gradient_color", color.hashCode()) }
    }
}

/** Импорт из старого формата TXT (построчно name|code|type|...). Без обложек. */
private suspend fun importLegacyTxt(context: Context, content: String): Pair<Int, List<String>> {
    val dao = DatabaseProvider.get(context).cardDao()
    val assetList = context.assets.list("cards")?.toSet() ?: emptySet()
    val errors = mutableListOf<String>()
    var imported = 0
    content.lines().forEach { line ->
        val parts = line.split("|")
        if (parts.size >= 2) {
            val name = parts[0]
            val code = parts[1]
            val type = parts.getOrNull(2) ?: "barcode"
            if (dao.getByNameCodeType(name, code, type) != null) return@forEach
            val coverAsset = if (parts.size >= 7) parts[6] else null
            val fixedCover = if (coverAsset != null && coverAsset.startsWith("cards/") && assetList.contains(coverAsset.removePrefix("cards/"))) {
                coverAsset
            } else {
                val found = CoverNames.coverNameMap.entries.find { it.value.equals(name, ignoreCase = true) }?.key
                if (found != null) "cards/$found" else null
            }
            val addTime = parts.getOrNull(3)?.toLongOrNull() ?: System.currentTimeMillis()
            val usageCount = parts.getOrNull(4)?.toIntOrNull() ?: 0
            val color = parts.getOrNull(5)?.toIntOrNull() ?: 0xFFFFFFFF.toInt()
            try {
                dao.insert(CardEntity(
                    name = name,
                    code = code,
                    type = type,
                    addTime = addTime,
                    usageCount = usageCount,
                    color = color,
                    frontCoverPath = fixedCover,
                    backCoverPath = null,
                    note = null
                ))
                imported++
            } catch (e: Exception) {
                errors.add(name)
            }
        } else if (line.isNotBlank()) {
            errors.add(line)
        }
    }
    return Pair(imported, errors)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    gradientColor: Color,
    onGradientColorChange: (Color) -> Unit,
    onBack: () -> Unit,
    topBarContainerColor: Color = Color.Transparent,
    onExport: () -> Unit = {},
    onImport: () -> Unit = {}
) {
    val colorScheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    var showDeleteDialog by remember { mutableStateOf(false) }
    val appVersion = BuildConfig.VERSION_NAME

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            TopAppBar(
                title = { Text("Настройки", color = colorScheme.onSurface, fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад", tint = colorScheme.onSurface)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
            Spacer(modifier = Modifier.height(32.dp))
            
            // Выбор градиентного цвета
            Surface(
                shape = RoundedCornerShape(16.dp),
                tonalElevation = 0.dp,
                shadowElevation = 0.dp,
                color = colorScheme.onPrimary,
                modifier = Modifier
                    .padding(horizontal = 32.dp)
                    .fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    Text(
                        text = "Градиентный цвет",
                        color = colorScheme.onSurface,
                        fontWeight = FontWeight.Medium,
                        fontSize = 18.sp,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    
                    // Цветовые варианты
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        GradientColorOption.values().forEach { option ->
                            val isSelected = gradientColor == option.color
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(
                                        color = option.color,
                                        shape = CircleShape
                                    )
                                    .border(
                                        width = if (isSelected) 3.dp else 0.dp,
                                        color = Color.White,
                                        shape = CircleShape
                                    )
                                    .clickable { onGradientColorChange(option.color) }
                            )
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            // Кнопка Telegram
            Button(
                onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, "https://t.me/SeacardSupportBot".toUri())
                    context.startActivity(intent)
                },
                shape = RoundedCornerShape(14.dp),
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = colorScheme.onPrimary,
                    contentColor = colorScheme.onSurface
                ),
                elevation = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp)
            ) {
                Text("Связаться с разработчиком", fontWeight = FontWeight.Medium)
            }
            Spacer(modifier = Modifier.height(24.dp))
            // Кнопки экспорта и импорта карточек на одной линии
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = onExport,
                    shape = RoundedCornerShape(14.dp),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = colorScheme.onPrimary,
                        contentColor = colorScheme.onSurface
                    ),
                    elevation = null,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Filled.ArrowUpward,
                        contentDescription = null,
                        tint = colorScheme.onSurface,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Экспорт", fontWeight = FontWeight.Medium)
                }
                Button(
                    onClick = onImport,
                    shape = RoundedCornerShape(14.dp),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = colorScheme.onPrimary,
                        contentColor = colorScheme.onSurface
                    ),
                    elevation = null,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Filled.ArrowDownward,
                        contentDescription = null,
                        tint = colorScheme.onSurface,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Импорт", fontWeight = FontWeight.Medium)
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
            // Кнопка удалить все карточки
            Button(
                onClick = { showDeleteDialog = true },
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = Color(0xFFF44336)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text("Удалить все карточки", color = Color.White, fontWeight = FontWeight.Medium)
            }
            Spacer(modifier = Modifier.weight(1f))
            // Версия приложения
            Text(
                text = "Версия приложения: $appVersion",
                color = colorScheme.onSurface.copy(alpha = 0.5f),
                fontSize = 14.sp,
                modifier = Modifier.padding(bottom = 16.dp)
                    .navigationBarsPadding()
            )
        }
        // Диалог подтверждения удаления
        if (showDeleteDialog) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { showDeleteDialog = false },
                title = { Text("Удалить все карточки?") },
                text = { Text("Вы уверены что хотите удалить все карточки?") },
                confirmButton = {
                    androidx.compose.material3.TextButton(
                        onClick = {
                            runBlocking(Dispatchers.IO) {
                                DatabaseProvider.get(context).cardDao().deleteAll()
                            }
                            showDeleteDialog = false
                            onBack()
                        }
                    ) {
                        Text("Удалить", color = Color.Red)
                    }
                },
                dismissButton = {
                    androidx.compose.material3.TextButton(onClick = { showDeleteDialog = false }) {
                        Text("Отмена")
                    }
                },
                containerColor = colorScheme.surface,
                titleContentColor = colorScheme.onSurface,
                textContentColor = colorScheme.onSurface
            )
        }
    }
}

fun Modifier.noRippleClickable(onClick: () -> Unit): Modifier = composed {
    this.clickable(
        indication = null,
        interactionSource = remember { MutableInteractionSource() },
        onClick = onClick
    )
}

@Preview(showBackground = true)
@Composable
fun SettingsScreenPreview() {
    SeaCardTheme {
        SettingsScreen(
            gradientColor = BerlinAzure,
            onGradientColorChange = {},
            onBack = {},
            topBarContainerColor = Color.Transparent
        )
    }
}