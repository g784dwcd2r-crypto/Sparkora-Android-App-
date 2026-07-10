package com.sparkora.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BeachAccess
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.sparkora.app.appContainer
import com.sparkora.app.data.SessionState
import com.sparkora.app.ui.home.HomeScreen
import com.sparkora.app.ui.login.LoginScreen
import com.sparkora.app.ui.payslips.PayslipsScreen
import com.sparkora.app.ui.profile.ProfileScreen
import com.sparkora.app.ui.schedule.ScheduleScreen
import com.sparkora.app.ui.timeoff.TimeOffScreen

private enum class Tab(val route: String, val label: String, val icon: ImageVector) {
    Today("today", "Today", Icons.Outlined.Timer),
    Schedule("schedule", "Schedule", Icons.Outlined.CalendarMonth),
    Leave("leave", "Leave", Icons.Outlined.BeachAccess),
    Payslips("payslips", "Pay", Icons.Outlined.Payments),
    Profile("profile", "Profile", Icons.Outlined.Person),
}

@Composable
fun SparkoraRoot() {
    val container = LocalContext.current.appContainer()
    val session by produceState<SessionState?>(initialValue = null) {
        container.session.load()
        container.session.state.collect { value = it }
    }

    when {
        session == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        session?.isLoggedIn != true -> LoginScreen()
        else -> MainScaffold(session!!)
    }
}

@Composable
private fun MainScaffold(session: SessionState) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    Scaffold(
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { tab ->
                    val selected = currentDestination?.hierarchy
                        ?.any { it.route == tab.route } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(tab.route) {
                                popUpTo(navController.graph.startDestinationId) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) },
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Tab.Today.route,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(Tab.Today.route) { HomeScreen(session) }
            composable(Tab.Schedule.route) { ScheduleScreen() }
            composable(Tab.Leave.route) { TimeOffScreen() }
            composable(Tab.Payslips.route) { PayslipsScreen() }
            composable(Tab.Profile.route) { ProfileScreen(session) }
        }
    }
}
