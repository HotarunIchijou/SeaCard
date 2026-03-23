package ru.merrcurys.seacard.features.main

import android.content.Intent
import android.graphics.BitmapFactory
import ru.merrcurys.seacard.features.scan.ScanCardActivity
import ru.merrcurys.seacard.features.detail.CardDetailActivity
import ru.merrcurys.seacard.features.settings.SettingsActivity
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Wallet
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.first
import ru.merrcurys.seacard.core.rustore.RuStoreInAppUpdateController
import ru.merrcurys.seacard.core.rustore.RuStoreReviewHelper
import ru.merrcurys.seacard.core.utils.SortType
import ru.merrcurys.seacard.core.design.SeaCardTheme
import ru.merrcurys.seacard.core.design.GradientBackground
import ru.merrcurys.seacard.core.design.GradientUtils
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.material3.Text
import androidx.compose.ui.text.TextStyle
import ru.merrcurys.seacard.domain.entity.Card as CardModel

private const val RU_STORE_REVIEW_LOG_TAG = "RuStoreReview"

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val viewModel: MainViewModel = viewModel()
            val cards by viewModel.cards.collectAsStateWithLifecycle(initialValue = emptyList())
            val currentSortType by viewModel.sortType.collectAsStateWithLifecycle()
            val showCoverPicker by viewModel.showCoverPicker.collectAsStateWithLifecycle()

            val scanCardLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { }
            val cardDetailLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { }
            val settingsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { }
            val context = this@MainActivity

            DisposableEffect(Unit) {
                val ruStoreUpdate = RuStoreInAppUpdateController(this@MainActivity)
                ruStoreUpdate.checkOnLaunch()
                onDispose { ruStoreUpdate.dispose() }
            }

            LaunchedEffect(Unit) {
                val window = this@MainActivity.window
                WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = false
            }

            // RuStore: оценка/отзыв — когда на главном экране и карт ≥ 5 (см. тег RuStoreReview в logcat)
            LaunchedEffect(Unit) {
                Log.i(RU_STORE_REVIEW_LOG_TAG, "Ожидание: главный экран (!picker) и карт >= 5, сейчас карт=${cards.size}")
                snapshotFlow { !showCoverPicker && cards.size >= 5 }
                    .first { it }
                Log.i(RU_STORE_REVIEW_LOG_TAG, "Условие выполнено, вызываем RuStoreReviewHelper")
                RuStoreReviewHelper.tryLaunchReview(this@MainActivity)
            }

            SeaCardTheme {
                if (showCoverPicker) {
                    CardCoverPickerScreen(
                        onCoverSelected = { coverAsset: String? ->
                            viewModel.setShowCoverPicker(false)
                            val intent = Intent(context, ScanCardActivity::class.java)
                            if (coverAsset != null) intent.putExtra("cover_asset", coverAsset)
                            scanCardLauncher.launch(intent)
                        },
                        onBack = { viewModel.setShowCoverPicker(false) }
                    )
                } else {
                    MainScreen(
                        cards = cards,
                        currentSortType = currentSortType,
                        onAddCard = { viewModel.setShowCoverPicker(true) },
                        onCardClick = { card ->
                            viewModel.updateCardUsage(card.id)
                            cardDetailLauncher.launch(Intent(context, CardDetailActivity::class.java).apply { putExtra("card_id", card.id) })
                        },
                        onSettingsClick = { settingsLauncher.launch(Intent(context, SettingsActivity::class.java)) },
                        onSortTypeChange = { viewModel.setSortType(it) },
                        onDeleteCards = { viewModel.deleteCards(it) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun MainScreen(
    cards: List<CardModel>,
    currentSortType: SortType,
    onAddCard: () -> Unit,
    onCardClick: (CardModel) -> Unit,
    onSettingsClick: () -> Unit,
    onSortTypeChange: (SortType) -> Unit,
    onDeleteCards: (List<CardModel>) -> Unit
) {
    val context = LocalContext.current
    val colorScheme = MaterialTheme.colorScheme
    // Загружаем градиентный цвет при каждом перерисовке для обновления при возвращении из настроек
    val gradientColor = GradientUtils.loadGradientColorPref(context)
    GradientBackground(gradientColor = gradientColor) {
        var searchQuery by remember { mutableStateOf("") }
        var showSearch by remember { mutableStateOf(false) }
        var showFilterMenu by remember { mutableStateOf(false) }
        var selectedCards by remember { mutableStateOf<Set<CardModel>>(emptySet()) }
        var selectionMode by remember { mutableStateOf(false) }
    
        // Функция для определения темного цвета
        fun isColorDark(color: Int): Boolean {
            val red = (color shr 16) and 0xFF
            val green = (color shr 8) and 0xFF
            val blue = color and 0xFF
            val brightness = (red * 299 + green * 587 + blue * 114) / 1000
            return brightness < 128
        }
    
        val filteredCards = remember(cards, searchQuery) {
            fun normalize(text: String): String {
                return text
                    .replace("'", "")
                    .replace("’", "")
                    .replace("`", "")
                    .replace("ё", "е", ignoreCase = true)
                    .replace("Ё", "Е", ignoreCase = true)
                    .lowercase()
            }
            if (searchQuery.isBlank()) {
                cards
            } else {
                val normQuery = normalize(searchQuery)
                cards.filter { card ->
                    normalize(card.name).contains(normQuery)
                }
            }
        }
    
        // Обработка системной кнопки "назад" для сброса выбора
        BackHandler(enabled = selectionMode) {
            selectionMode = false
            selectedCards = emptySet()
        }
    
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = {
                        if (selectionMode) {
                            Text("Выбрано: ${selectedCards.size}", color = colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        } else if (showSearch) {
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                placeholder = { Text("Поиск карт...") },
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = colorScheme.onSurface,
                                    unfocusedTextColor = colorScheme.onSurface,
                                    focusedBorderColor = Color.Transparent,
                                    unfocusedBorderColor = Color.Transparent,
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent
                                ),
                                singleLine = true,
                                textStyle = TextStyle(
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Medium
                                ),
                                trailingIcon = {
                                    if (searchQuery.isNotEmpty()) {
                                        IconButton(onClick = { searchQuery = "" }) {
                                            Icon(Icons.Default.Clear, contentDescription = "Очистить", tint = colorScheme.onSurface.copy(alpha = 0.7f))
                                        }
                                    }
                                }
                            )
                        } else {
                            Text("Карты", color = colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 24.sp)
                        }
                    },
                    navigationIcon = {},
                    actions = {
                        if (selectionMode) {
                            IconButton(onClick = {
                                onDeleteCards(selectedCards.toList())
                                selectedCards = emptySet()
                                selectionMode = false
                            }) {
                                Icon(Icons.Default.Delete, contentDescription = "Удалить", tint = colorScheme.onSurface)
                            }
                        } else {
                            IconButton(onClick = { showSearch = !showSearch }) {
                                Icon(Icons.Default.Search, contentDescription = "Поиск", tint = colorScheme.onSurface)
                            }
                            Box {
                                IconButton(onClick = { showFilterMenu = true }) {
                                    Icon(Icons.Default.FilterAlt, contentDescription = "Фильтр", tint = colorScheme.onSurface)
                                }
                                DropdownMenu(
                                    expanded = showFilterMenu,
                                    onDismissRequest = { showFilterMenu = false },
                                    modifier = Modifier.background(colorScheme.surface)
                                ) {
                                    SortType.entries.forEach { sortType ->
                                        DropdownMenuItem(
                                            text = { 
                                                Text(
                                                    text = sortType.displayName,
                                                    color = if (currentSortType == sortType) colorScheme.primary else colorScheme.onSurface
                                                ) 
                                            },
                                            onClick = {
                                                onSortTypeChange(sortType)
                                                showFilterMenu = false
                                            },
                                            leadingIcon = {
                                                if (currentSortType == sortType) {
                                                    Icon(
                                                        Icons.Default.Check,
                                                        contentDescription = "Выбрано",
                                                        tint = colorScheme.primary
                                                    )
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                            IconButton(onClick = onSettingsClick) {
                                Icon(Icons.Filled.Settings, contentDescription = "Настройки", tint = colorScheme.onSurface)
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                )
            },
            floatingActionButton = {
                if (!selectionMode) {
                    FloatingActionButton(
                        onClick = onAddCard,
                        containerColor = colorScheme.primary,
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 12.dp)) {
                            Icon(
                                Icons.Filled.Add,
                                contentDescription = "Добавить карту",
                                tint = colorScheme.onPrimary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Добавить карту", color = colorScheme.onPrimary)
                        }
                    }
                }
            },
            content = { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .clickable(
                            interactionSource = remember { 
                                MutableInteractionSource() },
                            indication = null
                        ) { 
                            if (showSearch) {
                                showSearch = false
                                searchQuery = ""
                            }
                            if (selectionMode) {
                                selectionMode = false
                                selectedCards = emptySet()
                            }
                        }
                ) {
                    if (filteredCards.isEmpty()) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.align(Alignment.Center).offset(y = (-64).dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Wallet,
                                contentDescription = "Нет карт",
                                tint = colorScheme.onBackground.copy(alpha = 0.18f),
                                modifier = Modifier.size(72.dp)
                            )
                            Spacer(modifier = Modifier.height(20.dp))
                            Text(
                                text = if (searchQuery.isBlank()) {
                                    "Вы еще не добавили\nни одной карты"
                                } else {
                                    "Карты не найдены"
                                },
                                color = colorScheme.onBackground.copy(alpha = 0.7f),
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                lineHeight = 30.sp,
                                modifier = Modifier.padding(horizontal = 32.dp, vertical = 16.dp),
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            contentPadding = PaddingValues(start = 8.dp, top = 8.dp, end = 8.dp, bottom = 80.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            flingBehavior = ScrollableDefaults.flingBehavior(),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(filteredCards, key = { it.id }) { card ->
                                val isSelected = selectedCards.contains(card)
                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (card.coverAsset != null) Color.Transparent else Color(card.color),
                                        contentColor = if (card.coverAsset != null) Color.Unspecified else if (isColorDark(card.color)) Color.White else Color.Black
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        // Устанавливаем соотношение сторон 1.574 для отображения карт
                                        .aspectRatio(1.574f)
                                        .fillMaxWidth()
                                        .then(
                                            if (isSelected) Modifier
                                                .border(
                                                    width = 3.dp,
                                                    color = colorScheme.primary,
                                                    shape = RoundedCornerShape(12.dp)
                                                )
                                            else Modifier
                                        )
                                        .combinedClickable(
                                            onClick = {
                                                if (selectionMode) {
                                                    selectedCards = if (isSelected) selectedCards - card else selectedCards + card
                                                    if (selectedCards.isEmpty()) selectionMode = false
                                                } else {
                                                    onCardClick(card)
                                                }
                                            },
                                            onLongClick = {
                                                if (!selectionMode) {
                                                    selectionMode = true
                                                    selectedCards = setOf(card)
                                                }
                                            }
                                        )
                                ) {
                                    val context = LocalContext.current
                                    val frontPath = card.frontCoverPath
                                    key(frontPath) {
                                        Box(
                                            modifier = Modifier.fillMaxSize(),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            // Затемнение поверх цвета карточки, если выбрана
                                            if (isSelected) {
                                                Box(
                                                    modifier = Modifier
                                                        .matchParentSize()
                                                        .background(Color.Black.copy(alpha = 0.12f), shape = RoundedCornerShape(12.dp))
                                                )
                                            }
                                            val imageBitmap: ImageBitmap? = remember(frontPath to card.coverAsset) {
                                                try {
                                                    frontPath?.let {
                                                        val bmp = BitmapFactory.decodeFile(it)
                                                        if (bmp != null) return@remember bmp.asImageBitmap()
                                                    }
                                                    card.coverAsset?.let {
                                                        val assetManager = context.assets
                                                        val input = assetManager.open(it)
                                                        val bmp = BitmapFactory.decodeStream(input)
                                                        input.close()
                                                        return@remember bmp?.asImageBitmap()
                                                    }
                                                    null
                                                } catch (e: Exception) { null }
                                            }
                                            if (imageBitmap != null) {
                                                Image(
                                                    bitmap = imageBitmap,
                                                    contentDescription = null,
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier.fillMaxSize()
                                                )
                                            } else {
                                                Text(
                                                    text = card.name,
                                                    color = if (isColorDark(card.color)) Color.White else Color.Black,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 18.sp,
                                                    textAlign = TextAlign.Center,
                                                    maxLines = 2,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.padding(8.dp)
                                                )
                                            }
                                            if (isSelected) {
                                                Icon(
                                                    Icons.Default.Check,
                                                    contentDescription = "Выбрано",
                                                    tint = colorScheme.primary,
                                                    modifier = Modifier.align(Alignment.TopEnd).padding(6.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        )
    }
}

@Preview(showBackground = true)
@Composable
fun MainScreenPreview() {
    SeaCardTheme {
        MainScreen(cards = emptyList(), currentSortType = SortType.ADD_TIME, onAddCard = {}, onCardClick = {}, onSettingsClick = {}, onSortTypeChange = {}, onDeleteCards = {})
    }
}