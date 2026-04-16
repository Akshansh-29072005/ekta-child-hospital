package com.example.ektachildhospital

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.core.content.ContextCompat
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.example.ektachildhospital.api.RetrofitClient
import com.example.ektachildhospital.api.GlobalAuthHandler
import com.example.ektachildhospital.ui.components.ShimmerCard
import com.example.ektachildhospital.ui.components.ShimmerDoctorCard
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

// DataStore setup
val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")
val IS_LOGGED_IN = booleanPreferencesKey("is_logged_in")
val IS_DARK_MODE = booleanPreferencesKey("is_dark_mode")
val PUSH_NOTIFICATIONS_ENABLED = booleanPreferencesKey("push_notifications_enabled")
val JWT_TOKEN = androidx.datastore.preferences.core.stringPreferencesKey("jwt_token")

// API Configuration
val BASE_URL = BuildConfig.BASE_URL

// Define the colors from the design
val Primary = Color(0xFF005BB3)
val PrimaryContainer = Color(0xFF0073DF)
val OnPrimary = Color(0xFFFFFFFF)
val LightSurface = Color(0xFFF9F9FF)
val DarkSurface = Color(0xFF121212)
val OnSurfaceLight = Color(0xFF181C23)
val OnSurfaceDark = Color(0xFFE1E1E1)
val Secondary = Color(0xFF3F5F91)
val SurfaceContainerLowLight = Color(0xFFF1F3FE)
val SurfaceContainerLowDark = Color(0xFF1E1E1E)
val PrimaryFixed = Color(0xFFD6E3FF)
val SecondaryFixed = Color(0xFFD6E3FF)
val TertiaryFixed = Color(0xFFFFDBCB)
val Tertiary = Color(0xFF9A4100)
val OnPrimaryFixed = Color(0xFF001B3D)
val SurfaceVariantLight = Color(0xFFE0E2ED)
val SurfaceVariantDark = Color(0xFF2C2C2C)
val Outline = Color(0xFF717786)

enum class AppState {
    Splash, Login, Main
}

enum class Screen {
    Home, Schedule, Visits, Profile, PrivacyPolicy, TermsOfService, HelpCenter
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        setContent {
            val context = LocalContext.current
    val isDarkModeFlow = remember {
        context.dataStore.data.map { preferences -> preferences[IS_DARK_MODE] }
    }
    val isDarkModeSaved by isDarkModeFlow.collectAsState(initial = isSystemInDarkTheme())

            EktaHospitalTheme(darkTheme = isDarkModeSaved ?: isSystemInDarkTheme()) {
                AppNavigation()
            }
        }
    }
}

@Composable
fun AppNavigation() {
    var appState by remember { mutableStateOf(AppState.Splash) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Notification Permission Launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        scope.launch {
            context.dataStore.edit { it[PUSH_NOTIFICATIONS_ENABLED] = isGranted }
        }
    }

    // Request permission on first launch if not already handled
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val isPermissionGranted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            
            if (!isPermissionGranted) {
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
    
    val isLoggedInFlow = remember {
        context.dataStore.data.map { preferences -> preferences[IS_LOGGED_IN] ?: false }
    }
    val isLoggedIn by isLoggedInFlow.collectAsState(initial = false)

    // Remove the SplashScreen composable call from here as it's handled by the API
    AnimatedContent(
        targetState = appState,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "AppNavigation"
    ) { state ->
        when (state) {
            AppState.Splash -> {
                // Determine next state
                LaunchedEffect(isLoggedIn) {
                    appState = if (isLoggedIn) AppState.Main else AppState.Login
                }
            }
            AppState.Login -> LoginScreen(onLoginSuccess = {
                scope.launch {
                    context.dataStore.edit { it[IS_LOGGED_IN] = true }
                    appState = AppState.Main
                }
            })
            AppState.Main -> {
                MainScreen(onLogout = {
                    scope.launch {
                        context.dataStore.edit { it[IS_LOGGED_IN] = false }
                        context.dataStore.edit { it.remove(JWT_TOKEN) }
                        appState = AppState.Login
                    }
                })
            }
        }
    }
}

@Composable
fun SplashScreen(onFinished: () -> Unit) {
    LaunchedEffect(Unit) {
        delay(2000)
        onFinished()
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "Ekta Hospital",
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.Bold,
                    color = Primary,
                    fontSize = 36.sp
                )
            )
            Text(
                "Appointment System",
                style = MaterialTheme.typography.titleMedium.copy(
                    color = Primary.copy(alpha = 0.7f),
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 1.sp
                )
            )
            Spacer(modifier = Modifier.height(60.dp))
            Image(
                painter = painterResource(id = R.drawable.logo),
                contentDescription = "Logo",
                modifier = Modifier.size(200.dp),
                contentScale = ContentScale.Fit
            )
        }
    }
}

@Composable
fun LoginScreen(onLoginSuccess: () -> Unit) {
    var phoneNumber by remember { mutableStateOf("") }
    var otp by remember { mutableStateOf("") }
    var isOtpSent by remember { mutableStateOf(false) }
    var isSignUp by remember { mutableStateOf(false) }
    var fullName by remember { mutableStateOf("") }

    // Force Light Theme for Login/Signup Screen
    MaterialTheme(colorScheme = lightColorScheme(
        surface = Color.White,
        onSurface = Color(0xFF181C23),
        primary = Primary,
        onPrimary = OnPrimary,
        outline = Outline,
        surfaceVariant = Color(0xFFF1F3FE)
    )) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.White
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    "Ekta Hospital",
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = Primary,
                        fontSize = 32.sp
                    )
                )
                Text(
                    "Appointment System",
                    style = MaterialTheme.typography.titleMedium.copy(
                        color = Primary.copy(alpha = 0.7f),
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 1.sp
                    )
                )

                Spacer(modifier = Modifier.height(32.dp))

                Image(
                    painter = painterResource(id = R.drawable.logo),
                    contentDescription = "Hospital Logo",
                    modifier = Modifier.size(180.dp),
                    contentScale = ContentScale.Fit
                )

                Spacer(modifier = Modifier.height(32.dp))

                Text(
                    text = if (isOtpSent) "Enter OTP" else if (isSignUp) "Create Account" else "Welcome Back",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold, 
                        color = Primary,
                        fontSize = 28.sp
                    )
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (isOtpSent) "We've sent a code to your phone" else "Log in to your patient portal",
                    style = MaterialTheme.typography.bodyLarge.copy(
                        color = Color.Gray,
                        textAlign = TextAlign.Center
                    )
                )
                
                Spacer(modifier = Modifier.height(32.dp))

                if (!isOtpSent) {
                    if (isSignUp) {
                        OutlinedTextField(
                            value = fullName,
                            onValueChange = { fullName = it },
                            label = { Text("Full Name") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                    OutlinedTextField(
                        value = phoneNumber,
                        onValueChange = { phoneNumber = it },
                        label = { Text("Phone Number") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        shape = RoundedCornerShape(12.dp),
                        prefix = { Text("+91 ") }
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    val isFormValid = if (isSignUp) {
                        fullName.trim().isNotEmpty() && phoneNumber.length == 10
                    } else {
                        phoneNumber.length == 10
                    }

                    Button(
                        onClick = {
                            if (isFormValid) {
                                // TODO: Implement actual API call
                                isOtpSent = true
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Primary,
                            disabledContainerColor = Primary.copy(alpha = 0.5f)
                        ),
                        enabled = isFormValid
                    ) {
                        Text(
                            if (isSignUp) "Sign Up" else "Send OTP",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(onClick = { isSignUp = !isSignUp }) {
                        Text(
                            if (isSignUp) "Already have an account? Login" else "Don't have an account? Sign Up",
                            style = MaterialTheme.typography.bodyLarge.copy(
                                color = Primary,
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                    }
                } else {
                    OutlinedTextField(
                        value = otp,
                        onValueChange = { if (it.length <= 6) otp = it },
                        label = { Text("6-Digit OTP Code") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = RoundedCornerShape(12.dp)
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = onLoginSuccess,
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = RoundedCornerShape(12.dp),
                        enabled = otp.length == 6
                    ) {
                        Text("Verify & Continue", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                    }
                    TextButton(onClick = { isOtpSent = false }) {
                        Text("Change Phone Number", style = MaterialTheme.typography.bodyLarge.copy(color = Primary, fontWeight = FontWeight.SemiBold))
                    }
                }
                
                FooterSection(onNavigate = {})
            }
        }
    }
}

@Composable
fun MainScreen(onLogout: () -> Unit) {
    var currentScreen by remember { mutableStateOf(Screen.Home) }

    Scaffold(
        bottomBar = {
            if (currentScreen in listOf(Screen.Home, Screen.Schedule, Screen.Visits, Screen.Profile)) {
                BottomNavigationBar(
                    currentScreen = currentScreen,
                    onScreenSelected = { currentScreen = it }
                )
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            AnimatedContent(
                targetState = currentScreen,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "ScreenTransition"
            ) { screen ->
                when (screen) {
                    Screen.Home -> PatientDashboard("Alexander Sterling", onNavigate = { currentScreen = it })
                    Screen.Schedule -> ScheduleScreen(onNavigate = { currentScreen = it })
                    Screen.Visits -> VisitsScreen(onNavigate = { currentScreen = it })
                    Screen.Profile -> ProfileScreen(onLogout = onLogout, onNavigate = { currentScreen = it })
                    Screen.PrivacyPolicy -> LegalScreen(LegalType.PrivacyPolicy, onBack = { currentScreen = Screen.Home })
                    Screen.TermsOfService -> LegalScreen(LegalType.TermsOfService, onBack = { currentScreen = Screen.Home })
                    Screen.HelpCenter -> LegalScreen(LegalType.HelpCenter, onBack = { currentScreen = Screen.Home })
                }
            }
        }
    }
}

enum class LegalType { PrivacyPolicy, TermsOfService, HelpCenter }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LegalScreen(initialType: LegalType, onBack: () -> Unit) {
    val scrollState = rememberScrollState()
    val privacyPolicyY = remember { mutableStateOf(0f) }
    val termsOfServiceY = remember { mutableStateOf(0f) }
    val helpCenterY = remember { mutableStateOf(0f) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(initialType) {
        val targetY = when (initialType) {
            LegalType.PrivacyPolicy -> privacyPolicyY.value
            LegalType.TermsOfService -> termsOfServiceY.value
            LegalType.HelpCenter -> helpCenterY.value
        }
        scrollState.animateScrollTo(targetY.toInt())
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Legal & Help") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(24.dp)
                .verticalScroll(scrollState)
        ) {
            // Privacy Policy Section
            Column(modifier = Modifier.onGloballyPositioned { privacyPolicyY.value = it.positionInParent().y }) {
                Text(
                    "Privacy Policy",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold, color = Primary)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "This Privacy Policy describes how Ekta Child Hospital collects, uses, and shares your personal information. We take your privacy seriously and ensure that all medical data is handled according to healthcare regulations.",
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    "\n• We collect phone numbers for authentication.\n• Appointment history is stored for medical records.\n• We do not share your data with third-party advertisers.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray
                )
            }

            Spacer(modifier = Modifier.height(48.dp))
            HorizontalDivider(color = Outline.copy(alpha = 0.1f))
            Spacer(modifier = Modifier.height(48.dp))

            // Terms of Service Section
            Column(modifier = Modifier.onGloballyPositioned { termsOfServiceY.value = it.positionInParent().y }) {
                Text(
                    "Terms of Service",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold, color = Primary)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "By using the Ekta Child Hospital application, you agree to the following terms:",
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    "\n1. Accuracy: You must provide accurate information for appointments.\n2. Cancellations: Appointments should be cancelled at least 2 hours in advance.\n3. Emergency: This app is for scheduling and medical records. For emergencies, please visit the hospital directly or call emergency services.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray
                )
            }

            Spacer(modifier = Modifier.height(48.dp))
            HorizontalDivider(color = Outline.copy(alpha = 0.1f))
            Spacer(modifier = Modifier.height(48.dp))

            // Help Center Section
            Column(modifier = Modifier.onGloballyPositioned { helpCenterY.value = it.positionInParent().y }) {
                Text(
                    "Help Center",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold, color = Primary)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "Need assistance? We are here to help you navigate your healthcare journey.",
                    style = MaterialTheme.typography.bodyLarge
                )
                Spacer(modifier = Modifier.height(16.dp))
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Contact Us", fontWeight = FontWeight.Bold)
                        Text("Email: support@ektachildhospital.com", style = MaterialTheme.typography.bodyMedium)
                        Text("Phone: +91 99999 88888", style = MaterialTheme.typography.bodyMedium)
                        Text("Hours: 9:00 AM - 9:00 PM", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun EktaHospitalTheme(darkTheme: Boolean, content: @Composable () -> Unit) {
    val colorScheme = if (darkTheme) {
        darkColorScheme(
            primary = Primary,
            onPrimary = OnPrimary,
            surface = Color(0xFF1A1C1E),
            onSurface = Color(0xFFE2E2E6),
            secondary = Color(0xFFBBC7DB),
            onSecondary = Color(0xFF253140),
            surfaceVariant = Color(0xFF44474E),
            onSurfaceVariant = Color(0xFFC4C6D0),
            outline = Color(0xFF8E9099),
            primaryContainer = Color(0xFF004487),
            onPrimaryContainer = Color(0xFFD6E3FF),
            secondaryContainer = Color(0xFF3B4858),
            onSecondaryContainer = Color(0xFFD7E3F7)
        )
    } else {
        lightColorScheme(
            primary = Primary,
            onPrimary = OnPrimary,
            surface = LightSurface,
            onSurface = OnSurfaceLight,
            secondary = Secondary,
            onSecondary = OnPrimary,
            surfaceVariant = SurfaceVariantLight,
            onSurfaceVariant = OnSurfaceLight,
            outline = Outline
        )
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PatientDashboard(userName: String, onNavigate: (Screen) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var nextAppointment by remember { mutableStateOf<com.example.ektachildhospital.api.Appointment?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    fun fetchData() {
        isLoading = true
        scope.launch {
            try {
                val appointments = RetrofitClient.getApiService(context).getAppointments()
                nextAppointment = appointments.firstOrNull { it.status == "Upcoming" }
            } catch (e: Exception) {
                val result = snackbarHostState.showSnackbar(
                    message = "Failed to load dashboard data",
                    actionLabel = "Retry"
                )
                if (result == SnackbarResult.ActionPerformed) {
                    fetchData()
                }
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(Unit) {
        fetchData()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(id = R.drawable.logo),
                            contentDescription = null,
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape),
                            contentScale = ContentScale.Fit
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Ekta Hospital",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = Primary
                            )
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { /* TODO */ }) {
                        Icon(Icons.Default.Notifications, contentDescription = "Notifications", tint = PrimaryContainer)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f))
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(8.dp))
                WelcomeSection(userName)
            }
            item {
                if (isLoading) {
                    ShimmerCard()
                } else {
                    NextAppointmentCard(nextAppointment)
                }
            }
            item {
                QuickActionsSection(onNavigate = onNavigate)
                Spacer(modifier = Modifier.height(32.dp))
                FooterSection(onNavigate = onNavigate)
            }
        }
    }
}

@Composable
fun WelcomeSection(userName: String) {
    Column {
        Text(
            text = "Welcome $userName,",
            style = MaterialTheme.typography.headlineLarge.copy(
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.5).sp
            )
        )
        Text(
            text = "Your wellness journey is our priority today.",
            style = MaterialTheme.typography.bodyMedium.copy(
                fontStyle = FontStyle.Italic,
                color = Secondary,
                fontWeight = FontWeight.Medium
            )
        )
    }
}

@Composable
fun NextAppointmentCard(appointment: com.example.ektachildhospital.api.Appointment?) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(240.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Primary)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        colors = listOf(Primary, PrimaryContainer)
                    )
                )
                .padding(24.dp)
        ) {
            if (appointment == null) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Default.DateRange,
                        contentDescription = null,
                        tint = OnPrimary.copy(alpha = 0.5f),
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "No upcoming appointments",
                        style = MaterialTheme.typography.bodyLarge.copy(color = OnPrimary.copy(alpha = 0.8f))
                    )
                    TextButton(onClick = { /* Navigate to Schedule */ }) {
                        Text("Book Now", color = OnPrimary, fontWeight = FontWeight.Bold)
                    }
                }
            } else {
                Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Surface(
                            color = Color.White.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(100)
                        ) {
                            Text(
                                text = "NEXT APPOINTMENT",
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = OnPrimary,
                                    letterSpacing = 1.sp
                                )
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = appointment.doctorName,
                            style = MaterialTheme.typography.headlineSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = OnPrimary
                            )
                        )
                        Text(
                            text = "Specialist • Consultation",
                            style = MaterialTheme.typography.bodyLarge.copy(
                                color = OnPrimary.copy(alpha = 0.8f)
                            )
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            color = Color.White.copy(alpha = 0.1f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                Icons.Default.DateRange,
                                contentDescription = null,
                                tint = OnPrimary,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "DATE & TIME",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = OnPrimary.copy(alpha = 0.7f),
                                    fontWeight = FontWeight.Bold
                                )
                            )
                            Text(
                                text = "${appointment.date} • ${appointment.time}",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = OnPrimary,
                                    fontWeight = FontWeight.SemiBold
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun QuickActionsSection(onNavigate: (Screen) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            text = "QUICK ACTIONS",
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                color = Secondary,
                letterSpacing = 1.sp
            )
        )
        QuickActionButton(
            text = "Book Appointment",
            icon = Icons.Default.AddCircle,
            containerColor = PrimaryFixed,
            iconColor = Primary,
            onClick = { onNavigate(Screen.Schedule) }
        )
        QuickActionButton(
            text = "My Records",
            icon = Icons.Default.Info,
            containerColor = SecondaryFixed,
            iconColor = Secondary,
            onClick = { onNavigate(Screen.Visits) }
        )
    }
}

@Composable
fun QuickActionButton(text: String, icon: ImageVector, containerColor: Color, iconColor: Color, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 1.dp
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(48.dp),
                    color = containerColor,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = iconColor,
                        modifier = Modifier.padding(12.dp)
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = Color.LightGray
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleScreen(onNavigate: (Screen) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var doctors by remember { mutableStateOf<List<com.example.ektachildhospital.api.Doctor>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    fun fetchDoctors() {
        isLoading = true
        scope.launch {
            try {
                doctors = RetrofitClient.getApiService(context).getDoctors()
            } catch (e: Exception) {
                val result = snackbarHostState.showSnackbar(
                    message = "Failed to load doctors",
                    actionLabel = "Retry"
                )
                if (result == SnackbarResult.ActionPerformed) {
                    fetchDoctors()
                }
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(Unit) {
        fetchDoctors()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(id = R.drawable.logo),
                            contentDescription = null,
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape),
                            contentScale = ContentScale.Fit
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Ekta Hospital",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = Primary
                            )
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { /* TODO */ }) {
                        Icon(Icons.Default.Notifications, contentDescription = "Notifications", tint = Primary)
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(32.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Find your specialist",
                        style = MaterialTheme.typography.headlineLarge.copy(
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = (-0.5).sp
                        )
                    )
                    Text(
                        text = "Book world-class care at your convenience.",
                        style = MaterialTheme.typography.bodyLarge.copy(
                            color = Secondary,
                            fontWeight = FontWeight.Medium
                        )
                    )
                }
            }

            if (isLoading) {
                items(5) {
                    ShimmerDoctorCard()
                }
            } else {
                item {
                    // Search Bar
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Row(
                            modifier = Modifier.padding(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = null,
                                modifier = Modifier.padding(horizontal = 12.dp),
                                tint = Outline
                            )
                            Text(
                                "Search doctors...",
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyMedium.copy(color = Outline)
                            )
                            Button(
                                onClick = { },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Primary)
                            ) {
                                Text("Search")
                            }
                        }
                    }
                }

                item {
                    // Specialties Section
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Bottom
                        ) {
                            Text("Browse Specialties", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
                            Text("View All", style = MaterialTheme.typography.labelLarge.copy(color = Primary, fontWeight = FontWeight.Bold))
                        }
                        
                        val specialties = listOf(
                            Triple("General Physician", Icons.Default.Favorite, PrimaryFixed),
                            Triple("Cardiology", Icons.Default.Favorite, TertiaryFixed),
                            Triple("Pediatrics", Icons.Default.AccountBox, SecondaryFixed),
                            Triple("Mental Health", Icons.Default.Face, PrimaryFixed)
                        )

                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                SpecialtyCard(specialties[0], Modifier.weight(1f))
                                SpecialtyCard(specialties[1], Modifier.weight(1f))
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                SpecialtyCard(specialties[2], Modifier.weight(1f))
                                SpecialtyCard(specialties[3], Modifier.weight(1f))
                            }
                        }
                    }
                }

                items(doctors) { doctor ->
                    DoctorCard(
                        name = doctor.name,
                        specialty = doctor.specialty,
                        rating = "4.9",
                        fee = "$120.00",
                        availability = if (doctor.availability.isNotEmpty()) doctor.availability[0] else "No slots",
                        availabilityColor = TertiaryFixed,
                        onAvailabilityColor = Tertiary
                    )
                }

                item {
                    FooterSection(onNavigate = onNavigate)
                }
            }
        }
    }
}

@Composable
fun SpecialtyCard(specialty: Triple<String, ImageVector, Color>, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.clickable { },
        shape = RoundedCornerShape(16.dp),
        color = if (isSystemInDarkTheme()) Color(0xFF1E1E1E) else Color(0xFFF1F3FE)
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Surface(
                modifier = Modifier.size(48.dp),
                color = specialty.third,
                shape = CircleShape
            ) {
                Icon(specialty.second, contentDescription = null, modifier = Modifier.padding(12.dp), tint = Primary)
            }
            Text(specialty.first, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold))
        }
    }
}

@Composable
fun DoctorCard(
    name: String,
    specialty: String,
    rating: String,
    fee: String,
    availability: String,
    availabilityColor: Color,
    onAvailabilityColor: Color
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 2.dp
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Box(
                    modifier = Modifier.size(80.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.fillMaxSize())
                }
                Column(modifier = Modifier.weight(1f)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text(name, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                            Text(specialty, style = MaterialTheme.typography.bodyMedium.copy(color = Secondary))
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.size(16.dp), tint = Primary)
                            Text(rating, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold, color = Primary))
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(color = availabilityColor, shape = RoundedCornerShape(100)) {
                        Text(
                            availability,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = onAvailabilityColor)
                        )
                    }
                }
            }
            HorizontalDivider(color = Outline.copy(alpha = 0.1f))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("CONSULTATION FEE", style = MaterialTheme.typography.labelSmall.copy(color = Outline, fontWeight = FontWeight.Bold, letterSpacing = 1.sp))
                    Text(fee, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                }
                Button(
                    onClick = { },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = Primary)
                ) {
                    Text("Book Now", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun VisitsScreen(onNavigate: (Screen) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var appointments by remember { mutableStateOf<List<com.example.ektachildhospital.api.Appointment>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    fun fetchAppointments() {
        isLoading = true
        scope.launch {
            try {
                appointments = RetrofitClient.getApiService(context).getAppointments()
            } catch (e: Exception) {
                val result = snackbarHostState.showSnackbar(
                    message = "Failed to load appointments",
                    actionLabel = "Retry"
                )
                if (result == SnackbarResult.ActionPerformed) {
                    fetchAppointments()
                }
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(Unit) {
        fetchAppointments()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            @OptIn(ExperimentalMaterial3Api::class)
            TopAppBar(
                title = {
                    Text(
                        "My Visits",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = Primary
                        )
                    )
                }
            )
        }
    ) { innerPadding ->
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize()
                    .padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                val upcoming = appointments.filter { it.status == "Upcoming" }
                val completed = appointments.filter { it.status == "Completed" }

                if (upcoming.isNotEmpty()) {
                    item {
                        Text(
                            "Upcoming Appointments",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = Primary)
                        )
                    }
                    items(upcoming) { appointment ->
                        AppointmentListItem(appointment)
                    }
                }

                if (completed.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "Past Visits",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = Outline)
                        )
                    }
                    items(completed) { appointment ->
                        AppointmentListItem(appointment)
                    }
                }

                if (upcoming.isEmpty() && completed.isEmpty()) {
                    item {
                        Box(modifier = Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) {
                            Text("No appointments found", style = MaterialTheme.typography.bodyLarge.copy(color = Outline))
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(32.dp))
                    FooterSection(onNavigate = onNavigate)
                }
            }
        }
    }
}

@Composable
fun AppointmentListItem(appointment: com.example.ektachildhospital.api.Appointment) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 1.dp
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(48.dp),
                color = if (appointment.status == "Upcoming") PrimaryFixed else MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    if (appointment.status == "Upcoming") Icons.Default.DateRange else Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.padding(12.dp),
                    tint = if (appointment.status == "Upcoming") Primary else Outline
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    appointment.doctorName,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                    "${appointment.date} • ${appointment.time}",
                    style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                )
            }
            if (appointment.status == "Upcoming") {
                Button(
                    onClick = { },
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryFixed, contentColor = Primary)
                ) {
                    Text("Join", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                }
            }
        }
    }
}

@Composable
fun ProfileScreen(onLogout: () -> Unit, onNavigate: (Screen) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isDarkModeFlow = remember {
        context.dataStore.data.map { preferences -> preferences[IS_DARK_MODE] }
    }
    val isDarkModeSaved by isDarkModeFlow.collectAsState(initial = isSystemInDarkTheme())

    Scaffold(
        topBar = {
            @OptIn(ExperimentalMaterial3Api::class)
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(id = R.drawable.logo),
                            contentDescription = null,
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape),
                            contentScale = ContentScale.Fit
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            "Ekta Hospital",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = Primary
                            )
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onLogout) {
                        Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = "Logout", tint = Primary)
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(24.dp)
                ) {
                    Column {
                        Surface(
                            color = PrimaryFixed,
                            shape = RoundedCornerShape(100)
                        ) {
                            Text(
                                text = "PATIENT PORTAL",
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = OnPrimaryFixed,
                                    letterSpacing = 1.sp
                                )
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Alexander Sterling",
                            style = MaterialTheme.typography.headlineLarge.copy(
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = (-0.5).sp
                            )
                        )
                    }
                }
            }

            item {
                // Appearance & Notifications Section
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(
                        "Preferences",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier.fillMaxWidth(),
                        shadowElevation = 1.dp
                    ) {
                        Column(modifier = Modifier.padding(24.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Settings, contentDescription = null, tint = Primary)
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text("Dark Mode", style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold))
                                }
                                Switch(
                                    checked = isDarkModeSaved ?: isSystemInDarkTheme(),
                                    onCheckedChange = { checked ->
                                        scope.launch {
                                            context.dataStore.edit { it[IS_DARK_MODE] = checked }
                                        }
                                    }
                                )
                            }
                            
                            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp), color = Outline.copy(alpha = 0.1f))

                            val isNotificationsFlow = remember {
                                context.dataStore.data.map { preferences -> preferences[PUSH_NOTIFICATIONS_ENABLED] ?: true }
                            }
                            val isNotificationsEnabled by isNotificationsFlow.collectAsState(initial = true)

                            // Notification Permission Launcher for Toggle
                            val permissionLauncher = rememberLauncherForActivityResult(
                                contract = ActivityResultContracts.RequestPermission()
                            ) { isGranted ->
                                scope.launch {
                                    context.dataStore.edit { it[PUSH_NOTIFICATIONS_ENABLED] = isGranted }
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Notifications, contentDescription = null, tint = Primary)
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text("Push Notifications", style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold))
                                }
                                Switch(
                                    checked = isNotificationsEnabled,
                                    onCheckedChange = { checked ->
                                        if (checked && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                            val isPermissionGranted = ContextCompat.checkSelfPermission(
                                                context,
                                                Manifest.permission.POST_NOTIFICATIONS
                                            ) == PackageManager.PERMISSION_GRANTED
                                            
                                            if (!isPermissionGranted) {
                                                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                            } else {
                                                scope.launch {
                                                    context.dataStore.edit { it[PUSH_NOTIFICATIONS_ENABLED] = true }
                                                }
                                            }
                                        } else {
                                            scope.launch {
                                                context.dataStore.edit { it[PUSH_NOTIFICATIONS_ENABLED] = checked }
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }

            item {
                // Settings Section
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    SettingsItem(
                        title = "Personal Information",
                        subtitle = "Update identity documents",
                        icon = Icons.Default.AccountBox,
                        iconContainerColor = SecondaryFixed,
                        iconColor = Secondary,
                        endIcon = Icons.AutoMirrored.Filled.KeyboardArrowRight
                    )
                    SettingsItem(
                        title = "Update Phone Number",
                        subtitle = "Manage your contact details",
                        icon = Icons.Default.Call,
                        iconContainerColor = PrimaryFixed,
                        iconColor = Primary,
                        endIcon = Icons.AutoMirrored.Filled.KeyboardArrowRight
                    )
                    SettingsItem(
                        title = "Security & Privacy",
                        subtitle = "Two-factor authentication",
                        icon = Icons.Default.Lock,
                        iconContainerColor = TertiaryFixed,
                        iconColor = Tertiary,
                        endIcon = Icons.AutoMirrored.Filled.KeyboardArrowRight
                    )
                }
                Spacer(modifier = Modifier.height(32.dp))
                FooterSection(onNavigate = onNavigate)
            }
        }
    }
}

@Composable
fun FooterSection(onNavigate: (Screen) -> Unit) {
    Column(
        modifier = Modifier.padding(bottom = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Privacy Policy",
                modifier = Modifier.clickable { onNavigate(Screen.PrivacyPolicy) },
                style = MaterialTheme.typography.labelMedium.copy(color = Primary)
            )
            Text(" • ", color = Color.Gray)
            Text(
                "Terms of Service",
                modifier = Modifier.clickable { onNavigate(Screen.TermsOfService) },
                style = MaterialTheme.typography.labelMedium.copy(color = Primary)
            )
            Text(" • ", color = Color.Gray)
            Text(
                "Help Center",
                modifier = Modifier.clickable { onNavigate(Screen.HelpCenter) },
                style = MaterialTheme.typography.labelMedium.copy(color = Primary)
            )
        }
        Text(
            "Version ${BuildConfig.VERSION_NAME}",
            style = MaterialTheme.typography.labelSmall.copy(color = Color.Gray)
        )
    }
}

@Composable
fun SettingsItem(
    title: String,
    subtitle: String,
    icon: ImageVector,
    iconContainerColor: Color,
    iconColor: Color,
    endIcon: ImageVector
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { },
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 1.dp
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(40.dp),
                    color = iconContainerColor,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.padding(8.dp))
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(title, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold))
                    Text(subtitle, style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant))
                }
            }
            Icon(endIcon, contentDescription = null, tint = Outline)
        }
    }
}

@Composable
fun BottomNavigationBar(currentScreen: Screen, onScreenSelected: (Screen) -> Unit) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
        tonalElevation = 8.dp
    ) {
        NavigationBarItem(
            selected = currentScreen == Screen.Home,
            onClick = { onScreenSelected(Screen.Home) },
            icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
            label = { Text("Home") },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = Color(0xFF001B3D),
                indicatorColor = PrimaryFixed
            )
        )
        NavigationBarItem(
            selected = currentScreen == Screen.Schedule,
            onClick = { onScreenSelected(Screen.Schedule) },
            icon = { Icon(Icons.Default.DateRange, contentDescription = "Schedule") },
            label = { Text("Schedule") },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = Color(0xFF001B3D),
                indicatorColor = PrimaryFixed
            )
        )
        NavigationBarItem(
            selected = currentScreen == Screen.Visits,
            onClick = { onScreenSelected(Screen.Visits) },
            icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Visits") },
            label = { Text("Visits") },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = Color(0xFF001B3D),
                indicatorColor = PrimaryFixed
            )
        )
        NavigationBarItem(
            selected = currentScreen == Screen.Profile,
            onClick = { onScreenSelected(Screen.Profile) },
            icon = { Icon(Icons.Default.Person, contentDescription = "Profile") },
            label = { Text("Profile") },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = Color(0xFF001B3D),
                indicatorColor = PrimaryFixed
            )
        )
    }
}
