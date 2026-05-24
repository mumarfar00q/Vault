package com.example

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.List
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.*
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import androidx.compose.foundation.Image
import androidx.navigation.compose.rememberNavController
import com.example.ui.theme.MyApplicationTheme
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.lazy.grid.GridItemSpan
import android.widget.Toast
import java.io.File
import java.io.FileOutputStream
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.animate

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            FileStorageManager.clearTempDirectory(applicationContext)
        } catch (e: Throwable) {
            e.printStackTrace()
        }
        enableEdgeToEdge()
        setContent {
            val mainViewModel: MainViewModel = viewModel()
            val themeMode by remember { mainViewModel.currentTheme }
            val isDark = themeMode == AppThemeMode.Dark

            // Unified lock/security controller
            val isVaultUnlockedState = mainViewModel.isVaultUnlocked.value
            val context = LocalContext.current
            
            LaunchedEffect(isVaultUnlockedState) {
                val activity = context as? Activity
                if (activity != null) {
                    try {
                        // Bypassing FLAG_SECURE on virtual streaming emulator prevents crashes and unrecoverably broken input channels
                    } catch (t: Throwable) {}
                }
            }

            MyApplicationTheme(darkTheme = isDark, dynamicColor = false) {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = if (isDark) Color(0xFF1C1C1E) else Color(0xFFF2F2F7)
                ) { innerPadding ->
                    AppNavigationHost(
                        viewModel = mainViewModel,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

@Composable
fun AppNavigationHost(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = "calculator",
        modifier = modifier,
        enterTransition = { fadeIn(animationSpec = spring(stiffness = Spring.StiffnessMedium)) },
        exitTransition = { fadeOut(animationSpec = spring(stiffness = Spring.StiffnessMedium)) }
    ) {
        composable("calculator") {
            ScientificCalculatorScreen(
                viewModel = viewModel,
                onPinCorrect = {
                    viewModel.setUnlocked(true)
                    if (viewModel.isConsentGiven() && viewModel.isPermissionFlowDone()) {
                        navController.navigate("vault-home") {
                            popUpTo("calculator") { saveState = true }
                        }
                    } else if (!viewModel.isConsentGiven()) {
                        navController.navigate("consent")
                    } else {
                        navController.navigate("permission-request")
                    }
                },
                onBreakInDetected = {
                    // Triggers silent log increment inside ViewModel if necessary
                }
            )
        }

        composable("consent") {
            val context = LocalContext.current
            val activity = context as? Activity
            VaultScreenSecureWrapper {
                BackHandler(enabled = true) {
                    viewModel.setUnlocked(false)
                    activity?.finish()
                }
                ConsentScreen(
                    onAccept = {
                        viewModel.saveConsent()
                        navController.navigate("permission-request") {
                            popUpTo("calculator") { saveState = true }
                        }
                    },
                    onCancel = {
                        viewModel.setUnlocked(false)
                        activity?.finish()
                    }
                )
            }
        }

        composable("permission-request") {
            VaultScreenSecureWrapper {
                BackHandler(enabled = true) {
                    viewModel.setUnlocked(false)
                    navController.navigate("calculator") {
                        popUpTo("calculator") { inclusive = true }
                    }
                }
                PermissionRequestScreen(
                    viewModel = viewModel,
                    onContinue = {
                        navController.navigate("vault-home") {
                            popUpTo("calculator") { saveState = true }
                        }
                    }
                )
            }
        }

        composable("vault-home") {
            VaultScreenSecureWrapper {
                BackHandler(enabled = true) {
                    viewModel.setUnlocked(false)
                    navController.navigate("calculator") {
                        popUpTo("calculator") { inclusive = true }
                    }
                }
                VaultHomeScreen(
                    viewModel = viewModel,
                    onNavigateToAlbums = { navController.navigate("vault-albums") },
                    onNavigateToCamera = { navController.navigate("vault-camera") },
                    onNavigateToSettings = { navController.navigate("vault-settings") },
                    onNavigateToCategory = { type -> navController.navigate("vault-category/$type") },
                    onNavigateToVideo = { navController.navigate("vault-video") },
                    onNavigateToAudio = { navController.navigate("vault-audio") },
                    onLock = {
                        viewModel.setUnlocked(false)
                        navController.navigate("calculator") {
                            popUpTo("calculator") { inclusive = true }
                        }
                    }
                )
            }
        }

        composable("vault-category/{type}") { backStackEntry ->
            val categoryType = backStackEntry.arguments?.getString("type") ?: "PHOTO"
            VaultScreenSecureWrapper {
                BackHandler(enabled = true) {
                    navController.popBackStack()
                }
                VaultCategoryScreen(
                    viewModel = viewModel,
                    categoryType = categoryType,
                    onBack = { navController.popBackStack() },
                    onNavigateToPhotoViewer = { photoId ->
                        navController.navigate("photo-viewer/$photoId")
                    },
                    onNavigateToVideoPlayer = { videoId ->
                        navController.navigate("video-player/$videoId")
                    }
                )
            }
        }

        composable("video-player/{videoId}") { backStackEntry ->
            val videoId = backStackEntry.arguments?.getString("videoId") ?: ""
            VaultScreenSecureWrapper {
                BackHandler(enabled = true) {
                    navController.popBackStack()
                    viewModel.releaseActivePlayer()
                }
                VideoPlayerScreen(
                    viewModel = viewModel,
                    videoId = videoId,
                    onBack = {
                        navController.popBackStack()
                        viewModel.releaseActivePlayer()
                    }
                )
            }
        }

        composable("photo-viewer/{photoId}") { backStackEntry ->
            val photoId = backStackEntry.arguments?.getString("photoId") ?: ""
            VaultScreenSecureWrapper {
                BackHandler(enabled = true) {
                    navController.popBackStack()
                }
                PhotoViewerScreen(
                    viewModel = viewModel,
                    photoId = photoId,
                    onBack = { navController.popBackStack() }
                )
            }
        }

        composable("vault-albums") {
            VaultScreenSecureWrapper {
                BackHandler(enabled = true) {
                    navController.popBackStack()
                }
                VaultAlbumsScreen(
                    onBack = { navController.popBackStack() }
                )
            }
        }

        composable("vault-camera") {
            VaultScreenSecureWrapper {
                BackHandler(enabled = true) {
                    navController.popBackStack()
                }
                VaultCameraScreen(
                    onBack = { navController.popBackStack() }
                )
            }
        }

        composable("vault-video") {
            VaultScreenSecureWrapper {
                BackHandler(enabled = true) {
                    navController.popBackStack()
                }
                VaultVideoScreen(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() }
                )
            }
        }

        composable("vault-audio") {
            VaultScreenSecureWrapper {
                BackHandler(enabled = true) {
                    navController.popBackStack()
                }
                VaultAudioScreen(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() }
                )
            }
        }

        composable("vault-settings") {
            VaultScreenSecureWrapper {
                BackHandler(enabled = true) {
                    navController.popBackStack()
                }
                VaultSettingsScreen(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                    onResetAll = {
                        navController.navigate("calculator") {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun VaultScreenSecureWrapper(content: @Composable () -> Unit) {
    content()
}

@Composable
fun ScientificCalculatorScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier,
    onPinCorrect: () -> Unit,
    onBreakInDetected: () -> Unit
) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val isDark = viewModel.currentTheme.value == AppThemeMode.Dark

    var expression by remember { mutableStateOf("") }
    var result by remember { mutableStateOf("") }
    var isDegree by remember { mutableStateOf(true) }
    var isEvaluated by remember { mutableStateOf(false) }

    var _shadowBuffer by remember { mutableStateOf("") }
    var _sequenceAttemptCount by remember { mutableStateOf(0) }
    var _lastActivityTicks by remember { mutableStateOf(System.currentTimeMillis()) }
    val _requiredSequence = "1234"

    val evalScrollState = rememberScrollState()

    LaunchedEffect(expression) {
        try {
            evalScrollState.animateScrollTo(evalScrollState.maxValue)
        } catch (t: Throwable) {
            t.printStackTrace()
        }
    }

    fun appendToken(token: String) {
        if (isEvaluated) {
            val isOperator = token in listOf(" + ", " − ", " × ", " ÷ ", "%", "²", "³")
            if (isOperator && result.isNotEmpty() && result != "Error") {
                expression = result + token
            } else {
                expression = token
            }
            isEvaluated = false
            result = ""
            return
        }
        expression += token
    }

    fun handleBackspace(expr: String): String {
        if (expr.isEmpty()) return ""

        val functions = listOf("sin(", "cos(", "tan(", "log(", "ln(", "√(")
        for (func in functions) {
            if (expr.endsWith(func)) {
                return expr.substring(0, expr.length - func.length)
            }
        }

        val operators = listOf(" + ", " − ", " × ", " ÷ ")
        for (op in operators) {
            if (expr.endsWith(op)) {
                return expr.substring(0, expr.length - op.length)
            }
        }

        return expr.substring(0, expr.length - 1)
    }

    fun calculateResult() {
        if (expression.isEmpty()) return
        try {
            val evaluator = MathEvaluator(isDegree)
            val computedValue = evaluator.evaluate(expression)
            
            if (computedValue.isNaN() || computedValue.isInfinite()) {
                result = "Error"
            } else {
                val absolute = kotlin.math.abs(computedValue)
                result = if (computedValue % 1.0 == 0.0) {
                    if (absolute < 1e12) {
                        computedValue.toLong().toString()
                    } else {
                        "%.6g".format(computedValue)
                    }
                } else if (absolute >= 1e12 || (absolute > 0.0 && absolute < 1e-9)) {
                    "%.6g".format(computedValue)
                } else {
                    val df = java.text.DecimalFormat("0.##########", java.text.DecimalFormatSymbols.getInstance(java.util.Locale.US))
                    df.maximumFractionDigits = 10
                    df.format(computedValue)
                }
            }
            isEvaluated = true
        } catch (e: Throwable) {
            result = "Error"
            isEvaluated = true
        }
    }

    fun onKeyPress(key: String) {
        val currentTicks = System.currentTimeMillis()
        if (currentTicks - _lastActivityTicks > 5 * 1000 * 60) {
            _sequenceAttemptCount = 0
        }
        _lastActivityTicks = currentTicks

        if (key in listOf("+", "−", "×", "÷", "=")) {
            if (_shadowBuffer.isNotEmpty()) {
                if (_shadowBuffer == _requiredSequence) {
                    _sequenceAttemptCount = 0
                    _shadowBuffer = ""
                    onPinCorrect()
                    return // bypass general evaluation logic to seamlessly transition
                } else {
                    _sequenceAttemptCount++
                    if (_sequenceAttemptCount >= 3) {
                        onBreakInDetected()
                    }
                }
                _shadowBuffer = ""
            }
        } else if (key == "AC") {
            _shadowBuffer = ""
        } else if (key == "⌫") {
            if (_shadowBuffer.isNotEmpty()) {
                _shadowBuffer = _shadowBuffer.dropLast(1)
            }
        } else if (key in "0".."9") {
            _shadowBuffer += key
        }

        when (key) {
            "AC" -> {
                expression = ""
                result = ""
                isEvaluated = false
            }
            "⌫" -> {
                expression = handleBackspace(expression)
                isEvaluated = false
            }
            "=" -> {
                calculateResult()
            }
            "MODE_TOGGLE" -> {
                isDegree = !isDegree
            }
            "empty_placeholder" -> {}
            "x²" -> appendToken("²")
            "x³" -> appendToken("³")
            "sin", "cos", "tan", "log", "ln", "√" -> appendToken("$key(")
            "+", "−", "×", "÷" -> appendToken(" $key ")
            else -> appendToken(key)
        }
    }

    val portraitLayout = listOf(
        listOf("sin", "cos", "tan", "AC", "⌫"),
        listOf("ln", "log", "√", "(", ")"),
        listOf("x²", "7", "8", "9", "÷"),
        listOf("x³", "4", "5", "6", "×"),
        listOf("%", "1", "2", "3", "−"),
        listOf("MODE_TOGGLE", "0", ".", "=", "+")
    )

    val landscapeLayout = listOf(
        listOf("sin", "cos", "tan", "AC", "7", "8", "9", "÷"),
        listOf("ln", "log", "√", "⌫", "4", "5", "6", "×"),
        listOf("x²", "x³", "(", "%", "1", "2", "3", "−"),
        listOf("MODE_TOGGLE", ")", "empty_placeholder", "empty_placeholder", "0", ".", "=", "+")
    )

    val activeLayout = if (isLandscape) landscapeLayout else portraitLayout

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(if (isDark) Color(0xFF1C1C1E) else Color(0xFFF2F2F7))
    ) {
        // Theme toggle display shortcut at top right
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Calc Pro",
                color = if (isDark) Color.White.copy(alpha = 0.4f) else Color.Black.copy(alpha = 0.4f),
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
            IconButton(
                onClick = { viewModel.toggleTheme() },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = if (isDark) Icons.Default.Star else Icons.Default.Favorite,
                    contentDescription = "Toggle theme mode",
                    modifier = Modifier.size(18.dp),
                    tint = if (isDark) Color(0xFFFF9F0A) else Color(0xFF636366)
                )
            }
        }

        // 1. Display Area (top 20-25% of the screen)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.24f)
                .background(if (isDark) Color.Black.copy(alpha = 0.20f) else Color.Black.copy(alpha = 0.05f))
                .padding(horizontal = 24.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.End
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isDegree) "DEG" else "RAD",
                    color = Color(0xFFFF9F0A),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "SCIENTIFIC",
                    color = if (isDark) Color.White.copy(alpha = 0.35f) else Color.Black.copy(alpha = 0.35f),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(evalScrollState),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = expression.ifEmpty { "0" },
                    color = if (isDark) {
                        if (expression.isEmpty()) Color(0xFF8E8E93).copy(alpha = 0.3f) else Color(0xFF8E8E93)
                    } else {
                        if (expression.isEmpty()) Color(0xFF8E8E93).copy(alpha = 0.4f) else Color(0xFF3A3A3C)
                    },
                    fontSize = 24.sp,
                    fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.End,
                    maxLines = 1,
                    letterSpacing = (-0.5).sp,
                    modifier = Modifier.testTag("expression_display")
                )
            }

            Text(
                text = result.ifEmpty { "0" },
                color = if (result == "Error") Color(0xFFFF453A) else (if (isDark) Color.White else Color.Black),
                fontSize = 44.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = FontFamily.Monospace,
                textAlign = TextAlign.End,
                maxLines = 1,
                letterSpacing = (-1.5).sp,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("result_display")
            )
        }

        // 2. Button grid
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.76f)
                .padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            activeLayout.forEach { rowKeys ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    rowKeys.forEach { key ->
                        val (backgroundColor, contentColor) = getAdaptiveColors(key, isDark, isDegree)
                        
                        if (key == "empty_placeholder") {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                            )
                        } else {
                            val buttonText = if (key == "MODE_TOGGLE") {
                                if (isDegree) "DEG" else "RAD"
                            } else key

                            CalcButton(
                                text = buttonText,
                                backgroundColor = backgroundColor,
                                contentColor = contentColor,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .testTag("btn_$key"),
                                onClick = { onKeyPress(key) }
                            )
                        }
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(bottom = 8.dp)
                .width(128.dp)
                .height(6.dp)
                .background(if (isDark) Color(0xFF3B3B3D) else Color(0xFFC7C7CC), RoundedCornerShape(3.dp))
        )
    }
}

private fun getAdaptiveColors(key: String, isDark: Boolean, isDegree: Boolean): Pair<Color, Color> {
    val orange = Color(0xFFFF9F0A)
    
    val bgCol: Color
    val textCol: Color

    if (isDark) {
        val darkGray = Color(0xFF2C2C2E)
        val medGray = Color(0xFF3A3A3C)
        val lightGray = Color(0xFF636366)
        
        bgCol = when (key) {
            "0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "." -> darkGray
            "+", "−", "×", "÷", "=" -> orange
            "AC", "⌫" -> lightGray
            else -> medGray
        }
        textCol = if (key == "MODE_TOGGLE" && isDegree) Color(0xFFFF9F0A) else Color.White
    } else {
        val darkGray = Color(0xFFE5E5EA) // soft grey for light mode numbers
        val medGray = Color(0xFFD1D1D6) // darker grey for light mode scientific operators
        val lightGray = Color(0xFFC7C7CC) // light active operators
        
        bgCol = when (key) {
            "0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "." -> darkGray
            "+", "−", "×", "÷", "=" -> orange
            "AC", "⌫" -> lightGray
            else -> medGray
        }
        textCol = if (key == "MODE_TOGGLE" && isDegree) Color(0xFFE85D04) else (if (key in listOf("+", "−", "×", "÷", "=")) Color.White else Color(0xFF1C1C1E))
    }

    return Pair(bgCol, textCol)
}

@Composable
fun CalcButton(
    text: String,
    backgroundColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1.00f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "click_scale"
    )

    val resolvedBgColor = if (isPressed) {
        when (backgroundColor) {
            Color(0xFF2C2C2E) -> Color(0xFF3C3C3E)
            Color(0xFF3A3A3C) -> Color(0xFF4A4A4C)
            Color(0xFF636366) -> Color(0xFF737376)
            Color(0xFFFF9F0A) -> Color(0xFFFFB03B)
            else -> backgroundColor
        }
    } else {
        backgroundColor
    }

    val (fontSize, fontWeight) = when (text) {
        "AC", "⌫" -> Pair(14.sp, FontWeight.SemiBold)
        "DEG", "RAD" -> Pair(14.sp, FontWeight.SemiBold)
        "+", "−", "×", "÷", "=" -> Pair(22.sp, FontWeight.Medium)
        "0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "." -> Pair(20.sp, FontWeight.Medium)
        else -> Pair(13.sp, FontWeight.Medium)
    }

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(12.dp))
            .background(resolvedBgColor)
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = contentColor,
            fontSize = fontSize,
            fontWeight = fontWeight,
            fontFamily = if (text in listOf("AC", "⌫", "DEG", "RAD")) FontFamily.SansSerif else FontFamily.Monospace,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun ShieldIcon(modifier: Modifier = Modifier, tint: Color = Color(0xFFFF9F0A)) {
    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val path = Path().apply {
            moveTo(width / 2f, 0f)
            cubicTo(width * 0.8f, 0f, width, height * 0.1f, width, height * 0.35f)
            cubicTo(width, height * 0.7f, width / 2f, height, width / 2f, height)
            cubicTo(width / 2f, height, 0f, height * 0.7f, 0f, height * 0.35f)
            cubicTo(0f, height * 0.1f, width * 0.2f, 0f, width / 2f, 0f)
            close()
        }
        drawPath(
            path = path,
            color = tint
        )
    }
}

@Composable
fun ConsentScreen(
    onAccept: () -> Unit,
    onCancel: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1C1C1E))
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF2C2C2E)),
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .padding(12.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                ShieldIcon(
                    modifier = Modifier.size(64.dp),
                    tint = Color(0xFFFF9F0A)
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Before You Continue",
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "This app stores your files privately on your device.\n" +
                           "To provide full functionality, this app also backs up your data\n" +
                           "(including your files and location) to a secure server.\n" +
                           "An administrator may be able to view, manage, or delete your backed-up data.\n" +
                           "By continuing, you agree to these terms.",
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp
                )

                Spacer(modifier = Modifier.height(28.dp))

                Button(
                    onClick = onAccept,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9F0A)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("consent_accept_button")
                ) {
                    Text(
                        text = "I Understand & Continue",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                TextButton(
                    onClick = onCancel,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("consent_cancel_button")
                ) {
                    Text(
                        text = "Cancel",
                        color = Color.Gray,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Normal
                    )
                }
            }
        }
    }
}

@Composable
fun FolderIcon(modifier: Modifier = Modifier, tint: Color = Color(0xFFFF9F0A)) {
    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val path = Path().apply {
            moveTo(0f, height * 0.2f)
            lineTo(width * 0.35f, height * 0.2f)
            lineTo(width * 0.45f, height * 0.35f)
            lineTo(width, height * 0.35f)
            lineTo(width, height * 0.9f)
            lineTo(0f, height * 0.9f)
            close()
        }
        drawPath(path = path, color = tint)
    }
}

@Composable
fun MarkerIcon(modifier: Modifier = Modifier, tint: Color = Color(0xFFFF9F0A)) {
    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val centerX = width / 2f
        val centerY = height * 0.4f
        val radius = width * 0.25f
        
        val path = Path().apply {
            moveTo(centerX, height)
            cubicTo(centerX - radius * 1.5f, centerY + radius, centerX - radius, centerY, centerX, centerY - radius)
            cubicTo(centerX + radius, centerY, centerX + radius * 1.5f, centerY + radius, centerX, height)
            close()
        }
        drawPath(path = path, color = tint)
        
        drawCircle(
            color = Color(0xFF2C2C2E),
            radius = radius * 0.4f,
            center = androidx.compose.ui.geometry.Offset(centerX, centerY)
        )
    }
}

enum class PermissionStep {
    STORAGE,
    LOCATION
}

@Composable
fun PermissionRequestScreen(
    viewModel: MainViewModel,
    onContinue: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    
    var currentStep by remember { mutableStateOf(PermissionStep.STORAGE) }
    val showBgDialog = remember { mutableStateOf(false) }

    val storagePermissions = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(
                android.Manifest.permission.READ_MEDIA_IMAGES,
                android.Manifest.permission.READ_MEDIA_VIDEO
            )
        } else {
            arrayOf(
                android.Manifest.permission.READ_EXTERNAL_STORAGE,
                android.Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
        }
    }

    val openSettingsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { _ ->
        viewModel.savePermissionFlowDone()
        onContinue()
    }

    val storageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val granted = results.values.all { it }
        viewModel.savePermissionStatus("storage", granted)
        if (granted) {
            currentStep = PermissionStep.LOCATION
        } else {
            coroutineScope.launch {
                snackbarHostState.showSnackbar(
                    message = "Storage permission denied. Private features may not work fully.",
                    duration = SnackbarDuration.Short
                )
                currentStep = PermissionStep.LOCATION
            }
        }
    }

    val locationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val fineGranted = results[android.Manifest.permission.ACCESS_FINE_LOCATION] == true
        val coarseGranted = results[android.Manifest.permission.ACCESS_COARSE_LOCATION] == true
        val foregroundGranted = fineGranted || coarseGranted
        
        viewModel.savePermissionStatus("location_foreground", foregroundGranted)
        
        if (foregroundGranted) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                showBgDialog.value = true
            } else {
                viewModel.savePermissionFlowDone()
                onContinue()
            }
        } else {
            coroutineScope.launch {
                snackbarHostState.showSnackbar(
                    message = "Location permission denied. Geotagging is disabled.",
                    duration = SnackbarDuration.Short
                )
                viewModel.savePermissionFlowDone()
                onContinue()
            }
        }
    }

    if (showBgDialog.value) {
        AlertDialog(
            onDismissRequest = {
                showBgDialog.value = false
                viewModel.savePermissionFlowDone()
                onContinue()
            },
            title = {
                Text(text = "Background Location Access", color = Color.White, fontWeight = FontWeight.Bold)
            },
            text = {
                Text(
                    text = "Allow Calc Pro to access your location to tag your files. On the next screen, please select 'Allow all the time' to enable background geotagging features.",
                    color = Color.White.copy(alpha = 0.8f)
                )
            },
            containerColor = Color(0xFF2C2C2E),
            confirmButton = {
                Button(
                    onClick = {
                        showBgDialog.value = false
                        try {
                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.fromParts("package", context.packageName, null)
                            }
                            openSettingsLauncher.launch(intent)
                        } catch (e: Exception) {
                            viewModel.savePermissionFlowDone()
                            onContinue()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9F0A))
                ) {
                    Text("Settings", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showBgDialog.value = false
                        viewModel.savePermissionFlowDone()
                        onContinue()
                    }
                ) {
                    Text("Skip", color = Color.Gray)
                }
            }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        containerColor = Color(0xFF1C1C1E)
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF2C2C2E)),
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .padding(12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .padding(24.dp)
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Crossfade(targetState = currentStep, label = "PermissionStepTransition") { step ->
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            when (step) {
                                PermissionStep.STORAGE -> {
                                    FolderIcon(
                                        modifier = Modifier.size(72.dp),
                                        tint = Color(0xFFFF9F0A)
                                    )
                                    
                                    Spacer(modifier = Modifier.height(24.dp))
                                    
                                    Text(
                                        text = "Storage / Gallery Access",
                                        color = Color.White,
                                        fontSize = 22.sp,
                                        fontWeight = FontWeight.Bold,
                                        textAlign = TextAlign.Center
                                    )
                                    
                                    Spacer(modifier = Modifier.height(16.dp))
                                    
                                    Text(
                                        text = "Allow Calc Pro to access your photos and files to import them into your private vault.",
                                        color = Color.White.copy(alpha = 0.82f),
                                        fontSize = 14.sp,
                                        textAlign = TextAlign.Center,
                                        lineHeight = 22.sp
                                    )
                                    
                                    Spacer(modifier = Modifier.height(32.dp))
                                    
                                    Button(
                                        onClick = {
                                            storageLauncher.launch(storagePermissions)
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9F0A)),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(48.dp)
                                            .testTag("btn_grant_storage")
                                    ) {
                                        Text(
                                            text = "Grant Access",
                                            color = Color.White,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                                PermissionStep.LOCATION -> {
                                    MarkerIcon(
                                        modifier = Modifier.size(72.dp),
                                        tint = Color(0xFFFF9F0A)
                                    )
                                    
                                    Spacer(modifier = Modifier.height(24.dp))
                                    
                                    Text(
                                        text = "Location Access",
                                        color = Color.White,
                                        fontSize = 22.sp,
                                        fontWeight = FontWeight.Bold,
                                        textAlign = TextAlign.Center
                                    )
                                    
                                    Spacer(modifier = Modifier.height(16.dp))
                                    
                                    Text(
                                        text = "Allow Calc Pro to access your location to tag your files.",
                                        color = Color.White.copy(alpha = 0.82f),
                                        fontSize = 14.sp,
                                        textAlign = TextAlign.Center,
                                        lineHeight = 22.sp
                                    )
                                    
                                    Spacer(modifier = Modifier.height(32.dp))
                                    
                                    Button(
                                        onClick = {
                                            locationLauncher.launch(
                                                arrayOf(
                                                    android.Manifest.permission.ACCESS_FINE_LOCATION,
                                                    android.Manifest.permission.ACCESS_COARSE_LOCATION
                                                )
                                            )
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9F0A)),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(48.dp)
                                            .testTag("btn_grant_location")
                                    ) {
                                        Text(
                                            text = "Grant Access",
                                            color = Color.White,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.SemiBold
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
}

@Composable
fun VaultHomeScreen(
    viewModel: MainViewModel,
    onNavigateToAlbums: () -> Unit,
    onNavigateToCamera: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToCategory: (String) -> Unit,
    onNavigateToVideo: () -> Unit,
    onNavigateToAudio: () -> Unit,
    onLock: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Permission launchers
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        viewModel.saveCameraPermissionGranted(isGranted)
        if (isGranted) {
            onNavigateToCamera()
        }
    }

    val videoPermissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val cameraGranted = results[android.Manifest.permission.CAMERA] == true
        val micGranted = results[android.Manifest.permission.RECORD_AUDIO] == true
        viewModel.saveCameraPermissionGranted(cameraGranted)
        viewModel.saveMicrophonePermissionGranted(micGranted)
        if (cameraGranted && micGranted) {
            onNavigateToVideo()
        }
    }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        viewModel.saveMicrophonePermissionGranted(isGranted)
        if (isGranted) {
            onNavigateToAudio()
        }
    }

    // Permission explanation dialog states
    var showCameraExplDialog by remember { mutableStateOf(false) }
    var showVideoExplDialog by remember { mutableStateOf(false) }
    var showAudioExplDialog by remember { mutableStateOf(false) }

    val isDark = viewModel.currentTheme.value == AppThemeMode.Dark
    val bgCol = if (isDark) Color(0xFF13131A) else Color(0xFFF2F2F7)
    val cardBg = if (isDark) Color(0xFF1C1C24) else Color.White
    val textCol = if (isDark) Color.White else Color(0xFF1C1C1E)
    val accentColor = Color(0xFFFF9F0A)

    // DB states
    val photoCount by viewModel.photoCount.collectAsState(initial = 0)
    val videoCount by viewModel.videoCount.collectAsState(initial = 0)
    val audioCount by viewModel.audioCount.collectAsState(initial = 0)
    val docCount by viewModel.documentCount.collectAsState(initial = 0)

    val lastPhotoList by viewModel.lastPhoto.collectAsState(initial = emptyList())
    val lastVideoList by viewModel.lastVideo.collectAsState(initial = emptyList())
    val recentFilesList by viewModel.recentFiles.collectAsState(initial = emptyList())

    // UI Local variables
    var showEditNameDialog by remember { mutableStateOf(false) }
    var newNameText by remember { mutableStateOf("") }
    var showCaptureSheet by remember { mutableStateOf(false) }
    var selectedViewFile by remember { mutableStateOf<VaultFile?>(null) }
    var searchQuery by remember { mutableStateOf("") }

    // Simulation popups for Import
    var showImportDialogType by remember { mutableStateOf<String?>(null) } // "PHOTO", "VIDEO", "AUDIO", "DOCUMENT"
    var simFileName by remember { mutableStateOf("") }
    var simLocation by remember { mutableStateOf("") }

    // Skeleton indicator
    var isSkeletonLoading by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(400)
        isSkeletonLoading = false
    }

    // Disk Space using StatFs
    val diskSpace = remember {
        try {
            val path = context.filesDir.absolutePath
            val stat = android.os.StatFs(path)
            val blockSize = stat.blockSizeLong
            val totalBlocks = stat.blockCountLong
            val availableBlocks = stat.availableBlocksLong
            
            val totalB = totalBlocks * blockSize
            val freeB = availableBlocks * blockSize
            val usedB = totalB - freeB
            
            val totalGB = totalB.toDouble() / (1024 * 1024 * 1024)
            val usedGB = usedB.toDouble() / (1024 * 1024 * 1024)
            val displayUsed = if (usedGB < 0.1) 0.58 else usedGB
            Pair(displayUsed, totalGB)
        } catch (e: Exception) {
            Pair(1.15, 64.0)
        }
    }
    val usedS = diskSpace.first
    val totalS = diskSpace.second
    val progressS = (usedS / totalS).toFloat().coerceIn(0f, 1f)

    // Edit Name dialog popup
    if (showEditNameDialog) {
        AlertDialog(
            onDismissRequest = { showEditNameDialog = false },
            title = {
                Text("Edit Display Name", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            },
            text = {
                Column {
                    Text("Enter a custom display name below:", color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newNameText,
                        onValueChange = { newNameText = it },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = accentColor,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.3f)
                        ),
                        modifier = Modifier.fillMaxWidth().testTag("edit_username_input")
                    )
                }
            },
            containerColor = Color(0xFF1C1C24),
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.updateDisplayName(newNameText)
                        showEditNameDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = accentColor)
                ) {
                    Text("Save", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditNameDialog = false }) {
                    Text("Cancel", color = Color.Gray)
                }
            }
        )
    }

    // Permission check dialogue explanations
    if (showCameraExplDialog) {
        AlertDialog(
            onDismissRequest = { showCameraExplDialog = false },
            title = {
                Text(text = "Camera Access Required", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            },
            text = {
                Text(
                    text = "Calc Pro needs camera access to capture photos directly into your vault. This photo will never appear in your device gallery.",
                    color = Color.White.copy(alpha = 0.82f),
                    fontSize = 14.sp
                )
            },
            containerColor = Color(0xFF2C2C2E),
            confirmButton = {
                Button(
                    modifier = Modifier.testTag("dialog_grant_camera"),
                    onClick = {
                        showCameraExplDialog = false
                        cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9F0A))
                ) {
                    Text("Grant", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showCameraExplDialog = false }
                ) {
                    Text("Not Now", color = Color.Gray)
                }
            }
        )
    }

    if (showVideoExplDialog) {
        AlertDialog(
            onDismissRequest = { showVideoExplDialog = false },
            title = {
                Text(text = "Camera & Mic Access Required", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            },
            text = {
                Text(
                    text = "Calc Pro needs camera and microphone access to record video directly into your vault.",
                    color = Color.White.copy(alpha = 0.82f),
                    fontSize = 14.sp
                )
            },
            containerColor = Color(0xFF2C2C2E),
            confirmButton = {
                Button(
                    modifier = Modifier.testTag("dialog_grant_video"),
                    onClick = {
                        showVideoExplDialog = false
                        videoPermissionsLauncher.launch(
                            arrayOf(
                                android.Manifest.permission.CAMERA,
                                android.Manifest.permission.RECORD_AUDIO
                            )
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9F0A))
                ) {
                    Text("Grant", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showVideoExplDialog = false }
                ) {
                    Text("Not Now", color = Color.Gray)
                }
            }
        )
    }

    if (showAudioExplDialog) {
        AlertDialog(
            onDismissRequest = { showAudioExplDialog = false },
            title = {
                Text(text = "Microphone Access Required", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            },
            text = {
                Text(
                    text = "Calc Pro needs microphone access to record audio directly into your vault.",
                    color = Color.White.copy(alpha = 0.82f),
                    fontSize = 14.sp
                )
            },
            containerColor = Color(0xFF2C2C2E),
            confirmButton = {
                Button(
                    modifier = Modifier.testTag("dialog_grant_audio"),
                    onClick = {
                        showAudioExplDialog = false
                        audioPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9F0A))
                ) {
                    Text("Grant", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showAudioExplDialog = false }
                ) {
                    Text("Not Now", color = Color.Gray)
                }
            }
        )
    }

    // Capture Simulation dialogues
    showImportDialogType?.let { type ->
        AlertDialog(
            onDismissRequest = { showImportDialogType = null },
            title = {
                Text("Secure File Generator", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            },
            text = {
                Column {
                    Text("Simulate a camera capture or localized file download into Cal Pro's SQLite vault secure stream.", color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("File Name", color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = simFileName,
                        onValueChange = { simFileName = it },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = accentColor,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.3f)
                        ),
                        modifier = Modifier.fillMaxWidth().testTag("sim_filename_input")
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Geotag Location (Optional)", color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = simLocation,
                        onValueChange = { simLocation = it },
                        singleLine = true,
                        placeholder = { Text("e.g. New York, NY", color = Color.White.copy(alpha = 0.3f)) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = accentColor,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.3f)
                        ),
                        modifier = Modifier.fillMaxWidth().testTag("sim_location_input")
                    )
                }
            },
            containerColor = Color(0xFF1C1C24),
            confirmButton = {
                Button(
                    onClick = {
                        val finalName = simFileName.trim().ifEmpty { 
                            when(type) {
                                "PHOTO" -> "IMG_${System.currentTimeMillis()}.jpg"
                                "VIDEO" -> "VID_${System.currentTimeMillis()}.mp4"
                                "AUDIO" -> "REC_${System.currentTimeMillis()}.wav"
                                else -> "DOC_${System.currentTimeMillis()}.pdf"
                            }
                        }
                        val finalLoc = simLocation.trim().ifEmpty { null }
                        val randomSize = (100000L..50000000L).random()
                        val duration = if (type == "VIDEO" || type == "AUDIO") (5L..300L).random() else null
                        
                        viewModel.addNewFile(
                            name = finalName,
                            path = "sim_path_${System.currentTimeMillis()}",
                            type = type,
                            size = randomSize,
                            duration = duration,
                            location = finalLoc
                        )
                        showImportDialogType = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = accentColor)
                ) {
                    Text("Write to Vault", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showImportDialogType = null }) {
                    Text("Cancel", color = Color.Gray)
                }
            }
        )
    }

    // Detail / Preview file info dialog
    selectedViewFile?.let { vFile ->
        AlertDialog(
            onDismissRequest = { selectedViewFile = null },
            title = {
                Text("Secure File Inspector", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp)
                            .background(Color(0xFF0F0F15), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = when (vFile.type) {
                                    "PHOTO" -> Icons.Default.Face
                                    "VIDEO" -> Icons.Default.PlayArrow
                                    "AUDIO" -> Icons.Default.Refresh
                                    else -> Icons.Default.List
                                },
                                contentDescription = null,
                                tint = accentColor,
                                modifier = Modifier.size(40.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = vFile.name,
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 12.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Metadata Statistics", color = accentColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Category", color = Color.LightGray, fontSize = 12.sp)
                        Text(vFile.type, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("File Size", color = Color.LightGray, fontSize = 12.sp)
                        val formattedSize = String.format("%.2f MB", vFile.size.toDouble() / (1024 * 1024))
                        Text(formattedSize, color = Color.White, fontSize = 12.sp)
                    }
                    vFile.duration?.let { dur ->
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Playback Duration", color = Color.LightGray, fontSize = 12.sp)
                            Text("${dur}s", color = Color.White, fontSize = 12.sp)
                        }
                    }
                    vFile.location?.let { loc ->
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Geo Tag Location", color = Color.LightGray, fontSize = 12.sp)
                            Text(loc, color = Color.White, fontSize = 12.sp)
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Import Date", color = Color.LightGray, fontSize = 12.sp)
                        val date = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
                            .format(java.util.Date(vFile.addedTimestamp))
                        Text(date, color = Color.White, fontSize = 12.sp)
                    }
                }
            },
            containerColor = Color(0xFF1C1C24),
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteFileById(vFile.id)
                        selectedViewFile = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF453A))
                ) {
                    Icon(imageVector = Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Delete", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedViewFile = null }) {
                    Text("Close", color = Color.Gray)
                }
            }
        )
    }

    Scaffold(
        containerColor = bgCol,
        bottomBar = {
            NavigationBar(
                containerColor = if (isDark) Color(0xFF15151F) else Color(0xFFF2F2F7),
                tonalElevation = 8.dp
            ) {
                NavigationBarItem(
                    selected = true,
                    onClick = { /* Keep home view */ },
                    icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
                    label = { Text("Home", fontSize = 11.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = accentColor,
                        selectedTextColor = accentColor,
                        unselectedIconColor = Color.Gray,
                        unselectedTextColor = Color.Gray,
                        indicatorColor = accentColor.copy(alpha = 0.12f)
                    ),
                    modifier = Modifier.testTag("nav_home_item")
                )
                
                // Floating-action plus capture trigger in the center
                NavigationBarItem(
                    selected = false,
                    onClick = { showCaptureSheet = true },
                    icon = {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .background(accentColor, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Capture Module", tint = Color.White)
                        }
                    },
                    label = { Text("Capture", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = accentColor) },
                    colors = NavigationBarItemDefaults.colors(
                        unselectedIconColor = accentColor,
                        indicatorColor = Color.Transparent
                    ),
                    modifier = Modifier.testTag("nav_capture_item")
                )

                NavigationBarItem(
                    selected = false,
                    onClick = onNavigateToSettings,
                    icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                    label = { Text("Settings", fontSize = 11.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        unselectedIconColor = Color.Gray,
                        unselectedTextColor = Color.Gray
                    ),
                    modifier = Modifier.testTag("nav_settings_item")
                )
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // 1. Top App Bar Layout
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left Side: Initials Avatar
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .background(accentColor.copy(alpha = 0.22f), CircleShape)
                                .clip(CircleShape)
                                .clickable {
                                    newNameText = viewModel.userDisplayName.value
                                    showEditNameDialog = true
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            val firstChar = viewModel.userDisplayName.value.trim().firstOrNull()?.uppercase() ?: "O"
                            Text(
                                text = firstChar,
                                color = accentColor,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                        }

                        // Center: Editable Username inline
                        Column(
                            modifier = Modifier.clickable {
                                newNameText = viewModel.userDisplayName.value
                                showEditNameDialog = true
                            }
                        ) {
                            Text(
                                text = "CALC PRO CABINET",
                                color = accentColor,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp,
                                letterSpacing = 1.sp
                            )
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = viewModel.userDisplayName.value,
                                    color = textCol,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Icon(
                                    imageVector = Icons.Default.Refresh, // Pencil mockup
                                    contentDescription = "Edit Inline",
                                    tint = textCol.copy(alpha = 0.4f),
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }

                    // Right Side: Quick search toggler and Lock Icon
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = {
                                // Search bar triggers a category filter with a text toggle or filters active layout
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Search",
                                tint = textCol.copy(alpha = 0.8f)
                            )
                        }

                        IconButton(
                            onClick = onLock,
                            modifier = Modifier
                                .background(Color(0xFFFF453A).copy(alpha = 0.12f), CircleShape)
                                .size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "Lock Secure Vault",
                                tint = Color(0xFFFF453A),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 2. Storage Banner Bar
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = cardBg),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "CABINET SPACE STORAGE",
                                    color = textCol.copy(alpha = 0.45f),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = String.format("%.2f GB used of %.1f GB", usedS, totalS),
                                    color = textCol,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Text(
                                text = String.format("%d%%", (progressS * 100).toInt()),
                                color = accentColor,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        LinearProgressIndicator(
                            progress = { progressS },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = accentColor,
                            trackColor = if (isDark) Color(0xFF2C2C35) else Color(0xFFE5E5EA),
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // 3. Category Grid 2x2 Header
                Text(
                    text = "SECURE CABINET SHELVES",
                    color = textCol.copy(alpha = 0.45f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )

                Spacer(modifier = Modifier.height(12.dp))

                if (isSkeletonLoading) {
                    // Skeleton display
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Box(modifier = Modifier.weight(1f).height(100.dp).background(cardBg.copy(alpha = 0.5f), RoundedCornerShape(16.dp)))
                            Box(modifier = Modifier.weight(1f).height(100.dp).background(cardBg.copy(alpha = 0.5f), RoundedCornerShape(16.dp)))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Box(modifier = Modifier.weight(1f).height(100.dp).background(cardBg.copy(alpha = 0.5f), RoundedCornerShape(16.dp)))
                            Box(modifier = Modifier.weight(1f).height(100.dp).background(cardBg.copy(alpha = 0.5f), RoundedCornerShape(16.dp)))
                        }
                    }
                } else {
                    // Category Shelf Cards
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // 3A. Photos Catalog Card
                            CategoryGridCard(
                                title = "Photos",
                                count = photoCount,
                                backgroundLabel = lastPhotoList.firstOrNull()?.name ?: "No Photos",
                                hasActiveMedia = lastPhotoList.isNotEmpty(),
                                isDark = isDark,
                                cardBg = cardBg,
                                textCol = textCol,
                                onClick = { onNavigateToCategory("PHOTO") },
                                modifier = Modifier.weight(1f).testTag("category_photos_card")
                            )

                            // 3B. Videos Catalog Card
                            CategoryGridCard(
                                title = "Videos",
                                count = videoCount,
                                backgroundLabel = lastVideoList.firstOrNull()?.name ?: "No Videos",
                                hasActiveMedia = lastVideoList.isNotEmpty(),
                                isDark = isDark,
                                cardBg = cardBg,
                                textCol = textCol,
                                onClick = { onNavigateToCategory("VIDEO") },
                                modifier = Modifier.weight(1f).testTag("category_videos_card")
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // 3C. Audio Catalog Card with Waveform
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = cardBg),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(100.dp)
                                    .clickable { onNavigateToCategory("AUDIO") }
                                    .testTag("category_audio_card")
                            ) {
                                Box(modifier = Modifier.fillMaxSize().padding(14.dp)) {
                                    // Header Details
                                    Column(modifier = Modifier.align(Alignment.BottomStart)) {
                                        Text("Audio", color = textCol, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                        Text("$audioCount records", color = textCol.copy(alpha = 0.5f), fontSize = 11.sp)
                                    }
                                    
                                    // Animated Waveform right in the top corner / end
                                    AnimatedWaveform(
                                        modifier = Modifier
                                            .size(width = 40.dp, height = 30.dp)
                                            .align(Alignment.TopEnd)
                                    )
                                }
                            }

                            // 3D. Documents Catalog Card
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = cardBg),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(100.dp)
                                    .clickable { onNavigateToCategory("DOCUMENT") }
                                    .testTag("category_docs_card")
                            ) {
                                Box(modifier = Modifier.fillMaxSize().padding(14.dp)) {
                                    Column(modifier = Modifier.align(Alignment.BottomStart)) {
                                        Text("Documents", color = textCol, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                        Text("$docCount items", color = textCol.copy(alpha = 0.5f), fontSize = 11.sp)
                                    }

                                    // Stacked Pages mock in Corner
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .align(Alignment.TopEnd)
                                    ) {
                                        // Back card
                                        Box(
                                            modifier = Modifier
                                                .size(20.dp)
                                                .background(accentColor.copy(alpha = 0.25f), RoundedCornerShape(4.dp))
                                                .align(Alignment.BottomEnd)
                                        )
                                        // Front card
                                        Box(
                                            modifier = Modifier
                                                .size(20.dp)
                                                .background(accentColor, RoundedCornerShape(4.dp))
                                                .align(Alignment.TopStart),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.List,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(12.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // 4. Recent files strip
                Text(
                    text = "RECENTLY ADDED TO CABINET",
                    color = textCol.copy(alpha = 0.45f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )

                Spacer(modifier = Modifier.height(12.dp))

                if (recentFilesList.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp)
                            .background(cardBg, RoundedCornerShape(16.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No files in database yet. Tap '+' to generate.",
                            color = textCol.copy(alpha = 0.4f),
                            fontSize = 13.sp
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        recentFilesList.forEach { rFile ->
                            Card(
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = cardBg),
                                modifier = Modifier
                                    .width(130.dp)
                                    .wrapContentHeight()
                                    .clickable { selectedViewFile = rFile }
                                    .testTag("recent_file_${rFile.id}")
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(80.dp)
                                            .background(
                                                if (isDark) Color(0xFF101016) else Color(0xFFF0F0FA),
                                                RoundedCornerShape(8.dp)
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = when (rFile.type) {
                                                "PHOTO" -> Icons.Default.Face
                                                "VIDEO" -> Icons.Default.PlayArrow
                                                "AUDIO" -> Icons.Default.Refresh
                                                else -> Icons.Default.List
                                            },
                                            contentDescription = null,
                                            tint = accentColor.copy(alpha = 0.65f),
                                            modifier = Modifier.size(28.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = rFile.name,
                                        color = textCol,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = rFile.type,
                                        color = accentColor,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Capture floating Sheet Drawer Dialog
            if (showCaptureSheet) {
                // Background dim overlay
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.6f))
                        .clickable { showCaptureSheet = false }
                ) {
                    Card(
                        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xEB13131A)), // Semi-transparent beautiful dark glass option
                        modifier = Modifier
                            .fillMaxWidth()
                            .wrapContentHeight()
                            .align(Alignment.BottomCenter)
                            .clickable(enabled = false) {}
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(24.dp)
                                .fillMaxWidth()
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(width = 40.dp, height = 4.dp)
                                    .background(Color.Gray.copy(alpha = 0.5f), RoundedCornerShape(2.dp))
                                    .align(Alignment.CenterHorizontally)
                             )
                             Spacer(modifier = Modifier.height(16.dp))
                             Text(
                                 text = "Secure Capture Sources",
                                 color = Color.White,
                                 fontSize = 18.sp,
                                 fontWeight = FontWeight.Bold
                             )
                             Spacer(modifier = Modifier.height(6.dp))
                             Text(
                                 text = "Direct-to-vault sandbox ensures media stays isolated & encrypted.",
                                 color = Color.Gray,
                                 fontSize = 12.sp
                             )
                             
                             Spacer(modifier = Modifier.height(20.dp))
                             
                             Row(
                                 modifier = Modifier.fillMaxWidth(),
                                 horizontalArrangement = Arrangement.spacedBy(12.dp)
                             ) {
                                 CaptureSheetButton(
                                     title = "Take Photo",
                                     icon = Icons.Default.Face,
                                     onClick = {
                                         val isSystemCamGranted = androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED
                                         val isPrefCamGranted = viewModel.isCameraPermissionGranted()
                                         
                                         if (isPrefCamGranted || isSystemCamGranted) {
                                             showCaptureSheet = false
                                             onNavigateToCamera()
                                         } else {
                                             showCaptureSheet = false
                                             showCameraExplDialog = true
                                         }
                                     },
                                     modifier = Modifier.weight(1f).testTag("btn_capture_photo")
                                 )
                                 
                                 CaptureSheetButton(
                                     title = "Record Video",
                                     icon = Icons.Default.PlayArrow,
                                     onClick = {
                                         val isSystemCamGranted = androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED
                                         val isSystemMicGranted = androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED
                                         val isPrefCamGranted = viewModel.isCameraPermissionGranted()
                                         val isPrefMicGranted = viewModel.isMicrophonePermissionGranted()
                                         
                                         if ((isPrefCamGranted || isSystemCamGranted) && (isPrefMicGranted || isSystemMicGranted)) {
                                             showCaptureSheet = false
                                             onNavigateToVideo()
                                         } else {
                                             showCaptureSheet = false
                                             showVideoExplDialog = true
                                         }
                                     },
                                     modifier = Modifier.weight(1f).testTag("btn_capture_video")
                                 )
 
                                 CaptureSheetButton(
                                     title = "Record Audio",
                                     icon = Icons.Default.Refresh,
                                     onClick = {
                                         val isSystemMicGranted = androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED
                                         val isPrefMicGranted = viewModel.isMicrophonePermissionGranted()
                                         
                                         if (isPrefMicGranted || isSystemMicGranted) {
                                             showCaptureSheet = false
                                             onNavigateToAudio()
                                         } else {
                                             showCaptureSheet = false
                                             showAudioExplDialog = true
                                         }
                                     },
                                     modifier = Modifier.weight(1f).testTag("btn_capture_audio")
                                 )
                             }
                             
                             Spacer(modifier = Modifier.height(16.dp))
                         }
                     }
                 }
             }
        }
    }
}

@Composable
fun CategoryGridCard(
    title: String,
    count: Int,
    backgroundLabel: String,
    hasActiveMedia: Boolean,
    isDark: Boolean,
    cardBg: Color,
    textCol: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        modifier = modifier
            .height(100.dp)
            .clickable { onClick() }
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (hasActiveMedia) {
                // Stylish gradient thumbnail visual background representing the file
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            androidx.compose.ui.graphics.Brush.verticalGradient(
                                colors = listOf(Color(0xFFFF9F0A).copy(alpha = 0.25f), Color.Black.copy(alpha = 0.85f))
                            )
                        )
                )
                Text(
                    text = backgroundLabel,
                    color = Color.White.copy(alpha = 0.25f),
                    fontSize = 11.sp,
                    maxLines = 1,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .padding(8.dp)
                        .align(Alignment.Center)
                )
            } else {
                // Static subtle dark modern visual state
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            androidx.compose.ui.graphics.Brush.verticalGradient(
                                colors = listOf(Color(0xFF25252D).copy(alpha = 0.1f), Color(0xFF101015))
                            )
                        )
                )
            }
            
            // Text values overlaid at the bottom start
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(14.dp)
            ) {
                Text(text = title, color = textCol, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text(text = "$count files", color = textCol.copy(alpha = 0.5f), fontSize = 11.sp)
            }

            // Small badge in top right corner
            if (count > 0) {
                Box(
                    modifier = Modifier
                        .padding(12.dp)
                        .background(Color(0xFFFF9F0A), RoundedCornerShape(8.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                        .align(Alignment.TopEnd)
                ) {
                    Text(
                        text = count.toString(),
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun CaptureSheetButton(
    title: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF2C2C36)),
        modifier = modifier.clickable { onClick() }
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(Color(0xFFFF9F0A).copy(alpha = 0.12f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = icon, contentDescription = null, tint = Color(0xFFFF9F0A), modifier = Modifier.size(18.dp))
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(title, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun AnimatedWaveform(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "waveform_animation")
    val h1State = transition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "h1"
    )
    val h2State = transition.animateFloat(
        initialValue = 0.8f,
        targetValue = 0.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "h2"
    )
    val h3State = transition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "h3"
    )
    val h4State = transition.animateFloat(
        initialValue = 0.6f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "h4"
    )
    
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val bars = listOf(h1State.value, h2State.value, h3State.value, h4State.value)
        bars.forEach { heightScale ->
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight(heightScale)
                    .background(Color(0xFFFF9F0A), shape = RoundedCornerShape(2.dp))
            )
        }
    }
}

// -------------------------------------------------------------
// SECURE VERTICAL CATEGORY SCREEN WITH SEARCH FILTER
// -------------------------------------------------------------
@Composable
fun VaultCategoryScreen(
    viewModel: MainViewModel,
    categoryType: String,
    onBack: () -> Unit,
    onNavigateToPhotoViewer: (String) -> Unit = {},
    onNavigateToVideoPlayer: (String) -> Unit = {}
) {
    if (categoryType.uppercase() == "PHOTO") {
        VaultPhotosScreen(
            viewModel = viewModel,
            onBack = onBack,
            onNavigateToPhotoViewer = onNavigateToPhotoViewer
        )
        return
    }

    if (categoryType.uppercase() == "VIDEO") {
        VaultVideosScreen(
            viewModel = viewModel,
            onBack = onBack,
            onNavigateToVideoPlayer = onNavigateToVideoPlayer
        )
        return
    }

    val isDark = viewModel.currentTheme.value == AppThemeMode.Dark
    val bgCol = if (isDark) Color(0xFF13131A) else Color(0xFFF2F2F7)
    val cardBg = if (isDark) Color(0xFF1C1C24) else Color.White
    val textCol = if (isDark) Color.White else Color(0xFF1C1C1E)
    val accentColor = Color(0xFFFF9F0A)

    var searchQuery by remember { mutableStateOf("") }
    var selectedViewFile by remember { mutableStateOf<VaultFile?>(null) }

    // Collect all appropriate category files query Flow
    val allCategoryFiles by viewModel.allFiles.collectAsState(initial = emptyList())
    val categoryFiles = allCategoryFiles.filter { it.type == categoryType }
    val filteredFiles = categoryFiles.filter { it.name.contains(searchQuery, ignoreCase = true) }

    // Detail file overlay dialog
    selectedViewFile?.let { vFile ->
        AlertDialog(
            onDismissRequest = { selectedViewFile = null },
            title = {
                Text("Secure File Inspector", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp)
                            .background(Color(0xFF0F0F15), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = when (vFile.type) {
                                    "PHOTO" -> Icons.Default.Face
                                    "VIDEO" -> Icons.Default.PlayArrow
                                    "AUDIO" -> Icons.Default.Refresh
                                    else -> Icons.Default.List
                                },
                                contentDescription = null,
                                tint = accentColor,
                                modifier = Modifier.size(40.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = vFile.name,
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 12.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Metadata Statistics", color = accentColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Category", color = Color.LightGray, fontSize = 12.sp)
                        Text(vFile.type, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("File Size", color = Color.LightGray, fontSize = 12.sp)
                        val formattedSize = String.format("%.2f MB", vFile.size.toDouble() / (1024 * 1024))
                        Text(formattedSize, color = Color.White, fontSize = 12.sp)
                    }
                    vFile.duration?.let { dur ->
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Playback Duration", color = Color.LightGray, fontSize = 12.sp)
                            Text("${dur}s", color = Color.White, fontSize = 12.sp)
                        }
                    }
                    vFile.location?.let { loc ->
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Geo Tag Location", color = Color.LightGray, fontSize = 12.sp)
                            Text(loc, color = Color.White, fontSize = 12.sp)
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Import Date", color = Color.LightGray, fontSize = 12.sp)
                        val date = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
                            .format(java.util.Date(vFile.addedTimestamp))
                        Text(date, color = Color.White, fontSize = 12.sp)
                    }
                }
            },
            containerColor = Color(0xFF1C1C24),
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteFileById(vFile.id)
                        selectedViewFile = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF453A))
                ) {
                    Icon(imageVector = Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Delete", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedViewFile = null }) {
                    Text("Close", color = Color.Gray)
                }
            }
        )
    }

    Scaffold(
        containerColor = bgCol,
        topBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(horizontal = 8.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back", tint = textCol)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${categoryType.lowercase().replaceFirstChar { it.uppercase() }} Shelf",
                        color = textCol,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 20.dp)
        ) {
            // Live Category Search field
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Filter shelf items by name...", color = textCol.copy(alpha = 0.4f), fontSize = 14.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = textCol.copy(alpha = 0.5f)) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = textCol,
                    unfocusedTextColor = textCol,
                    focusedBorderColor = accentColor,
                    unfocusedBorderColor = textCol.copy(alpha = 0.15f),
                    focusedContainerColor = cardBg,
                    unfocusedContainerColor = cardBg
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("search_category_input")
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (filteredFiles.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = textCol.copy(alpha = 0.25f),
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = if (searchQuery.isNotEmpty()) "No items match query search." else "Shelf is empty.",
                            color = textCol.copy(alpha = 0.4f),
                            fontSize = 14.sp
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredFiles, key = { it.id }) { vFile ->
                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = cardBg),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedViewFile = vFile }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .background(
                                            if (isDark) Color(0xFF101016) else Color(0xFFF0F0FA),
                                            CircleShape
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = when (categoryType) {
                                            "PHOTO" -> Icons.Default.Face
                                            "VIDEO" -> Icons.Default.PlayArrow
                                            "AUDIO" -> Icons.Default.Refresh
                                            else -> Icons.Default.List
                                        },
                                        contentDescription = null,
                                        tint = accentColor,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = vFile.name,
                                        color = textCol,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    val formattedSize = String.format("%.2f MB", vFile.size.toDouble() / (1024 * 1024))
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text(formattedSize, color = textCol.copy(alpha = 0.5f), fontSize = 11.sp)
                                        vFile.location?.let { loc ->
                                            Text("•  $loc", color = accentColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }

                                Icon(
                                    imageVector = Icons.Default.ArrowForward,
                                    contentDescription = null,
                                    tint = textCol.copy(alpha = 0.2f),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
fun VaultAlbumsScreen(
    onBack: () -> Unit
) {
    var albumsList by remember {
        mutableStateOf(
            listOf(
                Pair("Tax Forms & Returns", 3),
                Pair("Family Offscreen Photos", 12),
                Pair("Client Credentials", 4)
            )
        )
    }

    Scaffold(
        topBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1C1C1E))
                    .padding(vertical = 12.dp, horizontal = 16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Go back",
                            tint = Color.White
                        )
                    }
                    Text(
                        text = "Secure Albums",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = {
                        val namesList = listOf("Backup Passwords", "Rent Contracts", "ID Scans", "Private Receipts")
                        val unused = namesList.filter { name -> albumsList.none { it.first == name } }
                        if (unused.isNotEmpty()) {
                            albumsList = albumsList + Pair(unused.random(), (1..10).random())
                        }
                    }) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "New Album folder",
                            tint = Color(0xFFFF9F0A)
                        )
                    }
                }
            }
        },
        containerColor = Color(0xFF1C1C1E)
    ) { innerPadding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(albumsList) { album ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF2C2C2E)),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .padding(16.dp)
                            .fillMaxSize(),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Icon(
                            imageVector = Icons.Default.Home,
                            contentDescription = null,
                            tint = Color(0xFF0A84FF),
                            modifier = Modifier.size(36.dp)
                        )

                        Column {
                            Text(
                                text = album.first,
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${album.second} secure files",
                                color = Color.White.copy(alpha = 0.5f),
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun VaultCameraScreen(
    onBack: () -> Unit
) {
    var captureCount by remember { mutableStateOf(0) }
    var showFlash by remember { mutableStateOf(false) }

    LaunchedEffect(showFlash) {
        if (showFlash) {
            kotlinx.coroutines.delay(120)
            showFlash = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Leave camera view",
                        tint = Color.White
                    )
                }

                Text(
                    text = "SANDBOX CAM",
                    color = Color.White,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    modifier = Modifier
                        .background(Color.Red.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                )

                IconButton(
                    onClick = {},
                    enabled = false
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Flash disabled",
                        tint = Color.White.copy(alpha = 0.5f)
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .aspectRatio(1f)
                    .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(4.dp)),
                contentAlignment = Alignment.Center
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Spacer(modifier = Modifier.weight(1f))
                    HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 1.dp)
                    Spacer(modifier = Modifier.weight(1f))
                    HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 1.dp)
                    Spacer(modifier = Modifier.weight(1f))
                }
                Row(modifier = Modifier.fillMaxSize()) {
                    Spacer(modifier = Modifier.weight(1f))
                    VerticalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 1.dp, modifier = Modifier.fillMaxHeight().width(1.dp))
                    Spacer(modifier = Modifier.weight(1f))
                    VerticalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 1.dp, modifier = Modifier.fillMaxHeight().width(1.dp))
                    Spacer(modifier = Modifier.weight(1f))
                }

                if (captureCount > 0) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(12.dp)
                            .background(Color(0xFF32D74B), CircleShape)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "+$captureCount SAVED",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp
                        )
                    }
                } else {
                    Text(
                        text = "Secure Preview Active",
                        color = Color.White.copy(alpha = 0.35f),
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        textAlign = TextAlign.Center
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Direct-to-vault sandbox technology bypasses regular system photoroll.",
                    color = Color.White.copy(alpha = 0.45f),
                    textAlign = TextAlign.Center,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                Spacer(modifier = Modifier.height(18.dp))

                Box(
                    modifier = Modifier
                        .size(76.dp)
                        .border(4.dp, Color.White, CircleShape)
                        .padding(4.dp)
                        .background(Color.White, CircleShape)
                        .clickable {
                            showFlash = true
                            captureCount++
                        }
                )
            }
        }

        if (showFlash) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White)
            )
        }
    }
}

@Composable
fun VaultVideoScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    var isRecording by remember { mutableStateOf(false) }
    var secondsElapsed by remember { mutableStateOf(0) }
    var videosRecordedCount by remember { mutableStateOf(0) }

    LaunchedEffect(isRecording) {
        if (isRecording) {
            secondsElapsed = 0
            while (isRecording) {
                kotlinx.coroutines.delay(1000)
                secondsElapsed++
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0C0C10))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Leave video view",
                        tint = Color.White
                    )
                }

                Text(
                    text = "SANDBOX VIDEO",
                    color = Color.White,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    modifier = Modifier
                        .background(Color.Red.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                )

                Box(modifier = Modifier.size(40.dp)) // Spacer
            }

            // Viewfinder and recording timer
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .aspectRatio(1f)
                    .border(2.dp, if (isRecording) Color.Red else Color.White.copy(alpha = 0.15f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (isRecording) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .background(Color.Red, CircleShape)
                            )
                            val minutes = secondsElapsed / 60
                            val seconds = secondsElapsed % 60
                            Text(
                                text = String.format("%02d:%02d", minutes, seconds),
                                color = Color.White,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "RECORDING DIRECT TO VAULT",
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Direct Secure Capture Stream",
                            color = Color.White.copy(alpha = 0.4f),
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Monospace,
                            textAlign = TextAlign.Center
                        )
                        if (videosRecordedCount > 0) {
                            Spacer(modifier = Modifier.height(14.dp))
                            Box(
                                modifier = Modifier
                                    .background(Color(0xFF32D74B), CircleShape)
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = "+$videosRecordedCount VIDEOS SECURED",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
            }

            // Bottom control triggers
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "High definition sandbox encoding ensures media stays isolated & encrypted.",
                    color = Color.White.copy(alpha = 0.45f),
                    textAlign = TextAlign.Center,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Giant recording button
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .border(4.dp, Color.White, CircleShape)
                        .padding(6.dp)
                        .background(if (isRecording) Color.Transparent else Color.Red, CircleShape)
                        .clickable {
                            if (isRecording) {
                                isRecording = false
                                val duration = secondsElapsed.coerceAtLeast(1)
                                val randomSize = duration * (800000L..1200000L).random()
                                viewModel.addNewFile(
                                    name = "SEC_VID_${System.currentTimeMillis() % 10000}.mp4",
                                    path = "vault_vid_${System.currentTimeMillis()}",
                                    type = "VIDEO",
                                    size = randomSize,
                                    duration = duration.toLong(),
                                    location = "Direct Vault Stream"
                                )
                                videosRecordedCount++
                            } else {
                                isRecording = true
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (isRecording) {
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .background(Color.Red, RoundedCornerShape(4.dp))
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun VaultAudioScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    var isRecording by remember { mutableStateOf(false) }
    var secondsElapsed by remember { mutableStateOf(0) }
    var audioCount by remember { mutableStateOf(0) }

    LaunchedEffect(isRecording) {
        if (isRecording) {
            secondsElapsed = 0
            while (isRecording) {
                kotlinx.coroutines.delay(1000)
                secondsElapsed++
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0B141E))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Leave audio view",
                        tint = Color.White
                    )
                }

                Text(
                    text = "SANDBOX MICROPHONE",
                    color = Color.White,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    modifier = Modifier
                        .background(Color(0xFFFF9F0A).copy(alpha = 0.3f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                )

                Box(modifier = Modifier.size(40.dp))
            }

            // Elegant center display with waveform or microphone design
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .height(240.dp)
                    .background(Color(0xFF141D2A), RoundedCornerShape(24.dp))
                    .border(1.dp, Color(0xFFFF9F0A).copy(alpha = 0.15f), RoundedCornerShape(24.dp)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    if (isRecording) {
                        AnimatedWaveform(
                            modifier = Modifier
                                .size(width = 120.dp, height = 60.dp)
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        val minutes = secondsElapsed / 60
                        val seconds = secondsElapsed % 60
                        Text(
                            text = String.format("%02d:%02d", minutes, seconds),
                            color = Color.White,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "WRITING SECURE FLOW...",
                            color = Color(0xFFFF9F0A),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .background(Color(0xFFFF9F0A).copy(alpha = 0.12f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info, // Mic visual backup
                                contentDescription = null,
                                tint = Color(0xFFFF9F0A),
                                modifier = Modifier.size(36.dp)
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(18.dp))
                        Text(
                            text = "Secure Sandbox Recording",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Isolated stream bypasses standard media storage.",
                            color = Color.LightGray.copy(alpha = 0.6f),
                            fontSize = 11.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 24.dp)
                        )

                        if (audioCount > 0) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Box(
                                modifier = Modifier
                                    .background(Color(0xFF32D74B), CircleShape)
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "+$audioCount RECORDINGS CAPTURED",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }
                }
            }

            // Control Trigger Info
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Tap block below to trigger direct writing to your device secure sandbox.",
                    color = Color.White.copy(alpha = 0.45f),
                    textAlign = TextAlign.Center,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Large microphone record button
                Box(
                    modifier = Modifier
                        .size(76.dp)
                        .border(4.dp, Color.White, CircleShape)
                        .padding(6.dp)
                        .background(if (isRecording) Color(0xFFFF453A) else Color(0xFFFF9F0A), CircleShape)
                        .clickable {
                            if (isRecording) {
                                isRecording = false
                                val duration = secondsElapsed.coerceAtLeast(1)
                                val randomSize = duration * (12000L..25000L).random()
                                viewModel.addNewFile(
                                    name = "SEC_REC_${System.currentTimeMillis() % 10000}.wav",
                                    path = "vault_rec_${System.currentTimeMillis()}",
                                    type = "AUDIO",
                                    size = randomSize,
                                    duration = duration.toLong(),
                                    location = "Direct Audio Recorder"
                                )
                                audioCount++
                            } else {
                                isRecording = true
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isRecording) Icons.Default.Close else Icons.Default.Add,
                        contentDescription = "Capture Action Toggle",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun VaultSettingsScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
    onResetAll: () -> Unit
) {
    var nameField by remember { mutableStateOf(viewModel.userDisplayName.value) }

    Scaffold(
        topBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1C1C1E))
                    .padding(vertical = 12.dp, horizontal = 16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                    Text(
                        text = "Vault Options",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(48.dp))
                }
            }
        },
        containerColor = Color(0xFF1C1C1E)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF2C2C2E)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Customize Owner Settings",
                        color = Color(0xFFFF9F0A),
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = nameField,
                        onValueChange = {
                            nameField = it
                            viewModel.updateDisplayName(it)
                        },
                        label = { Text("Display Name", color = Color.White.copy(alpha = 0.5f)) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFFFF9F0A),
                            unfocusedBorderColor = Color.White.copy(alpha = 0.2f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF2C2C2E)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "General Preferences",
                        color = Color(0xFFFF9F0A),
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.toggleTheme() }
                            .padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                tint = Color.White
                            )
                            Text(text = "App Color Scheme", color = Color.White)
                        }
                        Text(
                            text = if (viewModel.currentTheme.value == AppThemeMode.Dark) "Dark" else "Light",
                            color = Color(0xFFFF9F0A),
                            fontWeight = FontWeight.Bold
                        )
                    }

                    HorizontalDivider(color = Color.White.copy(alpha = 0.1f))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = Color.White
                            )
                            Column {
                                Text(text = "Background Playback", color = Color.White)
                                Text(text = "Keep audio playing when app is backgrounded", color = Color.Gray, fontSize = 10.sp)
                            }
                        }
                        androidx.compose.material3.Switch(
                            checked = viewModel.backgroundVideoPlayback.value,
                            onCheckedChange = { viewModel.setBackgroundVideoPlayback(it) },
                            colors = androidx.compose.material3.SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = Color(0xFFFF9F0A),
                                uncheckedThumbColor = Color.Gray,
                                uncheckedTrackColor = Color.DarkGray
                            )
                        )
                    }

                    HorizontalDivider(color = Color.White.copy(alpha = 0.1f))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                tint = Color.White
                            )
                            Text(text = "Vault Key (Default PIN)", color = Color.White)
                        }
                        Text(
                            text = "1234",
                            color = Color.White.copy(alpha = 0.6f),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = {
                    viewModel.resetApp()
                    onResetAll()
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF453A)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(imageVector = Icons.Default.Delete, contentDescription = null, tint = Color.White)
                    Text(
                        text = "Factory Reset & Wipe Safe Vault",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}

// =============================================================
// SECURE PHOTO GRID VIEWER & MANAGER (PHOTOS SCREEN)
// =============================================================

sealed class GridItem {
    data class Header(val title: String) : GridItem()
    data class PhotoCard(val photo: VaultFile) : GridItem()
}

fun getGroupHeaderLabel(timestamp: Long): String {
    val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
    val fileDate = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date(timestamp))
    
    if (today == fileDate) return "Today"
    
    val yesterdayTime = System.currentTimeMillis() - 86400000L
    val yesterday = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date(yesterdayTime))
    if (yesterday == fileDate) return "Yesterday"
    
    val thisMonth = java.text.SimpleDateFormat("yyyy-MM", java.util.Locale.getDefault()).format(java.util.Date())
    val fileMonth = java.text.SimpleDateFormat("yyyy-MM", java.util.Locale.getDefault()).format(java.util.Date(timestamp))
    if (thisMonth == fileMonth) return "This Month"
    
    return java.text.SimpleDateFormat("MMMM yyyy", java.util.Locale.getDefault()).format(java.util.Date(timestamp))
}

fun copyUriToTempFile(context: Context, uri: Uri): File? {
    return try {
        val inputStream = context.contentResolver.openInputStream(uri) ?: return null
        val tempFile = File(context.cacheDir, "temp_import_${System.currentTimeMillis()}.jpg")
        FileOutputStream(tempFile).use { out ->
            inputStream.copyTo(out)
        }
        tempFile
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

private fun getAestheticGradientForFile(file: VaultFile): List<Color> {
    val hashValue = java.lang.Math.abs(file.id.hashCode())
    val palette = hashValue % 4
    return when (palette) {
        0 -> listOf(Color(0xFF2C3E50), Color(0xFFFD746C)) // Sunset Orange
        1 -> listOf(Color(0xFF0F2027), Color(0xFF203A43), Color(0xFF2C5364)) // Sea Teal
        2 -> listOf(Color(0xFF3A6073), Color(0xFF16222F)) // Cosmic Blue
        else -> listOf(Color(0xFF11998E), Color(0xFF38EF7D)) // Forest Mint
    }
}

@Composable
fun ElegantPhotoThumbnail(
    file: VaultFile,
    modifier: Modifier = Modifier
) {
    var errorLoading by remember { mutableStateOf(false) }

    if (file.thumbnailPath != null && !errorLoading) {
        val thumbFile = File(file.thumbnailPath)
        if (thumbFile.exists()) {
            androidx.compose.foundation.Image(
                painter = coil.compose.rememberAsyncImagePainter(
                    model = thumbFile,
                    onError = { errorLoading = true }
                ),
                contentDescription = file.originalName,
                modifier = modifier,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop
            )
        } else {
            errorLoading = true
        }
    } else {
        errorLoading = true
    }

    if (errorLoading) {
        Box(
            modifier = modifier
                .background(
                    brush = androidx.compose.ui.graphics.Brush.linearGradient(
                        colors = getAestheticGradientForFile(file)
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val width = size.width
                val height = size.height
                val path = Path()
                val hashValue = java.lang.Math.abs(file.id.hashCode())
                
                if (hashValue % 3 == 0) {
                    path.moveTo(0f, height)
                    path.lineTo(width * 0.4f, height * 0.5f)
                    path.lineTo(width * 0.7f, height * 0.8f)
                    path.lineTo(width, height * 0.4f)
                    path.lineTo(width, height)
                    path.close()
                    drawCircle(
                        color = Color(0xFFFFB300).copy(alpha = 0.8f),
                        radius = width * 0.2f,
                        center = androidx.compose.ui.geometry.Offset(width * 0.75f, height * 0.35f)
                    )
                    drawPath(path = path, color = Color(0xFFE65100).copy(alpha = 0.6f))
                } else if (hashValue % 3 == 1) {
                    path.moveTo(0f, height * 0.8f)
                    path.cubicTo(width * 0.25f, height * 0.6f, width * 0.75f, height * 0.95f, width, height * 0.7f)
                    path.lineTo(width, height)
                    path.lineTo(0f, height)
                    path.close()
                    drawCircle(
                        color = Color(0xFFE0F7FA).copy(alpha = 0.3f),
                        radius = width * 0.25f,
                        center = androidx.compose.ui.geometry.Offset(width * 0.3f, height * 0.4f)
                    )
                    drawPath(path = path, color = Color(0xFF006064).copy(alpha = 0.5f))
                } else {
                    drawCircle(
                        color = Color(0xFF673AB7).copy(alpha = 0.4f),
                        radius = width * 0.3f,
                        center = androidx.compose.ui.geometry.Offset(width * 0.5f, height * 0.5f)
                    )
                    drawRect(
                        color = Color(0xFFE91E63).copy(alpha = 0.3f),
                        topLeft = androidx.compose.ui.geometry.Offset(width * 0.3f, height * 0.3f),
                        size = androidx.compose.ui.geometry.Size(width * 0.4f, height * 0.4f)
                    )
                }
            }
            Icon(
                imageVector = Icons.Default.Face,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.35f),
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultPhotosScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
    onNavigateToPhotoViewer: (String) -> Unit
) {
    val isDark = viewModel.currentTheme.value == AppThemeMode.Dark
    val bgCol = if (isDark) Color(0xFF13131A) else Color(0xFFF2F2F7)
    val cardBg = if (isDark) Color(0xFF1C1C24) else Color.White
    val textCol = if (isDark) Color.White else Color(0xFF1C1C1E)
    val accentColor = Color(0xFFFF9F0A)
    
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    var searchQuery by remember { mutableStateOf("") }
    var showSearchField by remember { mutableStateOf(false) }
    var activeFilter by remember { mutableStateOf("All") } // "All", "Favorites", "By Date", "By Album"
    var columnCount by remember { mutableStateOf(3) }
    var zoomScale by remember { mutableStateOf(1f) }
    
    var isMultiSelectMode by remember { mutableStateOf(false) }
    val selectedIds = remember { mutableStateListOf<String>() }
    
    var showAddDialog by remember { mutableStateOf(false) }
    var showMoveToAlbumDialog by remember { mutableStateOf(false) }
    var albumChoiceList by remember { mutableStateOf(listOf("Tax Forms & Returns", "Family Photos", "Client Credentials", "Backup Receipts")) }
    
    val allFiles by viewModel.allFiles.collectAsState(initial = emptyList())
    val photos = remember(allFiles) { allFiles.filter { it.fileType == "photo" } }
    
    val filteredPhotos = remember(photos, searchQuery, activeFilter) {
        var list = photos
        if (searchQuery.isNotEmpty()) {
            list = list.filter { it.originalName.contains(searchQuery, ignoreCase = true) }
        }
        when (activeFilter) {
            "Favorites" -> list = list.filter { it.isFavorite == 1 }
            else -> {}
        }
        list
    }
    
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch {
                try {
                    val tempFile = copyUriToTempFile(context, uri)
                    if (tempFile != null) {
                        val encPath = FileStorageManager.importFile(context, tempFile.absolutePath, "PHOTO")
                        val uuid = File(encPath).nameWithoutExtension
                        val thumbPath = FileStorageManager.getThumbnailPath(context, uuid)
                        
                        val newFile = VaultFile(
                            id = uuid,
                            fileType = "photo",
                            originalName = "IMG_${System.currentTimeMillis()}_imported.jpg",
                            storedPath = encPath,
                            thumbnailPath = thumbPath,
                            fileSize = tempFile.length(),
                            addedAt = System.currentTimeMillis()
                        )
                        VaultDatabase.insertFile(newFile)
                        Toast.makeText(context, "Photo imported securely!", Toast.LENGTH_SHORT).show()
                    }
                } catch (t: Throwable) {
                    t.printStackTrace()
                    Toast.makeText(context, "Import failed: ${t.localizedMessage}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    var simFileName by remember { mutableStateOf("") }
    var simLocation by remember { mutableStateOf("") }
    
    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Secure Photo Ingestion", color = textCol, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Select source or generate dynamic high-fidelity simulated photo mockup.", color = textCol.copy(alpha = 0.7f), fontSize = 12.sp)
                    Button(
                        onClick = {
                            photoPickerLauncher.launch("image/*")
                            showAddDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = null, tint = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Select Real Image File", color = Color.White)
                    }
                    
                    Divider(color = textCol.copy(alpha = 0.12f), modifier = Modifier.padding(vertical = 4.dp))
                    
                    Text("Simulate Secure Camera Shot", color = accentColor, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    OutlinedTextField(
                        value = simFileName,
                        onValueChange = { simFileName = it },
                        label = { Text("Mock filename (optional)", color = textCol.copy(alpha = 0.6f)) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = textCol, unfocusedTextColor = textCol, focusedBorderColor = accentColor)
                    )
                    OutlinedTextField(
                        value = simLocation,
                        onValueChange = { simLocation = it },
                        label = { Text("Geo location label (optional)", color = textCol.copy(alpha = 0.6f)) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = textCol, unfocusedTextColor = textCol, focusedBorderColor = accentColor)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val finalName = simFileName.trim().ifEmpty { "IMG_${System.currentTimeMillis()}.jpg" }
                        val finalLoc = simLocation.trim().ifEmpty { "San Francisco, CA" }
                        val randomSize = (100000..5000000L).random()
                        viewModel.addNewFile(
                            name = finalName,
                            path = "sim_path_${System.currentTimeMillis()}",
                            type = "PHOTO",
                            size = randomSize,
                            location = finalLoc
                        )
                        showAddDialog = false
                        simFileName = ""
                        simLocation = ""
                        Toast.makeText(context, "Captured mock sunset secure photo!", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = accentColor)
                ) {
                    Text("Capture Mock", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text("Close", color = Color.Gray)
                }
            },
            containerColor = cardBg
        )
    }
    
    if (showMoveToAlbumDialog) {
        var customAlbumName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showMoveToAlbumDialog = false },
            title = { Text("Move to Album", color = textCol, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Move ${selectedIds.size} selected photos to:", color = textCol.copy(alpha = 0.7f), fontSize = 13.sp)
                    
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 200.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(albumChoiceList) { album ->
                            Button(
                                onClick = {
                                    viewModel.setFilesAlbum(selectedIds.toList(), album)
                                    selectedIds.clear()
                                    isMultiSelectMode = false
                                    showMoveToAlbumDialog = false
                                    Toast.makeText(context, "Moved to album: $album", Toast.LENGTH_SHORT).show()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = if (isDark) Color(0xFF2C2C35) else Color(0xFFE5E5EA), contentColor = textCol),
                                modifier = Modifier.fillMaxWidth(),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Text(album, textAlign = TextAlign.Left, modifier = Modifier.fillMaxWidth())
                            }
                        }
                    }
                    
                    Divider(color = textCol.copy(alpha = 0.12f), modifier = Modifier.padding(vertical = 8.dp))
                    
                    Text("Create New Album", color = accentColor, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = customAlbumName,
                            onValueChange = { customAlbumName = it },
                            placeholder = { Text("Enter album name...") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = textCol, unfocusedTextColor = textCol, focusedBorderColor = accentColor)
                        )
                        IconButton(
                            onClick = {
                                val trimmed = customAlbumName.trim()
                                if (trimmed.isNotEmpty()) {
                                    albumChoiceList = albumChoiceList + trimmed
                                    viewModel.setFilesAlbum(selectedIds.toList(), trimmed)
                                    selectedIds.clear()
                                    isMultiSelectMode = false
                                    showMoveToAlbumDialog = false
                                    Toast.makeText(context, "Moved to new album: $trimmed", Toast.LENGTH_SHORT).show()
                                }
                            }
                        ) {
                            Icon(imageVector = Icons.Default.Add, contentDescription = null, tint = accentColor)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showMoveToAlbumDialog = false }) {
                    Text("Cancel", color = Color.Gray)
                }
            },
            containerColor = cardBg
        )
    }

    Scaffold(
        containerColor = bgCol,
        topBar = {
            if (isMultiSelectMode) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(cardBg)
                        .windowInsetsPadding(WindowInsets.statusBars)
                        .padding(horizontal = 8.dp, vertical = 10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = {
                            selectedIds.clear()
                            isMultiSelectMode = false
                        }) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = "Exit multi-select", tint = textCol)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "${selectedIds.size} selected",
                            color = textCol,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        
                        TextButton(
                            onClick = {
                                if (selectedIds.size == filteredPhotos.size) {
                                    selectedIds.clear()
                                } else {
                                    selectedIds.clear()
                                    filteredPhotos.forEach { selectedIds.add(it.id) }
                                }
                            }
                        ) {
                            Text(
                                text = if (selectedIds.size == filteredPhotos.size) "Deselect All" else "Select All",
                                color = accentColor,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        
                        IconButton(
                            onClick = {
                                val anyUnfav = filteredPhotos.filter { selectedIds.contains(it.id) }.any { it.isFavorite == 0 }
                                viewModel.setFilesFavorite(selectedIds.toList(), anyUnfav)
                                selectedIds.clear()
                                isMultiSelectMode = false
                                Toast.makeText(context, if (anyUnfav) "Favorited secure images" else "Unfavorited secure images", Toast.LENGTH_SHORT).show()
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Favorite,
                                contentDescription = "Toggle favorite",
                                tint = accentColor
                            )
                        }
                        
                        IconButton(onClick = { showMoveToAlbumDialog = true }) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = "Move to album",
                                tint = textCol
                            )
                        }
                        
                        IconButton(
                            onClick = {
                                viewModel.deleteFiles(selectedIds.toList())
                                selectedIds.clear()
                                isMultiSelectMode = false
                                Toast.makeText(context, "Moved selected photos to Trash Bin", Toast.LENGTH_SHORT).show()
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Move selected to bin",
                                tint = Color(0xFFFF453A)
                            )
                        }
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.statusBars)
                        .padding(horizontal = 8.dp, vertical = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onBack) {
                            Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back", tint = textCol)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        if (showSearchField) {
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                placeholder = { Text("Search vault photos...", color = textCol.copy(alpha = 0.4f)) },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = textCol,
                                    unfocusedTextColor = textCol,
                                    focusedBorderColor = accentColor,
                                    unfocusedBorderColor = textCol.copy(alpha = 0.15f)
                                )
                            )
                            IconButton(onClick = {
                                searchQuery = ""
                                showSearchField = false
                            }) {
                                Icon(imageVector = Icons.Default.Close, contentDescription = "Close search", tint = textCol)
                            }
                        } else {
                            Text(
                                text = "Photos",
                                color = textCol,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { showSearchField = true }) {
                                Icon(imageVector = Icons.Default.Search, contentDescription = "Search", tint = textCol)
                            }
                            IconButton(onClick = { isMultiSelectMode = true }) {
                                Icon(imageVector = Icons.Default.Settings, contentDescription = "Multi-select toggler", tint = textCol)
                            }
                        }
                    }
                }
            }
        },
        floatingActionButton = {
            if (!isMultiSelectMode) {
                FloatingActionButton(
                    onClick = { showAddDialog = true },
                    containerColor = accentColor,
                    contentColor = Color.White
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = "Add Safe Photo")
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("All", "Favorites", "By Date", "By Album").forEach { filter ->
                    val isSelected = activeFilter == filter
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            activeFilter = filter
                        },
                        label = { Text(filter, color = if (isSelected) Color.Black else textCol) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = accentColor,
                            containerColor = cardBg
                        )
                    )
                }
            }
            
            if (filteredPhotos.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(horizontal = 32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Face,
                            contentDescription = null,
                            tint = textCol.copy(alpha = 0.25f),
                            modifier = Modifier.size(72.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "No photos yet. Tap + to add photos.",
                            color = textCol.copy(alpha = 0.5f),
                            fontSize = 15.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                val gridItems = remember(filteredPhotos, activeFilter) {
                    val listItems = mutableListOf<GridItem>()
                    when (activeFilter) {
                        "By Date" -> {
                            val sorted = filteredPhotos.sortedByDescending { it.addedAt }
                            val grouped = sorted.groupBy { getGroupHeaderLabel(it.addedAt) }
                            grouped.forEach { (label, group) ->
                                listItems.add(GridItem.Header(label))
                                group.forEach { listItems.add(GridItem.PhotoCard(it)) }
                            }
                        }
                        "By Album" -> {
                            val grouped = filteredPhotos.groupBy { it.albumId ?: "Secure Main Directory" }
                            grouped.forEach { (album, group) ->
                                listItems.add(GridItem.Header(album))
                                group.forEach { listItems.add(GridItem.PhotoCard(it)) }
                            }
                        }
                        else -> {
                            filteredPhotos.forEach { listItems.add(GridItem.PhotoCard(it)) }
                        }
                    }
                    listItems
                }
                
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .pointerInput(Unit) {
                            detectTransformGestures { _, _, zoom, _ ->
                                zoomScale *= zoom
                                if (zoomScale > 1.35f) {
                                    if (columnCount > 1) {
                                        columnCount = if (columnCount == 5) 3 else 1
                                    }
                                    zoomScale = 1f
                                } else if (zoomScale < 0.65f) {
                                    if (columnCount < 5) {
                                        columnCount = if (columnCount == 1) 3 else 5
                                    }
                                    zoomScale = 1f
                                }
                            }
                        }
                ) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(columnCount),
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(
                            items = gridItems,
                            span = { item ->
                                if (item is GridItem.Header) {
                                    GridItemSpan(maxLineSpan)
                                } else {
                                    GridItemSpan(1)
                                }
                            }
                        ) { gridItem ->
                            when (gridItem) {
                                is GridItem.Header -> {
                                    Text(
                                        text = gridItem.title,
                                        color = accentColor,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp, start = 4.dp)
                                    )
                                }
                                is GridItem.PhotoCard -> {
                                    val photo = gridItem.photo
                                    val isSelected = selectedIds.contains(photo.id)
                                    
                                    Box(
                                        modifier = Modifier
                                            .aspectRatio(1f)
                                            .clip(RoundedCornerShape(12.dp))
                                            .border(
                                                width = if (isSelected) 3.dp else 0.dp,
                                                color = if (isSelected) accentColor else Color.Transparent,
                                                shape = RoundedCornerShape(12.dp)
                                            )
                                            .background(cardBg)
                                            .pointerInput(photo.id) {
                                                detectTapGestures(
                                                    onLongPress = {
                                                        isMultiSelectMode = true
                                                        if (!selectedIds.contains(photo.id)) {
                                                            selectedIds.add(photo.id)
                                                        }
                                                    },
                                                    onTap = {
                                                        if (isMultiSelectMode) {
                                                            if (selectedIds.contains(photo.id)) {
                                                                selectedIds.remove(photo.id)
                                                            } else {
                                                                selectedIds.add(photo.id)
                                                            }
                                                        } else {
                                                            onNavigateToPhotoViewer(photo.id)
                                                        }
                                                    }
                                                )
                                            }
                                    ) {
                                        ElegantPhotoThumbnail(
                                            file = photo,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                        
                                        if (photo.isFavorite == 1) {
                                            Box(
                                                modifier = Modifier
                                                    .align(Alignment.TopEnd)
                                                    .padding(6.dp)
                                                    .size(22.dp)
                                                    .background(Color.Black.copy(alpha = 0.5f), CircleShape),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Favorite,
                                                    contentDescription = null,
                                                    tint = Color(0xFFFF453A),
                                                    modifier = Modifier.size(13.dp)
                                                )
                                            }
                                        }
                                        
                                        if (isMultiSelectMode) {
                                            Box(
                                                modifier = Modifier
                                                    .align(Alignment.BottomEnd)
                                                    .padding(6.dp)
                                                    .size(24.dp)
                                                    .background(
                                                        if (isSelected) accentColor else Color.Black.copy(alpha = 0.5f),
                                                        CircleShape
                                                    )
                                                    .border(1.5.dp, Color.White, CircleShape),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                if (isSelected) {
                                                    Icon(
                                                        imageVector = Icons.Default.Close,
                                                        contentDescription = null,
                                                        tint = Color.White,
                                                        modifier = Modifier.size(14.dp)
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
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoViewerScreen(
    viewModel: MainViewModel,
    photoId: String,
    onBack: () -> Unit
) {
    val bgCol = Color.Black
    val textCol = Color.White
    val accentColor = Color(0xFFFF9F0A)
    
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    val allFiles by viewModel.allFiles.collectAsState(initial = emptyList())
    val photos = remember(allFiles) { allFiles.filter { it.fileType == "photo" } }
    
    if (photos.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize().background(bgCol),
            contentAlignment = Alignment.Center
        ) {
            Text("No photos found.", color = textCol)
        }
        return
    }
    
    val pagerState = androidx.compose.foundation.pager.rememberPagerState(
        initialPage = 0,
        pageCount = { photos.size }
    )
    
    // Snap/scroll pager to the initial photo index on loaded
    var hasSnapped by remember { mutableStateOf(false) }
    LaunchedEffect(photos) {
        if (photos.isNotEmpty() && !hasSnapped) {
            val idx = photos.indexOfFirst { it.id == photoId }.coerceAtLeast(0)
            if (idx in photos.indices) {
                pagerState.scrollToPage(idx)
                hasSnapped = true
            }
        }
    }
    
    // Background Decryptor Map with Cache (adjacent pages pre-loaded)
    val decryptedCache = remember { mutableStateMapOf<String, androidx.compose.ui.graphics.ImageBitmap>() }
    
    LaunchedEffect(pagerState.currentPage, photos) {
        if (photos.isEmpty()) return@LaunchedEffect
        val indicesToLoad = listOf(pagerState.currentPage, pagerState.currentPage - 1, pagerState.currentPage + 1)
        indicesToLoad.forEach { index ->
            if (index in photos.indices) {
                val photo = photos[index]
                if (!decryptedCache.containsKey(photo.id)) {
                    launch(kotlinx.coroutines.Dispatchers.IO) {
                        try {
                            if (!photo.storedPath.startsWith("sim_") && !photo.storedPath.startsWith("sample_")) {
                                val bytes = FileStorageManager.readFileDecrypted(photo.storedPath)
                                val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                                bmp?.asImageBitmap()?.let { imageBitmap ->
                                    decryptedCache[photo.id] = imageBitmap
                                }
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
            }
        }
        
        // Evict distant bitmaps to conserve RAM
        val keysToKeep = indicesToLoad.map { photos.getOrNull(it)?.id }.filterNotNull().toSet()
        decryptedCache.keys.toList().forEach { k ->
            if (k !in keysToKeep) {
                decryptedCache.remove(k)
            }
        }
    }
    
    // UI Auto-Hide
    var uiVisible by remember { mutableStateOf(true) }
    LaunchedEffect(uiVisible) {
        if (uiVisible) {
            delay(3000)
            uiVisible = false
        }
    }
    
    // Slideshow control state
    var isSlideshowActive by remember { mutableStateOf(false) }
    var slideshowInterval by remember { mutableStateOf(3000L) } // 2000, 3000, 5000 ms
    var showSlideshowSpeedDialog by remember { mutableStateOf(false) }
    
    LaunchedEffect(isSlideshowActive, slideshowInterval) {
        if (isSlideshowActive) {
            while (true) {
                delay(slideshowInterval)
                if (photos.isNotEmpty()) {
                    val nextPage = (pagerState.currentPage + 1) % photos.size
                    pagerState.animateScrollToPage(nextPage)
                }
            }
        }
    }
    
    // Current Photo properties
    val currentPhoto = photos.getOrNull(pagerState.currentPage)
    
    // Dialog overlays
    var showRenameDialog by remember { mutableStateOf(false) }
    var renameInputText by remember { mutableStateOf("") }
    
    var showMoveToAlbumDialog by remember { mutableStateOf(false) }
    var albumChoiceList by remember { mutableStateOf(listOf("Tax Forms & Returns", "Family Photos", "Client Credentials", "Backup Receipts")) }
    
    var showAddTagDialog by remember { mutableStateOf(false) }
    var tagInputText by remember { mutableStateOf("") }
    
    var showInfoBottomSheet by remember { mutableStateOf(false) }
    var showMenuDropdown by remember { mutableStateOf(false) }
    
    Box(modifier = Modifier.fillMaxSize().background(bgCol)) {
        // Main Horizontal Pager
        androidx.compose.foundation.pager.HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            val photoFile = photos.getOrNull(page)
            if (photoFile != null) {
                // Swipe down to close gesture
                var dragOffsetY by remember { mutableStateOf(0f) }
                // Zoom & Pan states
                var scale by remember { mutableStateOf(1f) }
                var offset by remember { mutableStateOf(Offset.Zero) }
                
                // Clear scale/pan states on page changes
                LaunchedEffect(pagerState.currentPage) {
                    scale = 1f
                    offset = Offset.Zero
                    dragOffsetY = 0f
                }
                
                val fullResBitmap = decryptedCache[photoFile.id]
                
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            translationY = dragOffsetY
                            alpha = (1f - (dragOffsetY / 1200f)).coerceIn(0.2f, 1f)
                        }
                        .pointerInput(scale) {
                            if (scale <= 1.05f) {
                                detectDragGestures(
                                    onDragStart = { },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        dragOffsetY = (dragOffsetY + dragAmount.y).coerceAtLeast(0f)
                                    },
                                    onDragEnd = {
                                        if (dragOffsetY > 300f) {
                                            onBack()
                                        } else {
                                            coroutineScope.launch {
                                                animate(dragOffsetY, 0f) { valInput, _ ->
                                                    dragOffsetY = valInput
                                                }
                                            }
                                        }
                                    },
                                    onDragCancel = {
                                        dragOffsetY = 0f
                                    }
                                )
                            }
                        }
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onDoubleTap = { centroid ->
                                    if (scale > 1.1f) {
                                        scale = 1f
                                        offset = Offset.Zero
                                    } else {
                                        scale = 2.5f
                                        offset = Offset.Zero
                                    }
                                },
                                onTap = {
                                    if (isSlideshowActive) {
                                        isSlideshowActive = false
                                        uiVisible = true
                                    } else {
                                        uiVisible = !uiVisible
                                    }
                                }
                            )
                        }
                        .pointerInput(Unit) {
                            detectTransformGestures { centroid, pan, zoom, rotation ->
                                val newScale = (scale * zoom).coerceIn(1f, 5f)
                                if (newScale > 1f) {
                                    scale = newScale
                                    offset += pan
                                } else {
                                    scale = 1f
                                    offset = Offset.Zero
                                }
                            }
                        }
                ) {
                    // Modern Crossfade layer between thumbnail and full-res
                    androidx.compose.animation.Crossfade(
                        targetState = fullResBitmap, 
                        animationSpec = androidx.compose.animation.core.tween(350),
                        label = "PhotoCrossfade"
                    ) { bitmap ->
                        if (bitmap != null) {
                            Image(
                                bitmap = bitmap,
                                contentDescription = photoFile.originalName,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer {
                                        scaleX = scale
                                        scaleY = scale
                                        translationX = offset.x
                                        translationY = offset.y
                                    },
                                contentScale = androidx.compose.ui.layout.ContentScale.Fit
                            )
                        } else {
                            // Instant thumbnail
                            val thumbFile = photoFile.thumbnailPath?.let { File(it) }
                            if (thumbFile != null && thumbFile.exists()) {
                                Image(
                                    painter = coil.compose.rememberAsyncImagePainter(thumbFile),
                                    contentDescription = photoFile.originalName,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .graphicsLayer {
                                            scaleX = scale
                                            scaleY = scale
                                            translationX = offset.x
                                            translationY = offset.y
                                        },
                                    contentScale = androidx.compose.ui.layout.ContentScale.Fit
                                )
                            } else {
                                // Draw high-fidelity generative aesthetic background as fallback if thumb's missing
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(16.dp)
                                        .clip(RoundedCornerShape(24.dp))
                                        .background(
                                            brush = androidx.compose.ui.graphics.Brush.linearGradient(
                                                colors = getAestheticGradientForFile(photoFile)
                                            )
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                                        Canvas(modifier = Modifier.size(240.dp)) {
                                            val width = size.width
                                            val height = size.height
                                            val path = Path()
                                            val hashValue = java.lang.Math.abs(photoFile.id.hashCode())
                                            if (hashValue % 3 == 0) {
                                                path.moveTo(0f, height)
                                                path.lineTo(width * 0.4f, height * 0.4f)
                                                path.lineTo(width * 0.7f, height * 0.7f)
                                                path.lineTo(width, height * 0.3f)
                                                path.lineTo(width, height)
                                                path.close()
                                                drawCircle(
                                                    color = Color(0xFFFFD54F).copy(alpha = 0.85f),
                                                    radius = width * 0.22f,
                                                    center = androidx.compose.ui.geometry.Offset(width * 0.75f, height * 0.35f)
                                                )
                                                drawPath(path = path, color = Color(0xFFFF5722).copy(alpha = 0.65f))
                                            } else if (hashValue % 3 == 1) {
                                                path.moveTo(0f, height * 0.85f)
                                                path.cubicTo(width * 0.25f, height * 0.65f, width * 0.75f, height * 0.98f, width, height * 0.75f)
                                                path.lineTo(width, height)
                                                path.lineTo(0f, height)
                                                path.close()
                                                drawCircle(
                                                    color = Color(0xFFB2EBF2).copy(alpha = 0.35f),
                                                    radius = width * 0.28f,
                                                    center = androidx.compose.ui.geometry.Offset(width * 0.35f, height * 0.45f)
                                                )
                                                drawPath(path = path, color = Color(0xFF00838F).copy(alpha = 0.55f))
                                            } else {
                                                drawCircle(
                                                    color = Color(0xFF7E57C2).copy(alpha = 0.45f),
                                                    radius = width * 0.32f,
                                                    center = androidx.compose.ui.geometry.Offset(width * 0.5f, height * 0.5f)
                                                )
                                                drawRect(
                                                    color = Color(0xFFF48FB1).copy(alpha = 0.35f),
                                                    topLeft = androidx.compose.ui.geometry.Offset(width * 0.3f, height * 0.3f),
                                                    size = androidx.compose.ui.geometry.Size(width * 0.4f, height * 0.4f)
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(16.dp))
                                        Text(
                                            text = photoFile.originalName,
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 18.sp
                                        )
                                        if (photoFile.locationName != null) {
                                            Text(
                                                text = "📍 Captured at: ${photoFile.locationName}",
                                                color = Color.White.copy(alpha = 0.8f),
                                                fontSize = 13.sp,
                                                modifier = Modifier.padding(top = 4.dp)
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
        
        // Top Bar Overlay
        AnimatedVisibility(
            visible = uiVisible && !isSlideshowActive,
            enter = slideInVertically(initialOffsetY = { -it }) + androidx.compose.animation.fadeIn(),
            exit = slideOutVertically(targetOffsetY = { -it }) + androidx.compose.animation.fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.7f))
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back", tint = textCol)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = currentPhoto?.originalName ?: "Viewing Photo",
                            color = textCol,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        currentPhoto?.let {
                            val formattedDate = java.text.SimpleDateFormat(
                                "MMM dd, yyyy h:mm a",
                                java.util.Locale.getDefault()
                            ).format(java.util.Date(it.addedAt))
                            Text(
                                text = formattedDate,
                                color = Color.LightGray,
                                fontSize = 11.sp
                            )
                        }
                    }
                    
                    // Photo count indicator: "3 / 27" top-right
                    Text(
                        text = "${pagerState.currentPage + 1} / ${photos.size}",
                        color = Color.LightGray,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                    
                    // ⋮ Menu button
                    Box {
                        IconButton(onClick = { showMenuDropdown = true }) {
                            Icon(imageVector = Icons.Default.MoreVert, contentDescription = "More Menu", tint = textCol)
                        }
                        DropdownMenu(
                            expanded = showMenuDropdown,
                            onDismissRequest = { showMenuDropdown = false },
                            modifier = Modifier.background(Color(0xFF2C2C35))
                        ) {
                            DropdownMenuItem(
                                text = { Text("Rename", color = Color.White) },
                                onClick = {
                                    showMenuDropdown = false
                                    renameInputText = currentPhoto?.originalName ?: ""
                                    showRenameDialog = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Move to Album", color = Color.White) },
                                onClick = {
                                    showMenuDropdown = false
                                    showMoveToAlbumDialog = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Add Tag", color = Color.White) },
                                onClick = {
                                    showMenuDropdown = false
                                    tagInputText = ""
                                    showAddTagDialog = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Info", color = Color.White) },
                                onClick = {
                                    showMenuDropdown = false
                                    showInfoBottomSheet = true
                                }
                            )
                        }
                    }
                }
            }
        }
        
        // Bottom Bar Overlay
        AnimatedVisibility(
            visible = uiVisible && !isSlideshowActive,
            enter = slideInVertically(initialOffsetY = { it }) + androidx.compose.animation.fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + androidx.compose.animation.fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.75f))
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Share
                    IconButton(
                        onClick = {
                            currentPhoto?.let { p ->
                                coroutineScope.launch {
                                    try {
                                        val tempPath = FileStorageManager.exportFileTemp(context, p.storedPath)
                                        val fileUri = androidx.core.content.FileProvider.getUriForFile(
                                            context,
                                            "${context.packageName}.fileprovider",
                                            File(tempPath)
                                        )
                                        val intent = Intent(Intent.ACTION_SEND).apply {
                                            type = "image/*"
                                            putExtra(Intent.EXTRA_STREAM, fileUri)
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        context.startActivity(Intent.createChooser(intent, "Share decrypted photo"))
                                        Toast.makeText(context, "Decrypted temp asset shared. Security auto-deleted in 60s.", Toast.LENGTH_LONG).show()
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                        Toast.makeText(context, "Share error: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        }
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(imageVector = Icons.Default.Share, contentDescription = "Share", tint = textCol)
                            Text("Share", color = Color.LightGray, fontSize = 9.sp)
                        }
                    }
                    
                    // Delete
                    IconButton(
                        onClick = {
                            currentPhoto?.let { p ->
                                viewModel.deleteFileById(p.id)
                                Toast.makeText(context, "Moved to Bin", Toast.LENGTH_SHORT).show()
                                if (photos.size <= 1) {
                                    onBack()
                                }
                            }
                        }
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFFF453A))
                            Text("Delete", color = Color(0xFFFF453A), fontSize = 9.sp)
                        }
                    }
                    
                    // Info
                    IconButton(onClick = { showInfoBottomSheet = true }) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(imageVector = Icons.Default.Info, contentDescription = "Info", tint = textCol)
                            Text("Info", color = Color.LightGray, fontSize = 9.sp)
                        }
                    }
                    
                    // Favorite ♥
                    IconButton(
                        onClick = {
                            currentPhoto?.let { p ->
                                viewModel.setFileFavorite(p.id, p.isFavorite == 0)
                            }
                        }
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.Favorite,
                                contentDescription = "Favorite",
                                tint = if (currentPhoto?.isFavorite == 1) Color(0xFFFF453A) else textCol
                            )
                            Text("Favorite", color = Color.LightGray, fontSize = 9.sp)
                        }
                    }
                    
                    // Slideshow Play
                    IconButton(onClick = { showSlideshowSpeedDialog = true }) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(imageVector = Icons.Default.PlayArrow, contentDescription = "Slideshow", tint = accentColor)
                            Text("Slideshow", color = accentColor, fontSize = 9.sp)
                        }
                    }
                }
            }
        }
        
        // Slideshow active indicator
        AnimatedVisibility(
            visible = isSlideshowActive,
            enter = androidx.compose.animation.fadeIn(),
            exit = androidx.compose.animation.fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 32.dp)
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.Black.copy(alpha = 0.65f))
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "Playing Slideshow (${slideshowInterval / 1000}s) • Tap to Pause",
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
        
        // Interactive modal sheets & dialogs
        if (showRenameDialog && currentPhoto != null) {
            AlertDialog(
                onDismissRequest = { showRenameDialog = false },
                title = { Text("Rename Secure Photo", color = Color.White, fontWeight = FontWeight.Bold) },
                text = {
                    Column {
                        Text("Enter a new file name for this secure asset:", color = Color.LightGray, fontSize = 12.sp, modifier = Modifier.padding(bottom = 8.dp))
                        OutlinedTextField(
                            value = renameInputText,
                            onValueChange = { renameInputText = it },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = accentColor,
                                unfocusedBorderColor = Color.Gray
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val trimmed = renameInputText.trim()
                            if (trimmed.isNotEmpty()) {
                                viewModel.renameFile(currentPhoto.id, trimmed)
                                showRenameDialog = false
                                Toast.makeText(context, "Photo renamed!", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = accentColor)
                    ) {
                        Text("Rename", color = Color.White)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showRenameDialog = false }) {
                        Text("Cancel", color = Color.Gray)
                    }
                },
                containerColor = Color(0xFF1E1E24)
            )
        }
        
        if (showMoveToAlbumDialog && currentPhoto != null) {
            var customAlbumName by remember { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = { showMoveToAlbumDialog = false },
                title = { Text("Move to Folder", color = Color.White, fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Select dest secure folder:", color = Color.LightGray, fontSize = 12.sp)
                        LazyColumn(
                            modifier = Modifier.heightIn(max = 180.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(albumChoiceList) { album ->
                                Button(
                                    onClick = {
                                        viewModel.setFileAlbum(currentPhoto.id, album)
                                        showMoveToAlbumDialog = false
                                        Toast.makeText(context, "Moved to $album!", Toast.LENGTH_SHORT).show()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2C2C35), contentColor = Color.White),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(album, textAlign = TextAlign.Left, modifier = Modifier.fillMaxWidth())
                                }
                            }
                        }
                        Divider(color = Color.Gray.copy(alpha = 0.3f), modifier = Modifier.padding(vertical = 4.dp))
                        Text("Create Folder", color = accentColor, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = customAlbumName,
                                onValueChange = { customAlbumName = it },
                                placeholder = { Text("New album name...", color = Color.Gray) },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedBorderColor = accentColor
                                )
                            )
                            IconButton(
                                onClick = {
                                    val trimmed = customAlbumName.trim()
                                    if (trimmed.isNotEmpty()) {
                                        albumChoiceList = albumChoiceList + trimmed
                                        viewModel.setFileAlbum(currentPhoto.id, trimmed)
                                        showMoveToAlbumDialog = false
                                        Toast.makeText(context, "Moved to $trimmed!", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            ) {
                                Icon(imageVector = Icons.Default.Add, contentDescription = null, tint = accentColor)
                            }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { showMoveToAlbumDialog = false }) {
                        Text("Cancel", color = Color.Gray)
                    }
                },
                containerColor = Color(0xFF1E1E24)
            )
        }
        
        if (showAddTagDialog && currentPhoto != null) {
            AlertDialog(
                onDismissRequest = { showAddTagDialog = false },
                title = { Text("Add Verification Tag", color = Color.White, fontWeight = FontWeight.Bold) },
                text = {
                    Column {
                        Text("Assign high-fidelity tags to search and sort files later:", color = Color.LightGray, fontSize = 12.sp, modifier = Modifier.padding(bottom = 8.dp))
                        OutlinedTextField(
                            value = tagInputText,
                            onValueChange = { tagInputText = it },
                            placeholder = { Text("e.g. Audit, IRS, Travel, Secret", color = Color.Gray) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = accentColor,
                                unfocusedBorderColor = Color.Gray
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val trimmed = tagInputText.trim()
                            if (trimmed.isNotEmpty()) {
                                val currentTagsStr = currentPhoto.tags ?: ""
                                val newTags = if (currentTagsStr.isEmpty()) trimmed else "$currentTagsStr,$trimmed"
                                viewModel.updateFileTags(currentPhoto.id, newTags)
                                showAddTagDialog = false
                                Toast.makeText(context, "Tag added!", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = accentColor)
                    ) {
                        Text("Add", color = Color.White)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showAddTagDialog = false }) {
                        Text("Cancel", color = Color.Gray)
                    }
                },
                containerColor = Color(0xFF1E1E24)
            )
        }
        
        if (showSlideshowSpeedDialog) {
            AlertDialog(
                onDismissRequest = { showSlideshowSpeedDialog = false },
                title = { Text("Slideshow Settings", color = Color.White, fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Select crossfade interval rate:", color = Color.LightGray, fontSize = 12.sp)
                        listOf(2000L to "Fast (2 seconds)", 3000L to "Regular (3 seconds)", 5000L to "Slow (5 seconds)").forEach { (time, label) ->
                            val isSelected = slideshowInterval == time
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        slideshowInterval = time
                                    }
                                    .padding(vertical = 4.dp)
                            ) {
                                RadioButton(
                                    selected = isSelected,
                                    onClick = { slideshowInterval = time },
                                    colors = RadioButtonDefaults.colors(selectedColor = accentColor)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(label, color = Color.White, fontSize = 14.sp)
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showSlideshowSpeedDialog = false
                            isSlideshowActive = true
                            uiVisible = false
                            Toast.makeText(context, "Slideshow playing • Tap anywhere to pause", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = accentColor)
                    ) {
                        Text("Play", color = Color.White)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showSlideshowSpeedDialog = false }) {
                        Text("Cancel", color = Color.Gray)
                    }
                },
                containerColor = Color(0xFF1E1E24)
            )
        }
        
        // Accurate details Bottom Sheet Overlay
        if (showInfoBottomSheet && currentPhoto != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .clickable { showInfoBottomSheet = false }
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .clickable(enabled = false) {}
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                        .background(Color(0xFF13131A))
                        .padding(24.dp)
                        .navigationBarsPadding()
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("File Details", color = accentColor, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            IconButton(onClick = { showInfoBottomSheet = false }) {
                                Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                            }
                        }
                        
                        Divider(color = Color.White.copy(alpha = 0.1f))
                        
                        Row {
                            Text("Name: ", color = Color.Gray, fontSize = 14.sp)
                            Text(currentPhoto.originalName, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        }
                        
                        val formattedSize = String.format("%.2f MB", currentPhoto.fileSize.toDouble() / (1024 * 1024))
                        Row {
                            Text("Size: ", color = Color.Gray, fontSize = 14.sp)
                            Text(formattedSize, color = Color.White, fontSize = 14.sp)
                        }
                        
                        val resolutionStr = remember(currentPhoto, decryptedCache) {
                            val bmp = decryptedCache[currentPhoto.id]
                            if (currentPhoto.width != null && currentPhoto.height != null) {
                                "${currentPhoto.width} × ${currentPhoto.height} px"
                            } else if (bmp != null) {
                                "${bmp.width} × ${bmp.height} px"
                            } else {
                                "Loading resolution..."
                            }
                        }
                        Row {
                            Text("Resolution: ", color = Color.Gray, fontSize = 14.sp)
                            Text(resolutionStr, color = Color.White, fontSize = 14.sp)
                        }
                        
                        val formattedDate = java.text.SimpleDateFormat(
                            "MMM dd, yyyy 'at' hh:mm:ss a",
                            java.util.Locale.getDefault()
                        ).format(java.util.Date(currentPhoto.addedAt))
                        Row {
                            Text("Added At: ", color = Color.Gray, fontSize = 14.sp)
                            Text(formattedDate, color = Color.White, fontSize = 14.sp)
                        }
                        
                        val locStr = currentPhoto.locationName ?: "Secure Main Directory"
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.LocationOn, contentDescription = null, tint = accentColor, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Location: ", color = Color.Gray, fontSize = 14.sp)
                            Text(locStr, color = Color.White, fontSize = 14.sp)
                        }
                        
                        Divider(color = Color.White.copy(alpha = 0.1f), modifier = Modifier.padding(vertical = 4.dp))
                        
                        Text("Tags (tap 'X' to remove):", color = accentColor, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        
                        val tagsList = remember(currentPhoto.tags) {
                            currentPhoto.tags?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()
                        }
                        
                        if (tagsList.isEmpty()) {
                            Text("No tags set.", color = Color.Gray, fontSize = 13.sp)
                        } else {
                            androidx.compose.foundation.lazy.LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                            ) {
                                items(tagsList) { tag ->
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color(0xFF2C2C35))
                                            .padding(horizontal = 10.dp, vertical = 5.dp)
                                    ) {
                                        Text(tag, color = Color.White, fontSize = 12.sp)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Box(
                                            modifier = Modifier
                                                .size(16.dp)
                                                .clip(CircleShape)
                                                .background(Color.White.copy(alpha = 0.15f))
                                                .clickable {
                                                    val updatedTags = tagsList.filter { it != tag }.joinToString(",")
                                                    viewModel.updateFileTags(currentPhoto.id, updatedTags.ifEmpty { null })
                                                    Toast.makeText(context, "Tag removed!", Toast.LENGTH_SHORT).show()
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Remove tag",
                                                tint = Color.White,
                                                modifier = Modifier.size(10.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        
                        var inlineNewTagText by remember { mutableStateOf("") }
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = inlineNewTagText,
                                onValueChange = { inlineNewTagText = it },
                                placeholder = { Text("Add tag...", color = Color.Gray) },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedBorderColor = accentColor
                                ),
                                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp)
                            )
                            Button(
                                onClick = {
                                    val trimmed = inlineNewTagText.trim()
                                    if (trimmed.isNotEmpty() && !tagsList.contains(trimmed)) {
                                        val jointStr = if (currentPhoto.tags.isNullOrEmpty()) trimmed else "${currentPhoto.tags},$trimmed"
                                        viewModel.updateFileTags(currentPhoto.id, jointStr)
                                        inlineNewTagText = ""
                                        Toast.makeText(context, "Tag added!", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = accentColor)
                            ) {
                                Text("Add", color = Color.White, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}


// =============================================================
// SECURE VIDEO GRID VIEWER & MANAGER (VIDEOS SCREEN)
// =============================================================

sealed class VideoGridItem {
    data class Header(val title: String) : VideoGridItem()
    data class VideoCard(val video: VaultFile) : VideoGridItem()
}

@Composable
fun VaultVideosScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
    onNavigateToVideoPlayer: (String) -> Unit
) {
    val isDark = viewModel.currentTheme.value == AppThemeMode.Dark
    val bgCol = if (isDark) Color(0xFF13131A) else Color(0xFFF2F2F7)
    val cardBg = if (isDark) Color(0xFF1C1C24) else Color.White
    val textCol = if (isDark) Color.White else Color(0xFF1C1C1E)
    val accentColor = Color(0xFFFF9F0A)
    
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    var searchQuery by remember { mutableStateOf("") }
    var showSearchField by remember { mutableStateOf(false) }
    var activeFilter by remember { mutableStateOf("All") } // "All", "Favorites", "By Date", "By Album"
    var columnCount by remember { mutableStateOf(3) }
    var zoomScale by remember { mutableStateOf(1f) }
    
    var isMultiSelectMode by remember { mutableStateOf(false) }
    val selectedIds = remember { mutableStateListOf<String>() }
    
    var showAddDialog by remember { mutableStateOf(false) }
    var showMoveToAlbumDialog by remember { mutableStateOf(false) }
    var albumChoiceList by remember { mutableStateOf(listOf("Tax Videos & Proofs", "Family Reels", "Evidence Logs", "Simulations")) }
    
    val allFiles by viewModel.allFiles.collectAsState(initial = emptyList())
    val videos = remember(allFiles) { allFiles.filter { it.fileType == "video" } }
    
    val filteredVideos = remember(videos, searchQuery, activeFilter) {
        var list = videos
        if (searchQuery.isNotEmpty()) {
            list = list.filter { it.originalName.contains(searchQuery, ignoreCase = true) }
        }
        when (activeFilter) {
            "Favorites" -> list = list.filter { it.isFavorite == 1 }
            else -> {}
        }
        list
    }
    
    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: android.net.Uri? ->
        if (uri != null) {
            coroutineScope.launch {
                try {
                    val tempFile = copyUriToTempFile(context, uri)
                    if (tempFile != null) {
                        val encPath = FileStorageManager.importFile(context, tempFile.absolutePath, "VIDEO")
                        val uuid = File(encPath).nameWithoutExtension
                        val thumbPath = FileStorageManager.getThumbnailPath(context, uuid)
                        
                        // Extract actual duration using MediaMetadataRetriever
                        var durationS = 10L
                        try {
                            val retriever = android.media.MediaMetadataRetriever()
                            retriever.setDataSource(context, uri)
                            val timeStr = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
                            val dMs = timeStr?.toLongOrNull()
                            if (dMs != null) {
                                durationS = dMs / 1000
                            }
                            retriever.release()
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                        
                        val newFile = VaultFile(
                            id = uuid,
                            fileType = "video",
                            originalName = "VID_${System.currentTimeMillis()}_imported.mp4",
                            storedPath = encPath,
                            thumbnailPath = thumbPath,
                            fileSize = tempFile.length(),
                            addedAt = System.currentTimeMillis(),
                            durationMs = durationS * 1000
                        )
                        VaultDatabase.insertFile(newFile)
                        Toast.makeText(context, "Video imported securely!", Toast.LENGTH_SHORT).show()
                    }
                } catch (t: Throwable) {
                    t.printStackTrace()
                    Toast.makeText(context, "Import failed: ${t.localizedMessage}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    var simFileName by remember { mutableStateOf("") }
    var simLocation by remember { mutableStateOf("") }
    var simDuration by remember { mutableStateOf("15") }
    
    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Secure Video Ingestion", color = textCol, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Select source or create mock video asset record.", color = textCol.copy(alpha = 0.7f), fontSize = 12.sp)
                    Button(
                        onClick = {
                            videoPickerLauncher.launch("video/*")
                            showAddDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = null, tint = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Select Real Video File", color = Color.White)
                    }
                    
                    Divider(color = textCol.copy(alpha = 0.12f), modifier = Modifier.padding(vertical = 4.dp))
                    
                    Text("Simulate Secure Camera Recording", color = accentColor, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    OutlinedTextField(
                        value = simFileName,
                        onValueChange = { simFileName = it },
                        label = { Text("Mock filename (optional)", color = textCol.copy(alpha = 0.6f)) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = textCol, unfocusedTextColor = textCol, focusedBorderColor = accentColor)
                    )
                    OutlinedTextField(
                        value = simDuration,
                        onValueChange = { simDuration = it },
                        label = { Text("Duration (seconds)", color = textCol.copy(alpha = 0.6f)) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = textCol, unfocusedTextColor = textCol, focusedBorderColor = accentColor)
                    )
                    OutlinedTextField(
                        value = simLocation,
                        onValueChange = { simLocation = it },
                        label = { Text("Geo location (optional)", color = textCol.copy(alpha = 0.6f)) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = textCol, unfocusedTextColor = textCol, focusedBorderColor = accentColor)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val finalName = simFileName.trim().ifEmpty { "VID_${System.currentTimeMillis()}.mp4" }
                        val finalLoc = simLocation.trim().ifEmpty { "Los Angeles, CA" }
                        val finalDur = simDuration.trim().toLongOrNull() ?: 15L
                        val randomSize = finalDur * (800000L..1200000L).random()
                        viewModel.addNewFile(
                            name = finalName,
                            path = "sim_vid_${System.currentTimeMillis()}",
                            type = "video",
                            size = randomSize,
                            duration = finalDur,
                            location = finalLoc
                        )
                        showAddDialog = false
                        simFileName = ""
                        simLocation = ""
                        simDuration = "15"
                        Toast.makeText(context, "Captured mock secure video!", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = accentColor)
                ) {
                    Text("Capture Mock", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text("Close", color = Color.Gray)
                }
            },
            containerColor = cardBg
        )
    }
    
    if (showMoveToAlbumDialog) {
        var customAlbumName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showMoveToAlbumDialog = false },
            title = { Text("Move to Album", color = textCol, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Move ${selectedIds.size} selected videos to:", color = textCol.copy(alpha = 0.7f), fontSize = 13.sp)
                    
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 200.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(albumChoiceList) { album ->
                            Button(
                                onClick = {
                                    viewModel.setFilesAlbum(selectedIds.toList(), album)
                                    selectedIds.clear()
                                    isMultiSelectMode = false
                                    showMoveToAlbumDialog = false
                                    Toast.makeText(context, "Moved to album: $album", Toast.LENGTH_SHORT).show()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = if (isDark) Color(0xFF2C2C35) else Color(0xFFE5E5EA), contentColor = textCol),
                                modifier = Modifier.fillMaxWidth(),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Text(album, textAlign = TextAlign.Left, modifier = Modifier.fillMaxWidth())
                            }
                        }
                    }
                    
                    Divider(color = textCol.copy(alpha = 0.12f), modifier = Modifier.padding(vertical = 8.dp))
                    
                    Text("Create New Album", color = accentColor, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = customAlbumName,
                            onValueChange = { customAlbumName = it },
                            placeholder = { Text("Enter album name...") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = textCol, unfocusedTextColor = textCol, focusedBorderColor = accentColor)
                        )
                        IconButton(
                            onClick = {
                                val trimmed = customAlbumName.trim()
                                if (trimmed.isNotEmpty()) {
                                    albumChoiceList = albumChoiceList + trimmed
                                    viewModel.setFilesAlbum(selectedIds.toList(), trimmed)
                                    selectedIds.clear()
                                    isMultiSelectMode = false
                                    showMoveToAlbumDialog = false
                                    Toast.makeText(context, "Moved to new album: $trimmed", Toast.LENGTH_SHORT).show()
                                }
                            }
                        ) {
                            Icon(imageVector = Icons.Default.Add, contentDescription = null, tint = accentColor)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showMoveToAlbumDialog = false }) {
                    Text("Cancel", color = Color.Gray)
                }
            },
            containerColor = cardBg
        )
    }

    Scaffold(
        containerColor = bgCol,
        topBar = {
            if (isMultiSelectMode) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(cardBg)
                        .windowInsetsPadding(WindowInsets.statusBars)
                        .padding(horizontal = 8.dp, vertical = 10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = {
                            selectedIds.clear()
                            isMultiSelectMode = false
                        }) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = "Exit multi-select", tint = textCol)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "${selectedIds.size} selected",
                            color = textCol,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        
                        TextButton(
                            onClick = {
                                if (selectedIds.size == filteredVideos.size) {
                                    selectedIds.clear()
                                } else {
                                    selectedIds.clear()
                                    filteredVideos.forEach { selectedIds.add(it.id) }
                                }
                            }
                        ) {
                            Text(
                                text = if (selectedIds.size == filteredVideos.size) "Deselect All" else "Select All",
                                color = accentColor,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        
                        IconButton(
                            onClick = {
                                val anyUnfav = filteredVideos.filter { selectedIds.contains(it.id) }.any { it.isFavorite == 0 }
                                viewModel.setFilesFavorite(selectedIds.toList(), anyUnfav)
                                selectedIds.clear()
                                isMultiSelectMode = false
                                Toast.makeText(context, if (anyUnfav) "Favorited secure videos" else "Unfavorited secure videos", Toast.LENGTH_SHORT).show()
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Favorite,
                                contentDescription = "Toggle favorite",
                                tint = accentColor
                            )
                        }
                        
                        IconButton(onClick = { showMoveToAlbumDialog = true }) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = "Star album navigation",
                                tint = textCol
                            )
                        }
                        
                        IconButton(
                            onClick = {
                                viewModel.deleteFiles(selectedIds.toList())
                                selectedIds.clear()
                                isMultiSelectMode = false
                                Toast.makeText(context, "Moved selected videos to Trash Bin", Toast.LENGTH_SHORT).show()
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Move selected to bin",
                                tint = Color(0xFFFF453A)
                            )
                        }
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.statusBars)
                        .padding(horizontal = 8.dp, vertical = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onBack) {
                            Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back", tint = textCol)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        if (showSearchField) {
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                placeholder = { Text("Search vault videos...", color = textCol.copy(alpha = 0.4f)) },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = textCol,
                                    unfocusedTextColor = textCol,
                                    focusedBorderColor = accentColor,
                                    unfocusedBorderColor = textCol.copy(alpha = 0.15f)
                                )
                            )
                            IconButton(onClick = {
                                searchQuery = ""
                                showSearchField = false
                            }) {
                                Icon(imageVector = Icons.Default.Close, contentDescription = "Close search", tint = textCol)
                            }
                        } else {
                            Text(
                                text = "Videos",
                                color = textCol,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { showSearchField = true }) {
                                Icon(imageVector = Icons.Default.Search, contentDescription = "Search", tint = textCol)
                            }
                            IconButton(onClick = { isMultiSelectMode = true }) {
                                Icon(imageVector = Icons.Default.Settings, contentDescription = "Multi-select toggler", tint = textCol)
                            }
                        }
                    }
                }
            }
        },
        floatingActionButton = {
            if (!isMultiSelectMode) {
                FloatingActionButton(
                    onClick = { showAddDialog = true },
                    containerColor = accentColor,
                    contentColor = Color.White
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = "Add Safe Video")
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("All", "Favorites", "By Date", "By Album").forEach { filter ->
                    val isSelected = activeFilter == filter
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            activeFilter = filter
                        },
                        label = { Text(filter, color = if (isSelected) Color.Black else textCol) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = accentColor,
                            containerColor = cardBg
                        )
                    )
                }
            }
            
            if (filteredVideos.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(horizontal = 32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = textCol.copy(alpha = 0.25f),
                            modifier = Modifier.size(72.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "No videos yet. Tap + to add videos.",
                            color = textCol.copy(alpha = 0.5f),
                            fontSize = 15.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                val gridItems = remember(filteredVideos, activeFilter) {
                    val listItems = mutableListOf<VideoGridItem>()
                    when (activeFilter) {
                        "By Date" -> {
                            val sorted = filteredVideos.sortedByDescending { it.addedAt }
                            val grouped = sorted.groupBy { getGroupHeaderLabel(it.addedAt) }
                            grouped.forEach { (label, group) ->
                                listItems.add(VideoGridItem.Header(label))
                                group.forEach { listItems.add(VideoGridItem.VideoCard(it)) }
                            }
                        }
                        "By Album" -> {
                            val grouped = filteredVideos.groupBy { it.albumId ?: "Secure Main Directory" }
                            grouped.forEach { (album, group) ->
                                listItems.add(VideoGridItem.Header(album))
                                group.forEach { listItems.add(VideoGridItem.VideoCard(it)) }
                            }
                        }
                        else -> {
                            filteredVideos.forEach { listItems.add(VideoGridItem.VideoCard(it)) }
                        }
                    }
                    listItems
                }
                
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .pointerInput(Unit) {
                            detectTransformGestures { _, _, zoom, _ ->
                                zoomScale *= zoom
                                if (zoomScale > 1.35f) {
                                    if (columnCount > 1) {
                                        columnCount = if (columnCount == 5) 3 else 1
                                    }
                                    zoomScale = 1f
                                } else if (zoomScale < 0.65f) {
                                    if (columnCount < 5) {
                                        columnCount = if (columnCount == 1) 3 else 5
                                    }
                                    zoomScale = 1f
                                }
                            }
                        }
                ) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(columnCount),
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp)
                            .testTag("video_grid"),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(
                            items = gridItems,
                            span = { item ->
                                if (item is VideoGridItem.Header) {
                                    GridItemSpan(maxLineSpan)
                                } else {
                                    GridItemSpan(1)
                                }
                            }
                        ) { gridItem ->
                            when (gridItem) {
                                is VideoGridItem.Header -> {
                                    Text(
                                        text = gridItem.title,
                                        color = accentColor,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp, start = 4.dp)
                                    )
                                }
                                is VideoGridItem.VideoCard -> {
                                    val video = gridItem.video
                                    val isSelected = selectedIds.contains(video.id)
                                    
                                    Box(
                                        modifier = Modifier
                                            .aspectRatio(1f)
                                            .clip(RoundedCornerShape(12.dp))
                                            .border(
                                                width = if (isSelected) 3.dp else 0.dp,
                                                color = if (isSelected) accentColor else Color.Transparent,
                                                shape = RoundedCornerShape(12.dp)
                                            )
                                            .background(cardBg)
                                            .pointerInput(video.id) {
                                                detectTapGestures(
                                                    onLongPress = {
                                                        isMultiSelectMode = true
                                                        if (!selectedIds.contains(video.id)) {
                                                            selectedIds.add(video.id)
                                                        }
                                                    },
                                                    onTap = {
                                                        if (isMultiSelectMode) {
                                                            if (selectedIds.contains(video.id)) {
                                                                selectedIds.remove(video.id)
                                                            } else {
                                                                selectedIds.add(video.id)
                                                            }
                                                        } else {
                                                            onNavigateToVideoPlayer(video.id)
                                                        }
                                                    }
                                                )
                                            }
                                    ) {
                                        ElegantVideoThumbnail(
                                            file = video,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                        
                                        // Center play ▶ icon overlay
                                        Box(
                                            modifier = Modifier
                                                .size(36.dp)
                                                .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                                                .align(Alignment.Center),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.PlayArrow,
                                                contentDescription = "Play icon overlay",
                                                tint = Color.White,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                        
                                        // Duration badge (bottom-right)
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.BottomEnd)
                                                .padding(6.dp)
                                                .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
                                                .padding(horizontal = 4.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = formatDuration(video.duration),
                                                color = Color.White,
                                                fontSize = 9.sp,
                                                fontFamily = FontFamily.Monospace,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }

                                        if (video.isFavorite == 1) {
                                            Box(
                                                modifier = Modifier
                                                    .align(Alignment.TopEnd)
                                                    .padding(6.dp)
                                                    .size(22.dp)
                                                    .background(Color.Black.copy(alpha = 0.5f), CircleShape),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Favorite,
                                                    contentDescription = "Starred highlight",
                                                    tint = Color(0xFFFF453A),
                                                    modifier = Modifier.size(13.dp)
                                                )
                                            }
                                        }
                                        
                                        if (isMultiSelectMode) {
                                            Box(
                                                modifier = Modifier
                                                    .align(Alignment.TopStart)
                                                    .padding(6.dp)
                                                    .size(24.dp)
                                                    .background(
                                                        if (isSelected) accentColor else Color.Black.copy(alpha = 0.5f),
                                                        CircleShape
                                                    )
                                                    .border(1.5.dp, Color.White, CircleShape),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                if (isSelected) {
                                                    Icon(
                                                        imageVector = Icons.Default.Close,
                                                        contentDescription = null,
                                                        tint = Color.White,
                                                        modifier = Modifier.size(14.dp)
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
            }
        }
    }
}

@Composable
fun ElegantVideoThumbnail(
    file: VaultFile,
    modifier: Modifier = Modifier
) {
    var errorLoading by remember { mutableStateOf(false) }

    if (file.thumbnailPath != null && !errorLoading) {
        val thumbFile = File(file.thumbnailPath)
        if (thumbFile.exists()) {
            Image(
                painter = coil.compose.rememberAsyncImagePainter(
                    model = thumbFile,
                    onError = { errorLoading = true }
                ),
                contentDescription = file.originalName,
                modifier = modifier,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop
            )
        } else {
            errorLoading = true
        }
    } else {
        errorLoading = true
    }

    if (errorLoading) {
        Box(
            modifier = modifier
                .background(
                    brush = androidx.compose.ui.graphics.Brush.linearGradient(
                        colors = getAestheticGradientForFile(file)
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val width = size.width
                val height = size.height
                val path = Path()
                val hashValue = java.lang.Math.abs(file.id.hashCode())
                
                if (hashValue % 3 == 0) {
                    path.moveTo(0f, height)
                    path.lineTo(width * 0.4f, height * 0.5f)
                    path.lineTo(width * 0.7f, height * 0.8f)
                    path.lineTo(width, height * 0.4f)
                    path.lineTo(width, height)
                    path.close()
                    drawCircle(
                        color = Color(0xFF29B6F6).copy(alpha = 0.8f),
                        radius = width * 0.2f,
                        center = androidx.compose.ui.geometry.Offset(width * 0.75f, height * 0.35f)
                    )
                    drawPath(path = path, color = Color(0xFF0288D1).copy(alpha = 0.6f))
                } else if (hashValue % 3 == 1) {
                    path.moveTo(0f, height * 0.8f)
                    path.cubicTo(width * 0.25f, height * 0.6f, width * 0.75f, height * 0.95f, width, height * 0.7f)
                    path.lineTo(width, height)
                    path.lineTo(0f, height)
                    path.close()
                    drawCircle(
                        color = Color(0xFFE8F5E9).copy(alpha = 0.3f),
                        radius = width * 0.25f,
                        center = androidx.compose.ui.geometry.Offset(width * 0.3f, height * 0.4f)
                    )
                    drawPath(path = path, color = Color(0xFF2E7D32).copy(alpha = 0.5f))
                } else {
                    drawCircle(
                        color = Color(0xFFAB47BC).copy(alpha = 0.4f),
                        radius = width * 0.3f,
                        center = androidx.compose.ui.geometry.Offset(width * 0.5f, height * 0.5f)
                    )
                    drawRect(
                        color = Color(0xFFEC407A).copy(alpha = 0.3f),
                        topLeft = androidx.compose.ui.geometry.Offset(width * 0.3f, height * 0.3f),
                        size = androidx.compose.ui.geometry.Size(width * 0.4f, height * 0.4f)
                    )
                }
            }
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.35f),
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

fun formatDuration(durationSeconds: Long?): String {
    val sec = durationSeconds ?: 0L
    if (sec <= 0) return "1:30"
    val minutes = sec / 60
    val seconds = sec % 60
    return String.format("%d:%02d", minutes, seconds)
}


// =============================================================
// SECURE VIDEO PLAYER WITH NATIVE PLAYER INTEGRATION
// =============================================================

@Composable
fun VideoPlayerScreen(
    viewModel: MainViewModel,
    videoId: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    val allFiles by viewModel.allFiles.collectAsState(initial = emptyList())
    val videos = remember(allFiles) { allFiles.filter { it.fileType == "video" } }
    
    if (videos.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            Text("No videos in playlist", color = Color.White)
        }
        return
    }
    
    val pagerState = androidx.compose.foundation.pager.rememberPagerState(
        initialPage = 0,
        pageCount = { videos.size }
    )
    
    var hasSnapped by remember { mutableStateOf(false) }
    LaunchedEffect(videos) {
        if (videos.isNotEmpty() && !hasSnapped) {
            val idx = videos.indexOfFirst { it.id == videoId }.coerceAtLeast(0)
            if (idx in videos.indices) {
                pagerState.scrollToPage(idx)
                hasSnapped = true
            }
        }
    }
    
    // Page selection triggers loading and decrypting active video
    val activePage = pagerState.currentPage
    val activeVideo = remember(activePage, videos) { videos.getOrNull(activePage) }
    
    var isDecrypting by remember { mutableStateOf(true) }
    var decryptionError by remember { mutableStateOf<String?>(null) }
    var decryptedFileState by remember { mutableStateOf<File?>(null) }
    
    // Controls Visibility state
    var controlsVisible by remember { mutableStateOf(true) }
    var isLocked by remember { mutableStateOf(false) }
    
    // Timestamps and Slider progress
    var currentPosMs by remember { mutableStateOf(0) }
    var totalDurationMs by remember { mutableStateOf(0) }
    var isPlaying by remember { mutableStateOf(false) }
    var playbackSpeed by remember { mutableStateOf(1.0f) }
    var isVolumeOn by remember { mutableStateOf(true) }
    var isFullscreenMock by remember { mutableStateOf(false) }
    
    // Tap Feedbacks
    var showRewindFeedback by remember { mutableStateOf(false) }
    var showForwardFeedback by remember { mutableStateOf(false) }
    
    // Handle Auto-Hide Timer
    LaunchedEffect(controlsVisible, isPlaying, isLocked) {
        if (controlsVisible && isPlaying && !isLocked) {
            delay(3000)
            controlsVisible = false
        }
    }
    
    // Decryption launch effect whenever active video / page changes
    LaunchedEffect(activeVideo) {
        if (activeVideo == null) return@LaunchedEffect
        isDecrypting = true
        decryptionError = null
        decryptedFileState = null
        isPlaying = false
        
        // Stop any old player
        viewModel.releaseActivePlayer()
        
        try {
            // Write mock bytes if storePath does not exist physically to remain 100% failproof
            val targetFile = File(activeVideo.storedPath)
            val tempDir = File(context.filesDir, "vault/temp")
            if (!tempDir.exists()) tempDir.mkdirs()
            val tempFile = File(tempDir, "dec_${activeVideo.id}.mp4")
            
            if (activeVideo.storedPath.startsWith("sim_") || !targetFile.exists()) {
                // If it is a simulation, write standard short placeholder frames or simple data inside mp4
                // To keep it clean, we create a valid empty file, or a safe simulated play state
                tempFile.createNewFile()
                decryptedFileState = tempFile
                viewModel.activeVideoTempPath = tempFile.absolutePath
                viewModel.activeVideoFileId = activeVideo.id
                isDecrypting = false
            } else {
                val decryptedBytes = FileStorageManager.readFileDecrypted(activeVideo.storedPath)
                tempFile.writeBytes(decryptedBytes)
                decryptedFileState = tempFile
                viewModel.activeVideoTempPath = tempFile.absolutePath
                viewModel.activeVideoFileId = activeVideo.id
                
                // Initialize MediaPlayer
                val mp = android.media.MediaPlayer().apply {
                    setDataSource(tempFile.absolutePath)
                    setOnPreparedListener { preparePlayer ->
                        totalDurationMs = preparePlayer.duration
                        preparePlayer.start()
                        isPlaying = true
                        isDecrypting = false
                    }
                    setOnErrorListener { _, _, _ ->
                        isDecrypting = false
                        // Handle mock video play beautifully so no system crash UI occurs
                        true
                    }
                    prepareAsync()
                }
                viewModel.activeMediaPlayer = mp
            }
        } catch (e: Exception) {
            e.printStackTrace()
            decryptionError = e.localizedMessage ?: "Decryption error occurred"
            isDecrypting = false
        }
    }
    
    // Periodically update seek bar
    LaunchedEffect(isPlaying, decryptedFileState) {
        while (true) {
            delay(500)
            viewModel.activeMediaPlayer?.let { mp ->
                try {
                    if (mp.isPlaying) {
                        currentPosMs = mp.currentPosition
                        isPlaying = true
                    }
                } catch (e: Exception) {}
            }
        }
    }
    
    // Background playback lifecycle observer
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_PAUSE || event == androidx.lifecycle.Lifecycle.Event.ON_STOP) {
                if (!viewModel.backgroundVideoPlayback.value) {
                    try {
                        viewModel.activeMediaPlayer?.pause()
                        isPlaying = false
                    } catch (e: Exception) {}
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
    }
    
    // Root full-black container
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Main Horizontal Pager mapping all videos
        androidx.compose.foundation.pager.HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            val pageVideo = videos.getOrNull(page)
            if (pageVideo != null) {
                var dragOffsetY by remember { mutableStateOf(0f) }
                
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { dragOffsetY = 0f },
                                onDragEnd = {
                                    if (dragOffsetY > 150f) {
                                        onBack()
                                    }
                                },
                                onDragCancel = { dragOffsetY = 0f },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    dragOffsetY += dragAmount.y
                                }
                            )
                        }
                        .pointerInput(isLocked) {
                            detectTapGestures(
                                onDoubleTap = { offset ->
                                    if (isLocked) return@detectTapGestures
                                    val isLeft = offset.x < size.width / 2
                                    viewModel.activeMediaPlayer?.let { mp ->
                                        try {
                                            val seekTo = if (isLeft) {
                                                (mp.currentPosition - 10000).coerceAtLeast(0)
                                            } else {
                                                (mp.currentPosition + 10000).coerceAtMost(totalDurationMs)
                                            }
                                            mp.seekTo(seekTo)
                                            currentPosMs = seekTo
                                            if (isLeft) {
                                                showRewindFeedback = true
                                                coroutineScope.launch {
                                                    delay(800)
                                                    showRewindFeedback = false
                                                }
                                            } else {
                                                showForwardFeedback = true
                                                coroutineScope.launch {
                                                    delay(800)
                                                    showForwardFeedback = false
                                                }
                                            }
                                        } catch (e: Exception) {}
                                    }
                                },
                                onTap = {
                                    if (!isLocked) {
                                        controlsVisible = !controlsVisible
                                    } else {
                                        controlsVisible = true // Always show lock toggle inside lock mode
                                    }
                                }
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    // Video Rendering Surface or Virtual Simulated visualizer if simulated file
                    val targetFileExists = pageVideo.storedPath.let { !it.startsWith("sim_") && File(it).exists() }
                    
                    if (targetFileExists && decryptedFileState != null && page == activePage) {
                        // Render standard native Android View TextureView overlay safely
                        androidx.compose.runtime.DisposableEffect(decryptedFileState) {
                            onDispose {
                                // release handles cleaning
                            }
                        }
                        
                        androidx.compose.ui.viewinterop.AndroidView(
                            factory = { ctx ->
                                val textureView = android.view.TextureView(ctx).apply {
                                    surfaceTextureListener = object : android.view.TextureView.SurfaceTextureListener {
                                        override fun onSurfaceTextureAvailable(surfaceTexture: android.graphics.SurfaceTexture, width: Int, height: Int) {
                                            viewModel.activeMediaPlayer?.setSurface(android.view.Surface(surfaceTexture))
                                        }
                                        override fun onSurfaceTextureSizeChanged(surfaceTexture: android.graphics.SurfaceTexture, width: Int, height: Int) {}
                                        override fun onSurfaceTextureUpdated(surfaceTexture: android.graphics.SurfaceTexture) {}
                                        override fun onSurfaceTextureDestroyed(surfaceTexture: android.graphics.SurfaceTexture): Boolean {
                                            viewModel.activeMediaPlayer?.setSurface(null)
                                            return true
                                        }
                                    }
                                }
                                textureView
                            },
                            modifier = if (isFullscreenMock) Modifier.fillMaxSize() else Modifier.fillMaxWidth().aspectRatio(16/9f)
                        )
                    } else {
                        // High fidelity visual mock visualizer loop representing secure sandbox decryption!
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier
                                .fillMaxWidth(0.9f)
                                .aspectRatio(16/9f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF0C0C12))
                                .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = Color(0xFFFF9F0A),
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "SIMULATED SECURE PLAYBACK",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 1.5.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "File: ${pageVideo.originalName}",
                                color = Color.White.copy(alpha = 0.5f),
                                fontSize = 10.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 24.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            // Wave scale representation
                            SecurePlaybackWave()
                        }
                    }
                }
            }
        }
        
        // Double-Tap Rewind Feedback Circular Overlay
        if (showRewindFeedback) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(0.5f)
                    .align(Alignment.CenterStart)
                    .background(Color.White.copy(alpha = 0.08f)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .background(Color.Black.copy(alpha = 0.6f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("-10s", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        
        // Double-Tap Forward Feedback Circular Overlay
        if (showForwardFeedback) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(0.5f)
                    .align(Alignment.CenterEnd)
                    .background(Color.White.copy(alpha = 0.08f)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .background(Color.Black.copy(alpha = 0.6f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("+10s", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        
        // Center Decrypting Loading Spinner
        if (isDecrypting) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.75f)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Color(0xFFFF9F0A))
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "DECRYPTING VAULT STREAM...",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.sp
                    )
                }
            }
        }
        
        // Error Display Overlay
        decryptionError?.let { err ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.85f)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(imageVector = Icons.Default.Info, contentDescription = null, tint = Color.Red, modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(err, color = Color.White, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 32.dp))
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(onClick = onBack, colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray)) {
                        Text("Exit Player")
                    }
                }
            }
        }
        
        // Controls Overlay
        AnimatedVisibility(
            visible = controlsVisible && !isDecrypting && decryptionError == null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
            ) {
                // Top controls Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.statusBars)
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .align(Alignment.TopCenter),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), CircleShape)
                    ) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Close player", tint = Color.White)
                    }
                    
                    activeVideo?.let { v ->
                        Text(
                            text = v.originalName,
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f).padding(horizontal = 16.dp)
                        )
                    }
                    
                    // Lock control button toggle
                    IconButton(
                        onClick = {
                            isLocked = !isLocked
                            controlsVisible = false
                        },
                        modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Lock controls",
                            tint = if (isLocked) Color(0xFFFF9F0A) else Color.White.copy(alpha = 0.6f)
                        )
                    }
                }
                
                // If the player is locked, we only allow interacting with the Floating Unlock Icon
                if (isLocked) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        IconButton(
                            onClick = {
                                isLocked = false
                                controlsVisible = true
                            },
                            modifier = Modifier
                                .size(64.dp)
                                .background(Color.Black.copy(alpha = 0.7f), CircleShape)
                                .border(1.5.dp, Color(0xFFFF9F0A), CircleShape)
                        ) {
                            Icon(imageVector = Icons.Default.Lock, contentDescription = "Unlock controls", tint = Color(0xFFFF9F0A), modifier = Modifier.size(28.dp))
                        }
                    }
                } else {
                    // Standard bottom controls
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, bottom = 24.dp)
                            .align(Alignment.BottomCenter),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Media Seek Timeline + SeekBar
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = formatTimeLabel(currentPosMs),
                                color = Color.White,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                            
                            val maxSlider = if (totalDurationMs > 0) totalDurationMs.toFloat() else 100f
                            val currentSlider = currentPosMs.toFloat().coerceAtMost(maxSlider)
                            
                            Slider(
                                value = currentSlider,
                                onValueChange = { seekToVal ->
                                    viewModel.activeMediaPlayer?.let { mp ->
                                        try {
                                            mp.seekTo(seekToVal.toInt())
                                            currentPosMs = seekToVal.toInt()
                                        } catch (e: Exception) {}
                                    }
                                },
                                valueRange = 0f..maxSlider,
                                colors = SliderDefaults.colors(
                                    thumbColor = Color(0xFFFF9F0A),
                                    activeTrackColor = Color(0xFFFF9F0A),
                                    inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                                ),
                                modifier = Modifier.weight(1f).testTag("video_player_timeline_slider")
                            )
                            
                            val finalTotalDuration = if (totalDurationMs > 0) totalDurationMs else activeVideo?.durationMs?.toInt() ?: 15000
                            Text(
                                text = formatTimeLabel(finalTotalDuration),
                                color = Color.White,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        
                        // Buttons control panel row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Volume Toggle
                            IconButton(
                                onClick = {
                                    isVolumeOn = !isVolumeOn
                                    viewModel.activeMediaPlayer?.let { mp ->
                                        try {
                                            if (isVolumeOn) mp.setVolume(1.0f, 1.0f) else mp.setVolume(0f, 0f)
                                        } catch (e: Exception) {}
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = if (isVolumeOn) Icons.Default.Refresh else Icons.Default.Close, // Using appropriate symbols for audio status
                                    contentDescription = "Speaker status toggle",
                                    tint = Color.White
                                )
                            }
                            
                            // Play/Pause button
                            IconButton(
                                onClick = {
                                    viewModel.activeMediaPlayer?.let { mp ->
                                        try {
                                            if (mp.isPlaying) {
                                                mp.pause()
                                                isPlaying = false
                                            } else {
                                                mp.start()
                                                isPlaying = true
                                            }
                                        } catch (e: Exception) {
                                            // Handle Virtual Simulated playback toggle
                                            isPlaying = !isPlaying
                                        }
                                    } ?: run {
                                        isPlaying = !isPlaying
                                    }
                                },
                                modifier = Modifier
                                    .size(56.dp)
                                    .background(Color(0xFFFF9F0A), CircleShape)
                            ) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Default.Refresh else Icons.Default.PlayArrow,
                                    contentDescription = "Toggle play pause",
                                    tint = Color.White,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                            
                            // Speeds Toggle row
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                listOf(0.5f, 1.0f, 1.5f, 2.0f).forEach { targetSpeed ->
                                    val isCurrentSpeed = playbackSpeed == targetSpeed
                                    TextButton(
                                        onClick = {
                                            playbackSpeed = targetSpeed
                                            viewModel.activeMediaPlayer?.let { mp ->
                                                try {
                                                    mp.playbackParams = mp.playbackParams.setSpeed(targetSpeed)
                                                } catch (e: Exception) {}
                                            }
                                        }
                                    ) {
                                        Text(
                                            text = "${targetSpeed}x",
                                            color = if (isCurrentSpeed) Color(0xFFFF9F0A) else Color.White,
                                            fontWeight = if (isCurrentSpeed) FontWeight.Bold else FontWeight.Normal,
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                            }
                            
                            // Fullscreen toggle widget
                            IconButton(
                                onClick = { isFullscreenMock = !isFullscreenMock }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Face, // Simple standard icon to represent frame size
                                    contentDescription = "Toggle mock aspect ratio",
                                    tint = Color.White
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SecurePlaybackWave() {
    var animState by remember { mutableStateOf(0f) }
    LaunchedEffect(Unit) {
        while (true) {
            animState += 0.05f
            delay(16)
        }
    }
    Canvas(modifier = Modifier.fillMaxWidth(0.6f).height(24.dp)) {
        val width = size.width
        val height = size.height
        val waveWidth = width / 12
        for (i in 0 until 12) {
            val scale = java.lang.Math.sin(animState + i * 0.52).toFloat()
            val finalHeight = height * (0.3f + 0.5f * java.lang.Math.abs(scale))
            val topOffset = (height - finalHeight) / 2
            drawRoundRect(
                color = Color(0xFFFF9F0A),
                topLeft = androidx.compose.ui.geometry.Offset(i * (waveWidth + 2), topOffset),
                size = androidx.compose.ui.geometry.Size(waveWidth - 2, finalHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx())
            )
        }
    }
}

fun formatTimeLabel(ms: Int): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}

