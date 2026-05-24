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
import androidx.navigation.compose.rememberNavController
import com.example.ui.theme.MyApplicationTheme

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
        evalScrollState.animateScrollTo(evalScrollState.maxValue)
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
        } catch (e: Exception) {
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
    onBack: () -> Unit
) {
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
