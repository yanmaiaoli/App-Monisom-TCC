package com.example.monisom.ui

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.monisom.audio.RecordingState
import com.example.monisom.data.supabase
import com.example.monisom.ui.environment.EnvironmentScreen
import com.example.monisom.ui.forgot.ForgotPasswordScreen
import com.example.monisom.ui.home.HomeScreen
import com.example.monisom.ui.login.LoginScreen
import com.example.monisom.ui.monitoring.MonitoringScreen
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus

@Composable
fun MoniSomApp(
    pendingSchoolId: String? = null,
    pendingClassId: String? = null,
    onPendingConsumed: () -> Unit = {},
) {
    LaunchedEffect(Unit) {
        runCatching { supabase.auth.loadFromStorage() }
    }
    val sessionStatus by supabase.auth.sessionStatus.collectAsState()

    when (sessionStatus) {
        is SessionStatus.Initializing -> LoadingScreen()
        is SessionStatus.Authenticated -> MainNavHost(pendingSchoolId, pendingClassId, onPendingConsumed)
        else -> AuthNavHost()
    }
}

@Composable
private fun LoadingScreen() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            CircularProgressIndicator()
            Text("Carregando...")
        }
    }
}

@Composable
private fun AuthNavHost() {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = "login") {
        composable("login") {
            LoginScreen(onForgotPassword = { nav.navigate("forgot") })
        }
        composable("forgot") {
            ForgotPasswordScreen(onBack = { nav.popBackStack() })
        }
    }
}

@Composable
private fun MainNavHost(
    pendingSchoolId: String?,
    pendingClassId: String?,
    onPendingConsumed: () -> Unit,
) {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = "home") {
        composable("home") {
            HomeScreen(
                onOpenEnvironment = { schoolId, schoolName, classId, className ->
                    nav.navigate(
                        "environment/$schoolId/${Uri.encode(schoolName)}/$classId/${Uri.encode(className)}"
                    )
                },
            )
        }
        composable(
            "environment/{schoolId}/{schoolName}/{classId}/{className}",
            arguments = listOf(
                navArgument("schoolId") { type = NavType.StringType },
                navArgument("schoolName") { type = NavType.StringType },
                navArgument("classId") { type = NavType.StringType },
                navArgument("className") { type = NavType.StringType },
            ),
        ) { entry ->
            val schoolId = entry.arguments?.getString("schoolId").orEmpty()
            val schoolName = entry.arguments?.getString("schoolName").orEmpty()
            val className = entry.arguments?.getString("className").orEmpty()
            val classId = entry.arguments?.getString("classId").orEmpty()

            val refreshTrigger by entry.savedStateHandle
                .getStateFlow("refresh", false)
                .collectAsState()

            EnvironmentScreen(
                schoolId = schoolId,
                schoolName = schoolName,
                className = className,
                classId = classId,
                refreshTrigger = refreshTrigger,
                onConsumeRefresh = { entry.savedStateHandle["refresh"] = false },
                onBack = { nav.popBackStack() },
                onStartMonitoring = { nav.navigate("monitoring/$schoolId/$classId") },
            )
        }
        composable(
            "monitoring/{schoolId}/{classId}",
            arguments = listOf(
                navArgument("schoolId") { type = NavType.StringType },
                navArgument("classId") { type = NavType.StringType },
            ),
        ) { entry ->
            val schoolId = entry.arguments?.getString("schoolId").orEmpty()
            val classId = entry.arguments?.getString("classId").orEmpty()
            MonitoringScreen(
                schoolId = schoolId,
                classId = classId,
                onFinished = {
                    nav.previousBackStackEntry?.savedStateHandle?.set("refresh", true)
                    nav.popBackStack()
                },
            )
        }
    }

    LaunchedEffect(pendingSchoolId, pendingClassId) {
        if (pendingSchoolId != null && pendingClassId != null) {
            nav.navigate("monitoring/$pendingSchoolId/$pendingClassId") {
                launchSingleTop = true
            }
            onPendingConsumed()
        }
    }

    LaunchedEffect(Unit) {
        val sid = RecordingState.schoolId.value
        val cid = RecordingState.classId.value
        if (RecordingState.monitoringId.value != null && sid != null && cid != null) {
            nav.navigate("monitoring/$sid/$cid") {
                launchSingleTop = true
            }
        }
    }
}
