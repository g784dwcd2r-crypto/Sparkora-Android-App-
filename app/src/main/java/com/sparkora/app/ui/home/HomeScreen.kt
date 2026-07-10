package com.sparkora.app.ui.home

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sparkora.app.appContainer
import com.sparkora.app.data.SessionState
import com.sparkora.app.data.api.ClockEntryDto
import com.sparkora.app.data.api.ScheduleDto
import com.sparkora.app.ui.EmptyState
import com.sparkora.app.ui.LoadingBox
import com.sparkora.app.ui.StatusChip
import com.sparkora.app.ui.screenPadding
import com.sparkora.app.util.Dates
import kotlinx.coroutines.delay
import java.time.Instant

@Composable
fun HomeScreen(session: SessionState) {
    val container = LocalContext.current.appContainer()
    val vm: HomeViewModel = viewModel { HomeViewModel(container.repository, container.session, container.location) }
    val ui by vm.ui.collectAsState()
    val snackbar = remember { SnackbarHostState() }

    // Location permission gate: run the pending clock action after the user answers.
    var pendingAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        // Proceed either way — clocking without GPS is allowed, just unverified.
        pendingAction?.invoke()
        pendingAction = null
    }

    fun withLocationPermission(action: () -> Unit) {
        if (container.location.hasPermission()) {
            action()
        } else {
            pendingAction = action
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                )
            )
        }
    }

    LaunchedEffect(ui.notice) {
        ui.notice?.let {
            snackbar.showSnackbar(it)
            vm.consumeNotice()
        }
    }
    LaunchedEffect(ui.error) {
        ui.error?.let {
            snackbar.showSnackbar(it)
            vm.consumeError()
        }
    }

    ui.overridePrompt?.let { prompt ->
        AlertDialog(
            onDismissRequest = vm::dismissOverride,
            title = { Text("Outside the allowed area") },
            text = { Text(prompt.message) },
            confirmButton = {
                TextButton(onClick = vm::confirmOverride) { Text("Clock in anyway") }
            },
            dismissButton = {
                TextButton(onClick = vm::dismissOverride) { Text("Cancel") }
            },
        )
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        if (ui.loading) {
            LoadingBox(Modifier.padding(padding))
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = screenPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Column {
                    Text(
                        "Hi ${session.employeeName.ifBlank { "there" }} 👋",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        Dates.formatDayLong(Dates.today()),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item {
                val active = ui.activeEntry
                if (active != null) {
                    ActiveShiftCard(
                        entry = active,
                        busy = ui.busy,
                        onClockOut = { withLocationPermission { vm.clockOut() } },
                    )
                } else {
                    NotClockedInCard(
                        busy = ui.busy,
                        onClockInNoJob = { withLocationPermission { vm.clockIn(null) } },
                    )
                }
            }

            item {
                Text(
                    "Today's jobs",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            if (ui.todayJobs.isEmpty()) {
                item {
                    EmptyState(
                        title = "No jobs scheduled today",
                        subtitle = "Check the Schedule tab for the rest of your week.",
                    )
                }
            } else {
                items(ui.todayJobs, key = { it.id }) { job ->
                    JobCard(
                        job = job,
                        canClockIn = ui.activeEntry == null,
                        busy = ui.busy,
                        onClockIn = { withLocationPermission { vm.clockIn(job) } },
                    )
                }
            }

            if (ui.todayEntries.isNotEmpty()) {
                item {
                    Text(
                        "Today's activity",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                items(ui.todayEntries, key = { "entry-${it.id}" }) { entry ->
                    EntryRow(entry)
                }
            }
        }
    }
}

@Composable
private fun ActiveShiftCard(
    entry: ClockEntryDto,
    busy: Boolean,
    onClockOut: () -> Unit,
) {
    // Live ticking duration while the shift is open.
    var now by remember { mutableStateOf(Instant.now()) }
    LaunchedEffect(entry.id) {
        while (true) {
            now = Instant.now()
            delay(1_000L)
        }
    }
    val duration = Dates.durationSince(entry.clockIn, now)

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.Timer,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "On shift",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                duration?.let { Dates.formatDuration(it) } ?: "—",
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "Since ${Dates.formatInstantTime(entry.clockIn)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            if (entry.geoVerified == true) {
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Outlined.LocationOn,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "Location verified",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onClockOut,
                enabled = !busy,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
            ) {
                Icon(Icons.Outlined.Stop, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Clock out")
            }
        }
    }
}

@Composable
private fun NotClockedInCard(
    busy: Boolean,
    onClockInNoJob: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp)) {
            Text(
                "You're not clocked in",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Clock in from one of today's jobs below, or start an unassigned shift.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = onClockInNoJob, enabled = !busy) {
                Icon(Icons.Outlined.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Clock in without a job")
            }
        }
    }
}

@Composable
private fun JobCard(
    job: ScheduleDto,
    canClockIn: Boolean,
    busy: Boolean,
    onClockIn: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        job.clientName ?: "Unassigned client",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "${Dates.formatClock(job.startTime)} – ${Dates.formatClock(job.endTime)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                StatusChip(job.status)
            }
            if (!job.notes.isNullOrBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    job.notes,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (canClockIn && job.status != "completed") {
                Spacer(Modifier.height(12.dp))
                Button(onClick = onClockIn, enabled = !busy) {
                    Icon(Icons.Outlined.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Clock in")
                }
            }
        }
    }
}

@Composable
private fun EntryRow(entry: ClockEntryDto) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Outlined.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "${Dates.formatInstantTime(entry.clockIn)} – " +
                        if (entry.clockOut.isNullOrBlank()) "now" else Dates.formatInstantTime(entry.clockOut),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
                val duration = entry.clockOut?.let {
                    Dates.durationSince(entry.clockIn, Dates.parseInstant(it) ?: Instant.now())
                }
                if (duration != null) {
                    Text(
                        Dates.formatDuration(duration),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (entry.geoVerified == true) {
                Icon(
                    Icons.Outlined.LocationOn,
                    contentDescription = "Location verified",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.secondary,
                )
            }
        }
    }
}
