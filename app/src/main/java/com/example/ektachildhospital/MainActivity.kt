package com.example.ektachildhospital

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.ektachildhospital.supabase.SupabaseAuthRepository
import com.example.ektachildhospital.supabase.SupabaseAppointmentRepository
import com.example.ektachildhospital.supabase.SupabaseDoctorRepository
import com.example.ektachildhospital.supabase.SupabasePushRepository
import com.example.ektachildhospital.notifications.PushNotificationManager
import com.example.ektachildhospital.api.Doctor
import com.example.ektachildhospital.api.Appointment
import com.example.ektachildhospital.ui.components.ShimmerDoctorCard
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.launch
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.firstOrNull
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.delay
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardArrowUp
import io.github.jan.supabase.compose.auth.composable.NativeSignInResult
import io.github.jan.supabase.compose.auth.composable.rememberSignInWithGoogle
import kotlinx.serialization.json.jsonPrimitive

// DataStore setup
val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")
val IS_LOGGED_IN = booleanPreferencesKey("is_logged_in")
val USER_ROLE = androidx.datastore.preferences.core.stringPreferencesKey("user_role")
val USER_NAME = androidx.datastore.preferences.core.stringPreferencesKey("user_name")

// Colors
val Primary = Color(0xFF005BB3)

class MainActivity : ComponentActivity() {
    private val authRepository = SupabaseAuthRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        installSplashScreen()
        setContent {
            EktaHospitalApp(authRepository)
        }
    }
}

@Composable
fun EktaHospitalApp(authRepository: SupabaseAuthRepository) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pushRepository = remember { SupabasePushRepository() }
    var appState by remember { mutableStateOf<AppState>(AppState.Splash) }
    var userRole by remember { mutableStateOf("patient") }
    var userName by remember { mutableStateOf("") }

    // Permission launcher for POST_NOTIFICATIONS
    val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        // Handle result if needed
    }

    LaunchedEffect(Unit) {
        PushNotificationManager.createChannel(context)
        val isLoggedIn = context.dataStore.data.map { it[IS_LOGGED_IN] ?: false }.firstOrNull() ?: false
        if (isLoggedIn) {
            userRole = context.dataStore.data.map { it[USER_ROLE] ?: "patient" }.firstOrNull() ?: "patient"
            val savedName = context.dataStore.data.map { it[USER_NAME] ?: "" }.firstOrNull() ?: ""

            if (savedName.isBlank()) {
                // Try to recover name from session if missing in DataStore
                val user = authRepository.getCurrentUser()
                val metaName = user?.userMetadata?.get("full_name")?.jsonPrimitive?.content
                    ?: user?.userMetadata?.get("name")?.jsonPrimitive?.content
                userName = metaName ?: "User"
            } else {
                userName = savedName
            }

            appState = AppState.Main
            syncFcmToken(pushRepository)
        } else {
            appState = AppState.Login
        }

        // Request notification permission on first open
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            permissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    when (appState) {
        AppState.Splash -> SplashScreen { appState = AppState.Login }
        AppState.Login -> LoginScreen(
            authRepository = authRepository,
            onLoginSuccess = { role, name ->
                userRole = role
                userName = name
                scope.launch {
                    context.dataStore.edit {
                        it[IS_LOGGED_IN] = true
                        it[USER_ROLE] = role
                        it[USER_NAME] = name
                    }
                    appState = AppState.Main
                    syncFcmToken(pushRepository)
                }
            }
        )
        AppState.Main -> MainNavigation(userRole, userName, onLogout = {
            scope.launch {
                FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        task.result?.let { token ->
                            scope.launch {
                                pushRepository.deleteDeviceToken(token)
                            }
                        }
                    }
                }
                authRepository.logout()
                context.dataStore.edit { it[IS_LOGGED_IN] = false }
                appState = AppState.Login
            }
        })
    }
}

sealed class AppState {
    object Splash : AppState()
    object Login : AppState()
    object Main : AppState()
}

@Composable
fun LoginScreen(
    authRepository: SupabaseAuthRepository,
    onLoginSuccess: (String, String) -> Unit
) {
    val scope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val action = authRepository.composeAuth.rememberSignInWithGoogle(
        onResult = { result ->
            scope.launch {
                isLoading = true
                val profile = authRepository.handleSignInResult(result)
                if (profile != null) {
                    onLoginSuccess(profile.role, profile.full_name ?: "User")
                } else if (result is NativeSignInResult.Error) {
                    errorMessage = result.message
                }
                isLoading = false
            }
        }
    )

    Surface(modifier = Modifier.fillMaxSize(), color = Color.White) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.logo),
                contentDescription = "Logo",
                modifier = Modifier.size(150.dp)
            )
            Spacer(modifier = Modifier.height(32.dp))
            Text("Ekta Hospital", style = MaterialTheme.typography.headlineLarge, color = Primary)
            Text("Appointment Portal", style = MaterialTheme.typography.titleMedium, color = Color.Gray)

            Spacer(modifier = Modifier.height(64.dp))

            if (isLoading) {
                CircularProgressIndicator(color = Primary)
            } else {
                Button(
                    onClick = { action.startFlow() },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, Color.LightGray)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Email,
                            contentDescription = "Google Logo",
                            modifier = Modifier.size(24.dp),
                            tint = Primary
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Sign in with Google", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }

            errorMessage?.let {
                Spacer(modifier = Modifier.height(16.dp))
                Text(it, color = Color.Red, textAlign = TextAlign.Center)
            }
        }
    }
}

data class TabItem(val title: String, val selectedIcon: androidx.compose.ui.graphics.vector.ImageVector, val unselectedIcon: androidx.compose.ui.graphics.vector.ImageVector)

// Colors matching the Stitch design
val Background = Color(0xFFF9F9FF)
val OnBackground = Color(0xFF191C21)
val SurfaceContainer = Color(0xFFECEDF6)
val PrimaryContainer = Color(0xFF005FB8)
val Secondary = Color(0xFF4A5F7F)
val Tertiary = Color(0xFF005151)
val PrimaryFixed = Color(0xFFD6E3FF)
val SecondaryFixed = Color(0xFFD4E3FF)
val TertiaryFixed = Color(0xFF92F2F2)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainNavigation(role: String, name: String, onLogout: () -> Unit) {
    var selectedTab by remember { mutableIntStateOf(0) }

    val patientTabs = listOf(
        TabItem("Home", Icons.Filled.Home, Icons.Outlined.Home),
        TabItem("Schedule", Icons.Default.DateRange, Icons.Outlined.DateRange),
        TabItem("Visits", Icons.AutoMirrored.Filled.List, Icons.AutoMirrored.Outlined.List),
        TabItem("Profile", Icons.Filled.Person, Icons.Outlined.Person)
    )

    val adminTabs = listOf(
        TabItem("Home", Icons.Filled.Home, Icons.Outlined.Home),
        TabItem("Appointments", Icons.Default.Event, Icons.Outlined.Event),
        TabItem("Doctors", Icons.Filled.Person, Icons.Outlined.Person),
        TabItem("Schedule", Icons.Default.DateRange, Icons.Outlined.DateRange)
    )

    val currentTabs = if (role == "admin") adminTabs else patientTabs

    Scaffold(
        containerColor = Background,
        topBar = {
            Surface(
                modifier = Modifier.shadow(8.dp),
                color = Background.copy(alpha = 0.8f)
            ) {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Image(
                                painter = painterResource(id = R.drawable.logo),
                                contentDescription = null,
                                modifier = Modifier.size(32.dp).clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("Ekta Hospital", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Primary)
                        }
                    },
                    actions = {
                        IconButton(onClick = { /* Handle notifications */ }) {
                            Icon(Icons.Default.Notifications, contentDescription = "Notifications", tint = PrimaryContainer)
                        }
                        IconButton(onClick = onLogout) {
                            Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = "Logout", tint = PrimaryContainer)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                )
            }
        },
        bottomBar = {
            Surface(
                modifier = Modifier.shadow(20.dp, shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)),
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                color = Background.copy(alpha = 0.8f)
            ) {
                NavigationBar(
                    containerColor = Color.Transparent,
                    tonalElevation = 0.dp,
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    currentTabs.forEachIndexed { index, tab ->
                        val isSelected = selectedTab == index
                        NavigationBarItem(
                            selected = isSelected,
                            onClick = { selectedTab = index },
                            label = {
                                Text(
                                    tab.title,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Medium,
                                    letterSpacing = 0.5.sp
                                )
                            },
                            icon = {
                                Icon(
                                    imageVector = if (isSelected) tab.selectedIcon else tab.unselectedIcon,
                                    contentDescription = tab.title
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Color(0xFF001B3D),
                                selectedTextColor = Color(0xFF001B3D),
                                indicatorColor = PrimaryFixed,
                                unselectedIconColor = Color(0xFF495E7D),
                                unselectedTextColor = Color(0xFF495E7D)
                            )
                        )
                    }
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (role == "admin") {
                when (selectedTab) {
                    0 -> AdminDashboard()
                    1 -> AdminAppointmentsScreen()
                    2 -> DoctorManagementScreen()
                    3 -> AdminScheduleScreen()
                }
            } else {
                when (selectedTab) {
                    0 -> HomeScreen(name, onNavigateToTab = { selectedTab = it })
                    1 -> SchedulesScreen()
                    2 -> VisitsScreen()
                    3 -> ProfileScreen(name, role)
                }
            }
        }
    }
}

@Composable
fun HomeScreen(name: String, onNavigateToTab: (Int) -> Unit) {
    var appointments by remember { mutableStateOf<List<Appointment>>(emptyList()) }
    val appointmentRepository = remember { SupabaseAppointmentRepository() }

    LaunchedEffect(Unit) {
        try {
            appointments = appointmentRepository.getMyAppointments()
        } catch (e: Exception) {
            // Handle error
        }
    }

    val nextAppointment = appointments.firstOrNull { it.status.equals("confirmed", ignoreCase = true) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        item {
            Column {
                Text(
                    text = "Welcome $name!",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = OnBackground
                )
                Text(
                    text = "Your wellness journey is our priority today.",
                    style = MaterialTheme.typography.bodyLarge,
                    fontStyle = FontStyle.Italic,
                    color = Secondary
                )
            }
        }

        item {
            NextAppointmentCard(nextAppointment)
        }

        item {
            QuickActionsSection(onNavigateToTab)
        }
    }
}

@Composable
fun NextAppointmentCard(appointment: Appointment?) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 200.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Primary)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Background Decorative Circle
            Box(
                modifier = Modifier
                    .size(200.dp)
                    .offset(x = 100.dp, y = (-80).dp)
                    .background(Color.White.copy(alpha = 0.1f), CircleShape)
            )

            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Surface(
                        color = Color.White.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(percent = 50)
                    ) {
                        Text(
                            text = "NEXT APPOINTMENT",
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            letterSpacing = 1.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = appointment?.doctorName ?: "No Upcoming Session",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "Diagnostic Consultation",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                }

                if (appointment != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            color = Color.White.copy(alpha = 0.1f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.DateRange, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("DATE & TIME", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.7f))
                            Text("${appointment.date} • ${appointment.time}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun QuickActionsSection(onNavigateToTab: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            "QUICK ACTIONS",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = Secondary,
            letterSpacing = 1.sp
        )

        QuickActionButton(
            title = "Book Appointment",
            icon = Icons.Default.Add,
            backgroundColor = PrimaryFixed,
            iconColor = Primary,
            onClick = { onNavigateToTab(1) }
        )
        QuickActionButton(
            title = "My Records",
            icon = Icons.AutoMirrored.Filled.List,
            backgroundColor = SecondaryFixed,
            iconColor = Secondary,
            onClick = { onNavigateToTab(2) }
        )
    }
}

@Composable
fun QuickActionButton(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, backgroundColor: Color, iconColor: Color, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        border = BorderStroke(1.dp, Color.LightGray.copy(alpha = 0.1f)),
        shadowElevation = 2.dp
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(48.dp),
                color = backgroundColor,
                shape = RoundedCornerShape(12.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = iconColor)
                }
            }
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = title,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = OnBackground
            )
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = Color.LightGray)
        }
    }
}

@Composable
fun SchedulesScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val doctorRepository = remember { SupabaseDoctorRepository() }
    val appointmentRepository = remember { SupabaseAppointmentRepository() }
    var doctors by remember { mutableStateOf<List<Doctor>>(emptyList()) }
    var appointments by remember { mutableStateOf<List<Appointment>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var selectedDoctor by remember { mutableStateOf<Doctor?>(null) }

    fun refreshData() {
        scope.launch {
            isLoading = true
            try {
                doctors = doctorRepository.getDoctors()
                appointments = appointmentRepository.getMyAppointments()
            } catch (e: Exception) {
                android.util.Log.e("SchedulesScreen", "Error loading schedules", e)
                android.widget.Toast.makeText(context, "Failed to load schedules: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(Unit) {
        refreshData()
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                "My Schedule",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.ExtraBold,
                color = OnBackground
            )
            Text(
                "Browse doctors, request appointments, and track your bookings.",
                style = MaterialTheme.typography.bodyLarge,
                color = Secondary
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        if (isLoading) {
            items(3) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.LightGray.copy(alpha = 0.2f))
                    )
            }
        } else {
            item {
                Text(
                    "Available Doctors",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = OnBackground
                )
            }

            if (doctors.isEmpty()) {
                item {
                    EmptyStateCard(
                        title = "No doctors available",
                        message = "Ask an admin to add doctors before booking appointments."
                    )
                }
            } else {
                items(doctors) { doctor ->
                    DoctorBookingCard(
                        doctor = doctor,
                        onBook = { selectedDoctor = doctor }
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "My Appointment Requests",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = OnBackground
                )
            }

            if (appointments.isEmpty()) {
                item {
                    EmptyStateCard(
                        title = "No appointment requests yet",
                        message = "Book a doctor above and your request will appear here for admin review."
                    )
                }
            } else {
                items(appointments) { appointment ->
                    AppointmentCard(appointment)
                }
            }
        }
    }

    selectedDoctor?.let { doctor ->
        BookAppointmentDialog(
            doctor = doctor,
            onDismiss = { selectedDoctor = null },
            onBook = { date, time ->
                scope.launch {
                    val patientId = appointmentRepository.getCurrentUserId()
                    if (patientId == null) {
                        android.widget.Toast.makeText(context, "Please sign in again to book an appointment.", android.widget.Toast.LENGTH_LONG).show()
                        return@launch
                    }

                    try {
                        val appointmentDate = nextDateForDayLabel(date)
                        if (appointmentDate == null) {
                            android.widget.Toast.makeText(context, "Could not map the selected day to a calendar date.", android.widget.Toast.LENGTH_LONG).show()
                            return@launch
                        }

                        appointmentRepository.bookAppointment(
                            Appointment(
                                id = java.util.UUID.randomUUID().toString(),
                                patientId = patientId,
                                doctorId = doctor.id,
                                doctorName = doctor.name,
                                date = appointmentDate.toString(),
                                time = time,
                                status = "pending"
                            )
                        )
                        selectedDoctor = null
                        refreshData()
                        android.widget.Toast.makeText(context, "Appointment request sent to admin", android.widget.Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        android.util.Log.e("SchedulesScreen", "Error booking appointment", e)
                        android.widget.Toast.makeText(context, "Failed to book appointment: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                    }
                }
            }
        )
    }
}

@Composable
fun AppointmentCard(appointment: Appointment) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = Color.White,
        border = BorderStroke(1.dp, Color.LightGray.copy(alpha = 0.1f)),
        shadowElevation = 2.dp
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = PrimaryFixed,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.size(56.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.DateRange, contentDescription = null, tint = Primary)
                }
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    appointment.doctorName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = OnBackground
                )
                Text(
                    "${appointment.date} • ${appointment.time}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Secondary
                )
            }
            StatusBadge(appointment.status)
        }
    }
}

@Composable
fun StatusBadge(status: String) {
    val normalizedStatus = status.lowercase()
    val (containerColor, contentColor) = when (normalizedStatus) {
        "confirmed" -> Color(0xFFE8F5E9) to Color(0xFF2E7D32)
        "rejected" -> Color(0xFFFFEBEE) to Color(0xFFC62828)
        else -> Color(0xFFFFF3E0) to Color(0xFFEF6C00)
    }
    val displayStatus = normalizedStatus.replaceFirstChar { it.uppercase() }

    Surface(
        color = containerColor,
        shape = RoundedCornerShape(8.dp)
    ) {
        Text(
            text = displayStatus,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = contentColor
        )
    }
}

@Composable
fun VisitsScreen() {
    val context = LocalContext.current
    val appointmentRepository = remember { SupabaseAppointmentRepository() }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                "Medical History",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.ExtraBold,
                color = OnBackground
            )
            Text(
                "Your past consultations and clinical records.",
                style = MaterialTheme.typography.bodyLarge,
                color = Secondary
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
        item {
            RealVisitsSection(
                appointmentRepository = appointmentRepository,
                onError = { message ->
                    android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_LONG).show()
                }
            )
        }
    }
}

@Composable
fun VisitCard(appointment: Appointment) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = Color.White,
        border = BorderStroke(1.dp, Color.LightGray.copy(alpha = 0.1f)),
        shadowElevation = 1.dp
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = SecondaryFixed,
                    shape = CircleShape,
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = Secondary, modifier = Modifier.size(20.dp))
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Appointment", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Text("${appointment.doctorName} • ${formatDisplayDate(appointment.date)}", style = MaterialTheme.typography.bodySmall, color = Secondary)
                }
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = Color.LightGray)
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                "Time: ${appointment.time} • Status: ${appointment.status.replaceFirstChar { it.uppercase() }}",
                style = MaterialTheme.typography.bodyMedium,
                color = OnBackground.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
fun ProfileScreen(name: String, role: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val authRepository = remember { SupabaseAuthRepository() }
    val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { }
    var profile by remember { mutableStateOf<com.example.ektachildhospital.supabase.Profile?>(null) }
    var joinedText by remember { mutableStateOf("Joined recently") }
    var isSavingPhone by remember { mutableStateOf(false) }
    var showPhoneDialog by remember { mutableStateOf(false) }
    var footerScreen by remember { mutableStateOf<FooterScreen?>(null) }

    LaunchedEffect(Unit) {
        profile = authRepository.getProfile()
        joinedText = formatJoinedDate(authRepository.getCurrentUser()?.createdAt?.toString())
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // Asymmetric Profile Hero Section
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Color(0xFFF1F3FE), // surface-container-low
            shape = RoundedCornerShape(24.dp)
        ) {
            Box(modifier = Modifier.padding(24.dp)) {
                // Background Decorative Circle
                Box(
                    modifier = Modifier
                        .size(150.dp)
                        .offset(x = 120.dp, y = (-60).dp)
                        .background(Primary.copy(alpha = 0.05f), CircleShape)
                )

                Column {
                    Surface(
                        color = PrimaryFixed,
                        shape = RoundedCornerShape(percent = 50)
                    ) {
                        Text(
                            text = "PATIENT PORTAL",
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF001B3D), // on-primary-fixed
                            letterSpacing = 1.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = name,
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = OnBackground
                    )
                    Text(
                        text = role.uppercase(),
                        style = MaterialTheme.typography.labelMedium,
                        color = Primary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.DateRange,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = Color(0xFF414754) // on-surface-variant
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            joinedText,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF414754)
                        )
                    }
                }
            }
        }

        // Account Settings Section
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFFF1F3FE), RoundedCornerShape(24.dp))
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Settings, contentDescription = null, tint = Primary)
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    "Account settings",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = OnBackground
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                SettingsItem(
                    title = "Change Phone Number",
                    subtitle = profile?.phone?.takeIf { it.isNotBlank() } ?: "Add your 10-digit phone number",
                    icon = Icons.Default.Call,
                    backgroundColor = PrimaryFixed,
                    iconColor = Primary,
                    onClick = { showPhoneDialog = true }
                )
                SettingsItem(
                    title = "Security & Privacy",
                    subtitle = "Two-factor authentication",
                    icon = Icons.Default.Lock,
                    backgroundColor = TertiaryFixed,
                    iconColor = Tertiary
                )
            }
        }

        // Appearance Section
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFFF1F3FE), RoundedCornerShape(24.dp))
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Text(
                "Appearance",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = OnBackground
            )

            // Health Alerts Card
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = Primary // gradient replaced with solid primary for simplicity
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.NotificationsActive, contentDescription = null, tint = Color.White)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            "HEALTH ALERTS",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            letterSpacing = 1.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "Enable push notifications for medication reminders.",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = {
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                                permissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.2f)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("ENABLE NOW", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                FooterLink("Privacy Policy", onClick = { footerScreen = FooterScreen.PrivacyPolicy })
                Text(" : ", color = Color(0xFF7A8090), style = MaterialTheme.typography.bodyMedium)
                FooterLink("Terms of Service", onClick = { footerScreen = FooterScreen.TermsOfService })
                Text(" : ", color = Color(0xFF7A8090), style = MaterialTheme.typography.bodyMedium)
                FooterLink("Help Center", onClick = { footerScreen = FooterScreen.HelpCenter })
            }
            Text(
                text = "App Version ${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF7A8090),
                fontWeight = FontWeight.Medium
            )
        }
    }

    if (showPhoneDialog) {
        EditPhoneDialog(
            currentPhone = profile?.phone.orEmpty(),
            isSaving = isSavingPhone,
            onDismiss = { showPhoneDialog = false },
            onSave = { phone ->
                scope.launch {
                    isSavingPhone = true
                    try {
                        authRepository.updatePhone(phone)
                        profile = authRepository.getProfile()
                        showPhoneDialog = false
                        android.widget.Toast.makeText(context, "Phone number updated", android.widget.Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        android.util.Log.e("ProfileScreen", "Error updating phone", e)
                        android.widget.Toast.makeText(context, "Failed to update phone: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                    } finally {
                        isSavingPhone = false
                    }
                }
            }
        )
    }

    footerScreen?.let { screen ->
        FooterInfoScreen(
            screen = screen,
            onDismiss = { footerScreen = null }
        )
    }
}

@Composable
fun FooterLink(label: String, onClick: () -> Unit) {
    Text(
        text = label,
        modifier = Modifier.clickable(onClick = onClick),
        style = MaterialTheme.typography.bodySmall,
        color = Primary,
        fontWeight = FontWeight.SemiBold
    )
}

enum class FooterScreen {
    PrivacyPolicy,
    TermsOfService,
    HelpCenter
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FooterInfoScreen(
    screen: FooterScreen,
    onDismiss: () -> Unit
) {
    val title = when (screen) {
        FooterScreen.PrivacyPolicy -> "Privacy Policy"
        FooterScreen.TermsOfService -> "Terms of Service"
        FooterScreen.HelpCenter -> "Help Center"
    }

    val sections = when (screen) {
        FooterScreen.PrivacyPolicy -> listOf(
            "What We Collect" to "We store your profile details, selected appointments, doctor preferences, and support-related activity needed to run the patient experience.",
            "How We Use Data" to "Your information is used to manage appointments, improve hospital operations, and send service-related notifications such as confirmations and reminders.",
            "Data Protection" to "We restrict access to authorized users, use Supabase authentication, and apply row-level security so patients only access their own records.",
            "Contact" to "For privacy concerns, please reach out to the Ekta Child Hospital support team through the Help Center section in the app."
        )
        FooterScreen.TermsOfService -> listOf(
            "Appointments" to "Submitting an appointment request does not guarantee a confirmed slot. Requests are reviewed based on doctor availability and hospital workflow.",
            "Account Responsibility" to "You are responsible for keeping your profile details accurate, especially your name and contact number for appointment coordination.",
            "Acceptable Use" to "Do not misuse the platform, impersonate another patient, or submit false booking requests that disrupt hospital operations.",
            "Service Changes" to "Ekta Child Hospital may update scheduling processes, communication channels, or app features to improve care delivery."
        )
        FooterScreen.HelpCenter -> listOf(
            "Booking Support" to "If your request is pending for longer than expected, contact the hospital front desk and mention the doctor name and preferred slot.",
            "Phone Number Updates" to "You can update your 10-digit phone number from the profile page. Number verification can be added later for WhatsApp reminders.",
            "Technical Help" to "If the app is not syncing correctly, sign out and sign back in once, then retry your action on a stable internet connection.",
            "Emergency Note" to "This app is not intended for medical emergencies. Please contact the hospital directly or seek urgent care when necessary."
        )
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Background
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                TopAppBar(
                    title = {
                        Text(
                            title,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = OnBackground
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Primary)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Background)
                )
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    sections.forEach { (heading, body) ->
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(20.dp),
                            color = Color.White,
                            shadowElevation = 1.dp
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    heading,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = OnBackground
                                )
                                Text(
                                    body,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Secondary
                                )
                            }
                        }
                    }
                    Text(
                        text = "App Version ${BuildConfig.VERSION_NAME}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF7A8090),
                        modifier = Modifier.padding(top = 8.dp, bottom = 24.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun FooterLinkPreview() {
    FooterLink("Privacy Policy", onClick = {})
}

@Composable
fun SettingsItem(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    backgroundColor: Color,
    iconColor: Color,
    onClick: () -> Unit = {}
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        shadowElevation = 1.dp
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(40.dp),
                color = backgroundColor,
                shape = RoundedCornerShape(12.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(20.dp))
                }
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = OnBackground
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF414754)
                )
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = Color.LightGray)
        }
    }
}

@Composable
fun RealVisitsSection(
    appointmentRepository: SupabaseAppointmentRepository,
    onError: (String) -> Unit
) {
    var appointments by remember { mutableStateOf<List<Appointment>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        try {
            appointments = appointmentRepository.getMyAppointments()
                .filter { it.status.lowercase() != "pending" }
                .sortedByDescending { it.createdAt ?: it.date }
        } catch (e: Exception) {
            android.util.Log.e("VisitsScreen", "Error loading visits", e)
            onError("Failed to load visits: ${e.message}")
        } finally {
            isLoading = false
        }
    }

    when {
        isLoading -> {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                repeat(3) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(110.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.LightGray.copy(alpha = 0.2f))
                    )
                }
            }
        }

        appointments.isEmpty() -> {
            EmptyStateCard(
                title = "No visit history yet",
                message = "Your confirmed, completed, or cancelled appointments will appear here."
            )
        }

        else -> {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                appointments.forEach { appointment ->
                    VisitCard(appointment)
                }
            }
        }
    }
}

@Composable
fun EditPhoneDialog(
    currentPhone: String,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var phone by remember(currentPhone) { mutableStateOf(currentPhone.filter { it.isDigit() }) }
    val normalizedPhone = phone.filter { it.isDigit() }.take(10)
    val isValidPhone = normalizedPhone.length == 10

    AlertDialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        title = { Text("Change Phone Number", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Enter your 10-digit phone number. Verification can be added later when WhatsApp notifications are enabled.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Secondary
                )
                OutlinedTextField(
                    value = phone,
                    onValueChange = { value -> phone = value.filter { it.isDigit() }.take(10) },
                    label = { Text("Phone Number") },
                    placeholder = { Text("9876543210") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    supportingText = {
                        Text(if (phone.isBlank() || isValidPhone) "Enter 10 digits" else "Phone number must be 10 digits")
                    }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(normalizedPhone) },
                enabled = isValidPhone && !isSaving
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = Color.White
                    )
                } else {
                    Text("Save")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSaving) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun DoctorCard(doctor: Doctor) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(60.dp).background(Primary.copy(alpha = 0.1f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Person, contentDescription = null, tint = Primary)
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(doctor.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Text(doctor.specialty, color = Color.Gray, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
fun AdminDashboard() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var stats by remember { mutableStateOf(com.example.ektachildhospital.api.AdminStats(0, 0)) }
    var pendingAppointments by remember { mutableStateOf<List<Appointment>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    val appointmentRepository = remember { SupabaseAppointmentRepository() }

    fun refreshDashboard() {
        scope.launch {
            isLoading = true
            try {
                stats = appointmentRepository.getAdminStats()
                pendingAppointments = appointmentRepository.getPendingAppointments()
            } catch (e: Exception) {
                android.util.Log.e("AdminDashboard", "Error loading dashboard", e)
                android.widget.Toast.makeText(context, "Failed to load dashboard: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(Unit) {
        refreshDashboard()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Text(
            "Admin Dashboard",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = Primary
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Primary.copy(alpha = 0.05f))
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text("Management Overview", fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    AdminStatCard("Total Patients", stats.totalPatients.toString())
                    AdminStatCard("Pending Requests", stats.pendingAppointments.toString())
                }
            }
        }

        Text("Appointment Requests", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally), color = Primary)
        } else if (pendingAppointments.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No pending appointments", color = Color.Gray)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(pendingAppointments) { appointment ->
                    AppointmentRequestItem(
                        appointment = appointment,
                        onAccept = {
                            scope.launch {
                                try {
                                    appointmentRepository.updateAppointmentStatus(appointment.id, "confirmed")
                                    refreshDashboard()
                                } catch (e: Exception) {
                                    android.util.Log.e("AdminDashboard", "Error accepting appointment", e)
                                    android.widget.Toast.makeText(context, "Failed to accept request: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                                }
                            }
                        },
                        onReject = {
                            scope.launch {
                                try {
                                    appointmentRepository.updateAppointmentStatus(appointment.id, "rejected")
                                    refreshDashboard()
                                } catch (e: Exception) {
                                    android.util.Log.e("AdminDashboard", "Error rejecting appointment", e)
                                    android.widget.Toast.makeText(context, "Failed to reject request: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun AdminAppointmentsScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val appointmentRepository = remember { SupabaseAppointmentRepository() }
    
    val dateList = remember {
        (0..7).map { LocalDate.now().plusDays(it.toLong()) }
    }
    var selectedDate by remember { mutableStateOf(dateList.first()) }
    var appointments by remember { mutableStateOf<List<Appointment>>(emptyList()) }
    var doctors by remember { mutableStateOf<List<Doctor>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    fun refreshData() {
        scope.launch {
            isLoading = true
            try {
                doctors = appointmentRepository.getAllDoctors()
                appointments = appointmentRepository.getAppointmentsByDate(selectedDate.toString())
            } catch (e: Exception) {
                android.util.Log.e("AdminAppointments", "Error loading data", e)
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(selectedDate) {
        refreshData()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            "Manage Appointments",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = Primary
        )

        // Date Selector
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            items(dateList) { date ->
                val isSelected = date == selectedDate
                Surface(
                    onClick = { selectedDate = date },
                    shape = RoundedCornerShape(12.dp),
                    color = if (isSelected) Primary else Color.White,
                    border = BorderStroke(1.dp, if (isSelected) Primary else Color.LightGray.copy(alpha = 0.5f)),
                    modifier = Modifier.width(80.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            date.dayOfWeek.name.take(3),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isSelected) Color.White else Color.Gray
                        )
                        Text(
                            date.dayOfMonth.toString(),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) Color.White else OnBackground
                        )
                    }
                }
            }
        }

        if (isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Primary)
            }
        } else if (doctors.isEmpty()) {
            EmptyStateCard("No Doctors", "Add doctors first to see appointments.")
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(20.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(doctors) { doctor ->
                    DoctorAppointmentGroup(
                        doctor = doctor,
                        appointments = appointments.filter { it.doctorId == doctor.id },
                        onStatusUpdate = { appointmentId, status ->
                            scope.launch {
                                try {
                                    appointmentRepository.updateAppointmentStatus(appointmentId, status)
                                    refreshData()
                                    android.widget.Toast.makeText(context, "Appointment $status", android.widget.Toast.LENGTH_SHORT).show()
                                } catch (e: Exception) {
                                    android.widget.Toast.makeText(context, "Update failed", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun DoctorAppointmentGroup(
    doctor: Doctor,
    appointments: List<Appointment>,
    onStatusUpdate: (String, String) -> Unit
) {
    val appointmentsBySlot = appointments.groupBy { it.time }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            doctor.name,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.ExtraBold,
            color = Primary
        )

        if (appointments.isEmpty()) {
            Text(
                "No appointments scheduled for this day.",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray,
                modifier = Modifier.padding(start = 4.dp)
            )
        } else {
            appointmentsBySlot.forEach { (slot, slotAppointments) ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = Color.White,
                    shadowElevation = 1.dp,
                    border = BorderStroke(1.dp, Color.LightGray.copy(alpha = 0.2f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(slot, fontWeight = FontWeight.Bold, color = OnBackground)
                            Surface(
                                color = PrimaryFixed,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    "${slotAppointments.size} Patients",
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Primary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        slotAppointments.forEachIndexed { index, appt ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "Patient ID: ${appt.patientId.takeLast(6)}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Surface(
                                        color = when(appt.status) {
                                            "confirmed" -> Color(0xFFE8F5E9)
                                            "rejected" -> Color(0xFFFFEBEE)
                                            else -> Color(0xFFFFF3E0)
                                        },
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            appt.status.uppercase(),
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = when(appt.status) {
                                                "confirmed" -> Color(0xFF2E7D32)
                                                "rejected" -> Color(0xFFC62828)
                                                else -> Color(0xFFEF6C00)
                                            },
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                                
                                if (appt.status == "pending") {
                                    Row {
                                        IconButton(onClick = { onStatusUpdate(appt.id, "confirmed") }) {
                                            Icon(Icons.Default.CheckCircle, contentDescription = "Confirm", tint = Color(0xFF2E7D32))
                                        }
                                        IconButton(onClick = { onStatusUpdate(appt.id, "rejected") }) {
                                            Icon(Icons.Default.Cancel, contentDescription = "Reject", tint = Color(0xFFC62828))
                                        }
                                    }
                                }
                            }
                            if (index < slotAppointments.size - 1) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(vertical = 8.dp),
                                    color = Color.LightGray.copy(alpha = 0.3f)
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
fun DoctorBookingCard(doctor: Doctor, onBook: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = Color.White,
        border = BorderStroke(1.dp, Color.LightGray.copy(alpha = 0.1f)),
        shadowElevation = 2.dp
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = PrimaryFixed,
                    shape = CircleShape,
                    modifier = Modifier.size(52.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Person, contentDescription = null, tint = Primary)
                    }
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(doctor.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = OnBackground)
                    Text(doctor.specialty.ifBlank { "General Consultation" }, style = MaterialTheme.typography.bodyMedium, color = Secondary)
                    Text("Consultation fee: Rs ${doctor.consultationFee.toInt()}", style = MaterialTheme.typography.bodySmall, color = Primary)
                }
            }
            Button(
                onClick = onBook,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Primary)
            ) {
                Text("Request Appointment")
            }
        }
    }
}

@Composable
fun BookAppointmentDialog(
    doctor: Doctor,
    onDismiss: () -> Unit,
    onBook: (String, String) -> Unit
) {
    val sortedAvailability = remember(doctor.availability) {
        val dayOrder = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
        doctor.availability.toList().sortedBy { (day, _) ->
            dayOrder.indexOf(day).let { if (it == -1) Int.MAX_VALUE else it }
        }
    }
    var selectedDay by remember(sortedAvailability) {
        mutableStateOf(sortedAvailability.firstOrNull()?.first)
    }
    var selectedTime by remember(selectedDay, sortedAvailability) {
        mutableStateOf(
            sortedAvailability.firstOrNull { it.first == selectedDay }?.second?.firstOrNull().orEmpty()
        )
    }
    val availableSlots = sortedAvailability.firstOrNull { it.first == selectedDay }?.second.orEmpty()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Book ${doctor.name}", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Choose from the doctor's available days and time slots. Your request will be sent to admin for approval.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Secondary
                )

                if (sortedAvailability.isEmpty()) {
                    Text(
                        "This doctor does not have any available schedule yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Gray
                    )
                } else {
                    Text(
                        "Available Days",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = OnBackground
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        sortedAvailability.forEach { (day, slots) ->
                            val isSelected = selectedDay == day
                            Surface(
                                onClick = {
                                    selectedDay = day
                                    selectedTime = slots.firstOrNull().orEmpty()
                                },
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) PrimaryFixed else Color.White,
                                border = BorderStroke(1.dp, if (isSelected) Primary else Color.LightGray.copy(alpha = 0.3f))
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(day, fontWeight = FontWeight.Bold, color = OnBackground)
                                    Text("${slots.size} slots", color = Secondary, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }

                    Text(
                        "Available Time Slots",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = OnBackground
                    )
                    if (availableSlots.isEmpty()) {
                        Text(
                            "No slots available for the selected day.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.Gray
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            availableSlots.forEach { slot ->
                                val isSelected = selectedTime == slot
                                Surface(
                                    onClick = { selectedTime = slot },
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (isSelected) PrimaryFixed else Color.White,
                                    border = BorderStroke(1.dp, if (isSelected) Primary else Color.LightGray.copy(alpha = 0.3f))
                                ) {
                                    Text(
                                        text = slot,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                        color = OnBackground,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onBook(selectedDay.orEmpty(), selectedTime) },
                enabled = selectedDay != null && selectedTime.isNotBlank() && sortedAvailability.isNotEmpty()
            ) {
                Text("Send Request")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun EmptyStateCard(title: String, message: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.DateRange,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = Color.LightGray
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(title, color = OnBackground, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Text(message, color = Color.Gray, textAlign = TextAlign.Center)
        }
    }
}

fun nextDateForDayLabel(dayLabel: String): LocalDate? {
    val targetDay = when (dayLabel.trim().lowercase()) {
        "mon", "monday" -> DayOfWeek.MONDAY
        "tue", "tues", "tuesday" -> DayOfWeek.TUESDAY
        "wed", "wednesday" -> DayOfWeek.WEDNESDAY
        "thu", "thur", "thurs", "thursday" -> DayOfWeek.THURSDAY
        "fri", "friday" -> DayOfWeek.FRIDAY
        "sat", "saturday" -> DayOfWeek.SATURDAY
        "sun", "sunday" -> DayOfWeek.SUNDAY
        else -> null
    } ?: return null

    val today = LocalDate.now()
    val daysUntilTarget = (targetDay.value - today.dayOfWeek.value + 7) % 7
    return today.plusDays(if (daysUntilTarget == 0) 7 else daysUntilTarget.toLong())
}

fun formatDisplayDate(date: String): String {
    return try {
        LocalDate.parse(date).format(DateTimeFormatter.ofPattern("dd MMM yyyy"))
    } catch (_: Exception) {
        date
    }
}

fun formatJoinedDate(rawCreatedAt: String?): String {
    if (rawCreatedAt.isNullOrBlank()) return "Joined recently"
    return try {
        val date = Instant.parse(rawCreatedAt).atZone(ZoneId.systemDefault()).toLocalDate()
        "Joined ${date.format(DateTimeFormatter.ofPattern("dd MMM yyyy"))}"
    } catch (_: Exception) {
        "Joined recently"
    }
}

fun syncFcmToken(pushRepository: SupabasePushRepository) {
    FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
        if (!task.isSuccessful) {
            android.util.Log.w("FCM", "Fetching FCM registration token failed", task.exception)
            return@addOnCompleteListener
        }

        val token = task.result ?: return@addOnCompleteListener
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            try {
                pushRepository.saveDeviceToken(token)
            } catch (e: Exception) {
                android.util.Log.e("FCM", "Failed to persist FCM token", e)
            }
        }
    }
}

@Composable
fun AppointmentRequestItem(appointment: Appointment, onAccept: () -> Unit, onReject: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = Color.White,
        shadowElevation = 1.dp
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    appointment.doctorName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = OnBackground
                )
                Text(
                    "${appointment.date} • ${appointment.time}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Secondary
                )
            }
            Row {
                IconButton(onClick = onAccept) {
                    Icon(Icons.Default.CheckCircle, contentDescription = "Accept", tint = Color(0xFF2E7D32))
                }
                IconButton(onClick = onReject) {
                    Icon(Icons.Default.Close, contentDescription = "Reject", tint = Color(0xFFC62828))
                }
            }
        }
    }
}

@Composable
fun DoctorManagementScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var doctors by remember { mutableStateOf<List<Doctor>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var showAddDoctorDialog by remember { mutableStateOf(false) }
    val doctorRepository = remember { SupabaseDoctorRepository() }

    fun refreshDoctors() {
        scope.launch {
            isLoading = true
            try {
                doctors = doctorRepository.getDoctors()
            } catch (e: Exception) {
                android.util.Log.e("DoctorManagement", "Error loading doctors", e)
                android.widget.Toast.makeText(context, "Failed to load doctors: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
            }
            finally { isLoading = false }
        }
    }

    LaunchedEffect(Unit) {
        refreshDoctors()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Doctors", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = Primary)
            Button(
                onClick = { showAddDoctorDialog = true },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Primary)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Add Doctor")
            }
        }

        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally), color = Primary)
        } else {
            LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                items(doctors) { doctor ->
                    AdminDoctorCard(
                        doctor = doctor,
                        onDelete = {
                            scope.launch {
                                try {
                                    doctorRepository.deleteDoctor(doctor.id)
                                    refreshDoctors()
                                } catch (e: Exception) {
                                    android.util.Log.e("DoctorManagement", "Error deleting doctor", e)
                                    android.widget.Toast.makeText(context, "Failed to delete doctor: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    )
                }
            }
        }
    }

        if (showAddDoctorDialog) {
            AddDoctorDialog(
                onDismiss = { showAddDoctorDialog = false },
                onAdd = { name, email, specialty, fee ->
                    scope.launch {
                        try {
                            doctorRepository.addDoctor(Doctor(
                                id = java.util.UUID.randomUUID().toString(),
                                name = name,
                                email = email,
                                specialty = specialty,
                                consultationFee = fee,
                                availability = emptyMap()
                            ))
                            refreshDoctors()
                            showAddDoctorDialog = false
                            android.widget.Toast.makeText(context, "Doctor added successfully", android.widget.Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            android.util.Log.e("DoctorManagement", "Error adding doctor", e)
                            android.widget.Toast.makeText(context, "Failed to add doctor: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                        }
                    }
                }
            )
        }
}

@Composable
fun AddDoctorDialog(onDismiss: () -> Unit, onAdd: (String, String, String, Double) -> Unit) {
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var specialty by remember { mutableStateOf("") }
    var fee by remember { mutableStateOf("500") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add New Doctor", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Full Name") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Email Address") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = specialty, onValueChange = { specialty = it }, label = { Text("Specialization") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = fee, onValueChange = { fee = it }, label = { Text("Consultation Fee (₹)") }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            }
        },
        confirmButton = {
            Button(onClick = {
                if (name.isNotBlank() && email.isNotBlank()) {
                    onAdd(name, email, specialty, fee.toDoubleOrNull() ?: 500.0)
                }
            }) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun AdminScheduleScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var doctors by remember { mutableStateOf<List<Doctor>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    val doctorRepository = remember { SupabaseDoctorRepository() }

    fun refreshDoctors() {
        scope.launch {
            isLoading = true
            try {
                doctors = doctorRepository.getDoctors()
            } catch (e: Exception) {
                android.util.Log.e("AdminSchedule", "Error loading doctors", e)
                android.widget.Toast.makeText(context, "Failed to load doctors: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
            }
            finally { isLoading = false }
        }
    }

    LaunchedEffect(Unit) {
        refreshDoctors()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Text("Weekly Schedules", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = Primary)

        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally), color = Primary)
        } else {
            LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                items(doctors) { doctor ->
                    DoctorScheduleCard(doctor) { updatedDoctor ->
                        scope.launch {
                            try {
                                doctorRepository.updateDoctor(updatedDoctor)
                                refreshDoctors()
                            } catch (e: Exception) {
                                android.util.Log.e("AdminSchedule", "Error updating doctor", e)
                                android.widget.Toast.makeText(context, "Failed to update doctor: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DoctorScheduleCard(doctor: Doctor, onUpdate: (Doctor) -> Unit) {
    var expanded by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = Color.White,
        shadowElevation = 1.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(doctor.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, contentDescription = null)
                }
            }

            val days = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                days.forEach { day ->
                    val isAvailable = doctor.availability.containsKey(day)
                    Surface(
                        modifier = Modifier.weight(1f).clickable {
                            val newAvailability = doctor.availability.toMutableMap()
                            if (isAvailable) newAvailability.remove(day)
                            else newAvailability[day] = listOf("09:00 AM - 11:00 AM", "05:00 PM - 07:00 PM")
                            onUpdate(doctor.copy(availability = newAvailability))
                        },
                        color = if (isAvailable) Primary else Color.LightGray.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            day,
                            modifier = Modifier.padding(vertical = 4.dp),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isAvailable) Color.White else OnBackground
                        )
                    }
                }
            }

            if (expanded) {
                Spacer(modifier = Modifier.height(16.dp))
                Text("Manage Time Slots", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)

                doctor.availability.forEach { (day, slots) ->
                    Column(modifier = Modifier.padding(vertical = 8.dp)) {
                        Text(day, fontWeight = FontWeight.Medium, color = Primary)
                        slots.forEachIndexed { index, slot ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(slot, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                                IconButton(onClick = {
                                    val newAvailability = doctor.availability.toMutableMap()
                                    val newSlots = slots.toMutableList()
                                    newSlots.removeAt(index)
                                    if (newSlots.isEmpty()) newAvailability.remove(day)
                                    else newAvailability[day] = newSlots
                                    onUpdate(doctor.copy(availability = newAvailability))
                                }) {
                                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.Red)
                                }
                            }
                        }

                        var showSlotDialog by remember { mutableStateOf(false) }
                        TextButton(onClick = { showSlotDialog = true }) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Text("Add Slot", style = MaterialTheme.typography.labelSmall)
                        }

                        if (showSlotDialog) {
                            var newSlot by remember { mutableStateOf("09:00 AM - 10:00 AM") }
                            AlertDialog(
                                onDismissRequest = { showSlotDialog = false },
                                title = { Text("Add Slot for $day") },
                                text = {
                                    OutlinedTextField(value = newSlot, onValueChange = { newSlot = it }, label = { Text("Time Slot (e.g. 10AM - 11AM)") })
                                },
                                confirmButton = {
                                    Button(onClick = {
                                        val newAvailability = doctor.availability.toMutableMap()
                                        val newSlots = slots.toMutableList()
                                        newSlots.add(newSlot)
                                        newAvailability[day] = newSlots
                                        onUpdate(doctor.copy(availability = newAvailability))
                                        showSlotDialog = false
                                    }) { Text("Add") }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AdminDoctorCard(doctor: Doctor, onDelete: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        shadowElevation = 2.dp
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(56.dp).background(Primary.copy(alpha = 0.1f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Person, contentDescription = null, tint = Primary, modifier = Modifier.size(32.dp))
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(doctor.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                Text(doctor.specialty, color = Primary, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                Text("₹${doctor.consultationFee}", color = Tertiary, fontWeight = FontWeight.Bold)
                Text(doctor.email, color = Color.Gray, style = MaterialTheme.typography.labelMedium)
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFD32F2F))
            }
        }
    }
}

@Composable
fun DoctorAvailabilityCard(doctor: Doctor, onUpdate: (Doctor) -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = Color.White,
        shadowElevation = 1.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(doctor.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            val days = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                days.forEach { day ->
                    val isAvailable = doctor.availability.containsKey(day)
                    Surface(
                        modifier = Modifier.weight(1f).clickable {
                            val newAvailability = doctor.availability.toMutableMap()
                            if (isAvailable) newAvailability.remove(day)
                            else newAvailability[day] = listOf("09:00 AM - 05:00 PM") // Default slot
                            onUpdate(doctor.copy(availability = newAvailability))
                        },
                        color = if (isAvailable) Primary else Color.LightGray.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            day,
                            modifier = Modifier.padding(vertical = 4.dp),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isAvailable) Color.White else OnBackground
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AdminStatCard(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = Primary)
    }
}

@Composable
fun SplashScreen(onFinished: () -> Unit) {
    LaunchedEffect(Unit) { delay(1000); onFinished() }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = Primary)
    }
}
