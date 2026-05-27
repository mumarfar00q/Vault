package com.example

import android.content.Context
import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

// Helper to determine gradient aesthetic fallback background
internal fun getSearchAestheticGradientForFile(file: VaultFile): List<Color> {
    val hashValue = java.lang.Math.abs(file.id.hashCode())
    val palette = hashValue % 4
    return when (palette) {
        0 -> listOf(Color(0xFF2C3E50), Color(0xFFFD746C)) // Sunset Orange
        1 -> listOf(Color(0xFF0F2027), Color(0xFF203A43), Color(0xFF2C5364)) // Sea Teal
        2 -> listOf(Color(0xFF3A6073), Color(0xFF16222F)) // Cosmic Blue
        else -> listOf(Color(0xFF11998E), Color(0xFF38EF7D)) // Forest Mint
    }
}

// ==========================================
// 1. VAULT SEARCH SCREEN (Debounced Search)
// ==========================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultSearchScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
    onNavigateToViewer: (VaultFile) -> Unit
) {
    val context = LocalContext.current
    val isDark = viewModel.currentTheme.value == AppThemeMode.Dark
    val bgCol = if (isDark) Color(0xFF13131A) else Color(0xFFF2F2F7)
    val cardBg = if (isDark) Color(0xFF1C1C24) else Color.White
    val textCol = if (isDark) Color.White else Color(0xFF1C1C1E)
    val accentColor = Color(0xFFFF9F0A)

    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<VaultFile>>(emptyList()) }
    val recentQueries by viewModel.recentSearches

    val focusRequester = remember { FocusRequester() }

    // Init focus immediate
    LaunchedEffect(Unit) {
        viewModel.loadRecentSearches()
        focusRequester.requestFocus()
    }

    // Debounced real-time DB search
    LaunchedEffect(searchQuery) {
        if (searchQuery.isNotBlank()) {
            delay(300)
            // Perform actual database query
            val db = VaultDatabase.dbFlow.value
            if (db != null) {
                searchResults = db.vaultFileDao().searchFiles(searchQuery)
            }
            // Add search to recent queries list after 1s interval
            delay(700)
            viewModel.addRecentSearch(searchQuery)
        } else {
            searchResults = emptyList()
        }
    }

    // Filter Chips: All, Photos, Videos, Audio, Documents, Favorites
    val chips = listOf("All", "Photos", "Videos", "Audio", "Documents", "Favorites")
    var selectedChip by remember { mutableStateOf("All") }

    val filteredResults = remember(searchResults, selectedChip) {
        var list = searchResults
        when (selectedChip) {
            "Photos" -> list = list.filter { it.fileType.lowercase() == "photo" }
            "Videos" -> list = list.filter { it.fileType.lowercase() == "video" }
            "Audio" -> list = list.filter { it.fileType.lowercase() == "audio" }
            "Documents" -> list = list.filter { it.fileType.lowercase() == "document" }
            "Favorites" -> list = list.filter { it.isFavorite == 1 }
            else -> {}
        }
        list
    }

    Scaffold(
        containerColor = bgCol,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = cardBg),
                title = {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search encrypted files...", color = textCol.copy(alpha = 0.5f)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester)
                            .testTag("search_input_field"),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = accentColor,
                            unfocusedBorderColor = Color.Transparent,
                            focusedTextColor = textCol,
                            unfocusedTextColor = textCol
                        ),
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(imageVector = Icons.Default.Close, contentDescription = "Clear search", tint = textCol)
                                }
                            }
                        }
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back", tint = textCol)
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Filter chips below search bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                chips.forEach { chipName ->
                    val isSel = selectedChip == chipName
                    FilterChip(
                        selected = isSel,
                        onClick = { selectedChip = chipName },
                        label = { Text(chipName, color = if (isSel) Color.Black else textCol) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = accentColor,
                            containerColor = cardBg
                        )
                    )
                }
            }

            // Results / Empty states / Pre-searches
            if (searchQuery.isBlank()) {
                // Pre-search state: show "Recent searches" (last 5 queries stored in settings DB)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Recent searches",
                            color = textCol.copy(alpha = 0.6f),
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        if (recentQueries.isNotEmpty()) {
                            TextButton(onClick = { viewModel.clearRecentSearchHistory() }) {
                                Text("Clear", color = accentColor, fontSize = 12.sp)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    if (recentQueries.isEmpty()) {
                        Text(
                            text = "No recent searches.",
                            color = textCol.copy(alpha = 0.4f),
                            fontSize = 13.sp
                        )
                    } else {
                        recentQueries.forEach { query ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { searchQuery = query }
                                    .padding(vertical = 12.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(imageVector = Icons.Default.Refresh, contentDescription = null, tint = textCol.copy(alpha = 0.4f), modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(text = query, color = textCol, fontSize = 15.sp)
                            }
                        }
                    }
                }
            } else if (filteredResults.isEmpty()) {
                // No results found
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(imageVector = Icons.Default.SentimentDissatisfied, contentDescription = null, tint = textCol.copy(alpha = 0.25f), modifier = Modifier.size(64.dp))
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Nothing found for '$searchQuery'",
                            color = textCol.copy(alpha = 0.5f),
                            fontSize = 15.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 32.dp)
                        )
                    }
                }
            } else {
                // Group results by category / fileType
                val groups = remember(filteredResults) {
                    filteredResults.groupBy { it.fileType.uppercase() }
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    groups.forEach { (typeLabel, filesInGroup) ->
                        item {
                            Text(
                                text = typeLabel,
                                color = accentColor,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                        items(filesInGroup) { file ->
                            Card(
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = cardBg),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onNavigateToViewer(file) }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Thumbnail / Icon representation
                                    Box(
                                        modifier = Modifier
                                            .size(50.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color.Black.copy(alpha = 0.1f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (file.thumbnailPath != null && File(file.thumbnailPath).exists()) {
                                            val bmp = BitmapFactory.decodeFile(file.thumbnailPath)
                                            if (bmp != null) {
                                                Image(
                                                    bitmap = bmp.asImageBitmap(),
                                                    contentDescription = null,
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier.fillMaxSize()
                                                )
                                            } else {
                                                Icon(imageVector = Icons.Default.InsertDriveFile, contentDescription = null, tint = accentColor)
                                            }
                                        } else {
                                            Icon(
                                                imageVector = when (file.fileType.lowercase()) {
                                                    "photo" -> Icons.Default.Face
                                                    "video" -> Icons.Default.PlayArrow
                                                    "audio" -> Icons.Default.VolumeUp
                                                    "note" -> Icons.Default.Edit
                                                    else -> Icons.Default.InsertDriveFile
                                                },
                                                contentDescription = null,
                                                tint = accentColor
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(16.dp))

                                    // Details
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = file.originalName,
                                            color = textCol,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        val formattedDate = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date(file.addedAt))
                                        Text(
                                            text = formattedDate,
                                            color = textCol.copy(alpha = 0.5f),
                                            fontSize = 12.sp
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(8.dp))

                                    // Type badge
                                    AssistChip(
                                        onClick = {},
                                        label = { Text(file.fileType.uppercase(), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = accentColor) },
                                        enabled = false,
                                        colors = AssistChipDefaults.assistChipColors(
                                            disabledContainerColor = accentColor.copy(alpha = 0.1f),
                                            disabledLabelColor = accentColor
                                        )
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

// ==========================================
// 2. SECURE NOTES SCREEN
// ==========================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultNotesScreen(
    viewModel: MainViewModel,
    id: String?,
    onBack: () -> Unit,
    onNavigateToEditor: (String) -> Unit,
    onNavigateToHome: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    val context = LocalContext.current
    val notes by viewModel.allNotes.collectAsState(initial = emptyList())
    val isDark = viewModel.currentTheme.value == AppThemeMode.Dark
    val bgCol = if (isDark) Color(0xFF13131A) else Color(0xFFF2F2F7)
    val cardBg = if (isDark) Color(0xFF1C1C24) else Color.White
    val textCol = if (isDark) Color.White else Color(0xFF1C1C1E)
    val accentColor = Color(0xFFFF9F0A)

    var showRenameDialog by remember { mutableStateOf<VaultFile?>(null) }
    var renameInput by remember { mutableStateOf("") }

    Scaffold(
        containerColor = bgCol,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = cardBg),
                title = { Text("Secure Notes Office", fontWeight = FontWeight.Bold, color = textCol) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back", tint = textCol)
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { onNavigateToEditor("new") },
                containerColor = accentColor,
                contentColor = Color.White
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Add Secure Note")
            }
        },
        bottomBar = {
            NavigationBar(
                containerColor = if (isDark) Color(0xFF15151F) else Color(0xFFF2F2F7),
                tonalElevation = 8.dp
            ) {
                NavigationBarItem(
                    selected = false,
                    onClick = onNavigateToHome,
                    icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
                    label = { Text("Home", fontSize = 11.sp) },
                    colors = NavigationBarItemDefaults.colors(unselectedIconColor = Color.Gray)
                )
                NavigationBarItem(
                    selected = true,
                    onClick = {},
                    icon = { Icon(Icons.Default.Edit, contentDescription = "Notes", tint = accentColor) },
                    label = { Text("Notes", fontSize = 11.sp, color = accentColor) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = accentColor,
                        indicatorColor = accentColor.copy(alpha = 0.12f)
                    )
                )
                NavigationBarItem(
                    selected = false,
                    onClick = onNavigateToSettings,
                    icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                    label = { Text("Settings", fontSize = 11.sp) },
                    colors = NavigationBarItemDefaults.colors(unselectedIconColor = Color.Gray)
                )
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (notes.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(imageVector = Icons.Default.EditNote, contentDescription = null, tint = textCol.copy(alpha = 0.2f), modifier = Modifier.size(80.dp))
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("No secure notes saved.\nPress the button to write your first.", color = textCol.copy(alpha = 0.5f), textAlign = TextAlign.Center)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(notes) { note ->
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = cardBg),
                            modifier = Modifier
                                .fillMaxWidth()
                                .pointerInput(note.id) {
                                    detectTapGestures(
                                        onTap = { onNavigateToEditor(note.id) },
                                        onLongPress = {
                                            renameInput = note.originalName
                                            showRenameDialog = note
                                        }
                                    )
                                }
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = note.originalName,
                                        color = textCol,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    IconButton(
                                        onClick = {
                                            viewModel.deleteFileById(note.id)
                                            Toast.makeText(context, "Note permanently deleted", Toast.LENGTH_SHORT).show()
                                        },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFFF453A), modifier = Modifier.size(16.dp))
                                    }
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = note.tags ?: "",
                                    color = textCol.copy(alpha = 0.6f),
                                    fontSize = 13.sp,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                val formattedDate = SimpleDateFormat("MMM dd, yyyy h:mm a", Locale.getDefault()).format(Date(note.addedAt))
                                Text(
                                    text = formattedDate,
                                    color = textCol.copy(alpha = 0.4f),
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        // Rename note dialog
        if (showRenameDialog != null) {
            val noteToRename = showRenameDialog!!
            AlertDialog(
                onDismissRequest = { showRenameDialog = null },
                title = { Text("Rename Secure Note", color = textCol, fontWeight = FontWeight.Bold) },
                text = {
                    OutlinedTextField(
                        value = renameInput,
                        onValueChange = { renameInput = it },
                        label = { Text("Note Title", color = textCol.copy(alpha = 0.6f)) },
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = accentColor)
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (renameInput.isNotBlank()) {
                                viewModel.renameNote(noteToRename.id, renameInput)
                                showRenameDialog = null
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = accentColor)
                    ) {
                        Text("Save", color = Color.White)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showRenameDialog = null }) {
                        Text("Cancel", color = Color.Gray)
                    }
                },
                containerColor = cardBg
            )
        }
    }
}

// ==========================================
// 3. SECURE NOTE EDITOR SCREEN (Auto-save)
// ==========================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultNoteEditorScreen(
    viewModel: MainViewModel,
    noteId: String?,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val isDark = viewModel.currentTheme.value == AppThemeMode.Dark
    val bgCol = if (isDark) Color(0xFF13131A) else Color(0xFFF2F2F7)
    val cardBg = if (isDark) Color(0xFF1C1C24) else Color.White
    val textCol = if (isDark) Color.White else Color(0xFF1C1C1E)
    val accentColor = Color(0xFFFF9F0A)

    var noteTitle by remember { mutableStateOf("") }
    var noteContent by remember { mutableStateOf("") }
    var originalNoteFile by remember { mutableStateOf<VaultFile?>(null) }
    var isLoaded by remember { mutableStateOf(false) }

    // Load existing note details if noteId is not null
    LaunchedEffect(noteId) {
        if (noteId != null) {
            val noteFile = VaultDatabase.getFileById(noteId)
            if (noteFile != null) {
                originalNoteFile = noteFile
                noteTitle = noteFile.originalName
                noteContent = viewModel.loadNoteContent(noteFile.storedPath)
            }
        }
        isLoaded = true
    }

    // Auto-save: triggered on title or content change after short delay
    LaunchedEffect(noteTitle, noteContent) {
        if (isLoaded && (noteTitle.isNotBlank() || noteContent.isNotBlank())) {
            delay(1000) // Trigger autosaver after 1s stillness
            viewModel.createOrUpdateNote(
                context = context,
                noteId = noteId ?: originalNoteFile?.id,
                title = noteTitle,
                content = noteContent
            )
        }
    }

    Scaffold(
        containerColor = bgCol,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = cardBg),
                navigationIcon = {
                    IconButton(onClick = {
                        // Explicit final save before leaving
                        if (noteTitle.isNotBlank() || noteContent.isNotBlank()) {
                            viewModel.createOrUpdateNote(
                                context = context,
                                noteId = noteId ?: originalNoteFile?.id,
                                title = noteTitle,
                                content = noteContent
                            )
                        }
                        onBack()
                    }) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Save and Back", tint = textCol)
                    }
                },
                title = {
                    BasicTextField(
                        value = noteTitle,
                        onValueChange = { noteTitle = it },
                        textStyle = MaterialTheme.typography.titleMedium.copy(
                            color = textCol,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("note_title_input"),
                        singleLine = true,
                        decorationBox = { innerTextField ->
                            if (noteTitle.isEmpty()) {
                                Text("Note Title", color = textCol.copy(alpha = 0.4f), fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            }
                            innerTextField()
                        }
                    )
                },
                actions = {
                    IconButton(onClick = {
                        viewModel.createOrUpdateNote(
                            context = context,
                            noteId = noteId ?: originalNoteFile?.id,
                            title = noteTitle,
                            content = noteContent
                        )
                        Toast.makeText(context, "Draft Saved Automatically", Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(imageVector = Icons.Default.Check, contentDescription = "Manual Save", tint = accentColor)
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            BasicTextField(
                value = noteContent,
                onValueChange = { noteContent = it },
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = textCol,
                    fontSize = 16.sp,
                    lineHeight = 22.sp
                ),
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("note_content_input"),
                decorationBox = { innerTextField ->
                    if (noteContent.isEmpty()) {
                        Text("Start writing your secure note here...", color = textCol.copy(alpha = 0.35f), fontSize = 16.sp)
                    }
                    innerTextField()
                }
            )
        }
    }
}

// ==========================================
// 4. SECURE FULLSCREEN IMMERSIVE SLIDESHOW
// ==========================================
@OptIn(ExperimentalFoundationApi::class, ExperimentalAnimationApi::class)
@Composable
fun SecureSlideshowDialog(
    photos: List<VaultFile>,
    initialIndex: Int,
    onDismiss: () -> Unit
) {
    if (photos.isEmpty()) return

    val context = LocalContext.current
    var currentIndex by remember { mutableStateOf(initialIndex.coerceIn(photos.indices)) }
    var isPaused by remember { mutableStateOf(false) }
    var speedMs by remember { mutableStateOf(3000L) }
    var showSpeedSelector by remember { mutableStateOf(false) }
    var firstTapHappened by remember { mutableStateOf(false) }
    var overlayVisible by remember { mutableStateOf(true) }

    // Hide overlay metadata after delay
    LaunchedEffect(overlayVisible) {
        if (overlayVisible) {
            delay(3000)
            overlayVisible = false
        }
    }

    // Auto advance controller
    LaunchedEffect(currentIndex, isPaused, speedMs) {
        if (!isPaused) {
            delay(speedMs)
            currentIndex = (currentIndex + 1) % photos.size
        }
    }

    // Decrypted Image bitmap loader
    val currentPhoto = photos[currentIndex]
    var decryptedBitmap by remember(currentIndex) { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }

    LaunchedEffect(currentIndex) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                if (!currentPhoto.storedPath.startsWith("sim_") && !currentPhoto.storedPath.startsWith("sample_")) {
                    val bytes = FileStorageManager.readFileDecrypted(currentPhoto.storedPath)
                    val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    bmp?.asImageBitmap()?.let {
                        decryptedBitmap = it
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // Immersive display box
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        isPaused = !isPaused
                        if (!firstTapHappened) {
                            showSpeedSelector = true
                            firstTapHappened = true
                        }
                        overlayVisible = true
                    }
                )
            }
    ) {
        // Crossfade display frame
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            if (decryptedBitmap != null) {
                Image(
                    bitmap = decryptedBitmap!!,
                    contentDescription = currentPhoto.originalName,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            } else {
                // Background Gradient Fallback
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.linearGradient(colors = getSearchAestheticGradientForFile(currentPhoto))
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(currentPhoto.originalName, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                }
            }
        }

        // Horizontal Swiping manual trigger Box
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = {},
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            if (dragAmount < -15) { // Next
                                currentIndex = (currentIndex + 1) % photos.size
                            } else if (dragAmount > 15) { // Prev
                                currentIndex = if (currentIndex - 1 < 0) photos.size - 1 else currentIndex - 1
                            }
                        }
                    )
                }
        )

        // Exit [X] button top-left
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(16.dp)
        ) {
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                    .size(44.dp)
            ) {
                Icon(imageVector = Icons.Default.Close, contentDescription = "Exit Slideshow", tint = Color.White)
            }
        }

        // Overlay Metadata panels
        AnimatedVisibility(
            visible = overlayVisible,
            enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                        )
                    )
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(24.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = currentPhoto.originalName,
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    val formattedDate = SimpleDateFormat("MMM dd, yyyy h:mm a", Locale.getDefault()).format(Date(currentPhoto.addedAt))
                    Text(
                        text = formattedDate,
                        color = Color.LightGray,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Icon(
                            imageVector = if (isPaused) Icons.Default.PlayArrow else Icons.Default.Refresh,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = if (isPaused) "Paused" else "Playing (${speedMs / 1000}s)",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )

                        Divider(
                            color = Color.White.copy(alpha = 0.3f),
                            modifier = Modifier
                                .height(16.dp)
                                .width(1.dp)
                        )

                        TextButton(onClick = { showSpeedSelector = true }) {
                            Text("Set Pace", color = Color(0xFFFF9F0A), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Select Duration popup options
        if (showSpeedSelector) {
            AlertDialog(
                onDismissRequest = { showSpeedSelector = false },
                title = { Text("Slideshow Pace Menu", color = Color.White, fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        listOf(2000L to "2 Seconds", 3000L to "3 Seconds", 5000L to "5 Seconds", 10000L to "10 Seconds").forEach { (ms, durationTitle) ->
                            val currentChoice = speedMs == ms
                            Button(
                                onClick = {
                                    speedMs = ms
                                    isPaused = false
                                    showSpeedSelector = false
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (currentChoice) Color(0xFFFF9F0A) else Color(0xFF2C2C35)
                                ),
                                modifier = Modifier.fillMaxWidth()
                                    .height(48.dp)
                            ) {
                                Text(durationTitle, color = Color.White, fontWeight = if (currentChoice) FontWeight.Bold else FontWeight.Normal)
                            }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { showSpeedSelector = false }) {
                        Text("Dismiss", color = Color.LightGray)
                    }
                },
                containerColor = Color(0xFF13131A)
            )
        }
    }
}
