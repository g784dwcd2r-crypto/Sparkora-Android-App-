package com.sparkora.app.ui.timeoff

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
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
import com.sparkora.app.data.api.TimeOffDto
import com.sparkora.app.ui.EmptyState
import com.sparkora.app.ui.LoadingBox
import com.sparkora.app.ui.StatusChip
import com.sparkora.app.ui.screenPadding
import com.sparkora.app.util.Dates
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Locale

private val LEAVE_TYPES = listOf("annual", "sick", "unpaid", "other")

@Composable
fun TimeOffScreen() {
    val container = LocalContext.current.appContainer()
    val vm: TimeOffViewModel = viewModel { TimeOffViewModel(container.repository) }
    val ui by vm.ui.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    var showForm by remember { mutableStateOf(false) }

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

    if (showForm) {
        RequestLeaveDialog(
            busy = ui.busy,
            onDismiss = { showForm = false },
            onSubmit = { start, end, type, reason ->
                vm.submit(start, end, type, reason)
                showForm = false
            },
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showForm = true },
                icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                text = { Text("Request leave") },
            )
        },
    ) { padding ->
        when {
            ui.loading -> LoadingBox(Modifier.padding(padding))
            ui.requests.isEmpty() -> Column(Modifier.padding(padding)) {
                EmptyState(
                    title = "No leave requests yet",
                    subtitle = "Tap “Request leave” to ask for time off.",
                )
            }
            else -> LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = screenPadding(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(ui.requests, key = { it.id }) { request ->
                    TimeOffCard(
                        request = request,
                        busy = ui.busy,
                        onCancel = { vm.cancel(request) },
                    )
                }
            }
        }
    }
}

@Composable
private fun TimeOffCard(
    request: TimeOffDto,
    busy: Boolean,
    onCancel: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        (request.leaveType ?: "leave").replaceFirstChar {
                            if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
                        } + " leave",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "${Dates.formatDay(request.startDate)} → ${Dates.formatDay(request.endDate)}" +
                            (request.requestedDays?.let { days ->
                                val d = if (days % 1.0 == 0.0) days.toInt().toString() else days.toString()
                                "  ·  $d day${if (days > 1) "s" else ""}"
                            } ?: ""),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                StatusChip(request.status)
            }
            if (!request.reason.isNullOrBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    request.reason,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (request.status == "pending") {
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onCancel, enabled = !busy) {
                    Text("Cancel request")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RequestLeaveDialog(
    busy: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (LocalDate, LocalDate, String, String) -> Unit,
) {
    var leaveType by remember { mutableStateOf("annual") }
    var typeMenuOpen by remember { mutableStateOf(false) }
    var startDate by remember { mutableStateOf<LocalDate?>(null) }
    var endDate by remember { mutableStateOf<LocalDate?>(null) }
    var reason by remember { mutableStateOf("") }
    var pickingStart by remember { mutableStateOf(false) }
    var pickingEnd by remember { mutableStateOf(false) }

    if (pickingStart || pickingEnd) {
        val initial = (if (pickingStart) startDate else endDate) ?: LocalDate.now()
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = {
                pickingStart = false
                pickingEnd = false
            },
            confirmButton = {
                TextButton(onClick = {
                    val millis = pickerState.selectedDateMillis
                    if (millis != null) {
                        val picked = Instant.ofEpochMilli(millis)
                            .atZone(ZoneOffset.UTC)
                            .toLocalDate()
                        if (pickingStart) {
                            startDate = picked
                            if (endDate == null || endDate!!.isBefore(picked)) endDate = picked
                        } else {
                            endDate = picked
                        }
                    }
                    pickingStart = false
                    pickingEnd = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = {
                    pickingStart = false
                    pickingEnd = false
                }) { Text("Cancel") }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Request leave") },
        text = {
            Column {
                OutlinedButton(
                    onClick = { typeMenuOpen = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        leaveType.replaceFirstChar {
                            if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
                        } + " leave"
                    )
                }
                DropdownMenu(
                    expanded = typeMenuOpen,
                    onDismissRequest = { typeMenuOpen = false },
                ) {
                    LEAVE_TYPES.forEach { type ->
                        DropdownMenuItem(
                            text = {
                                Text(type.replaceFirstChar {
                                    if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
                                })
                            },
                            onClick = {
                                leaveType = type
                                typeMenuOpen = false
                            },
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { pickingStart = true },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(startDate?.let { Dates.formatDayShort(it) } ?: "Start date")
                    }
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(
                        onClick = { pickingEnd = true },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(endDate?.let { Dates.formatDayShort(it) } ?: "End date")
                    }
                }

                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("Reason (optional)") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = !busy && startDate != null && endDate != null &&
                    !endDate!!.isBefore(startDate!!),
                onClick = { onSubmit(startDate!!, endDate!!, leaveType, reason) },
            ) { Text("Submit") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
