package com.sparkora.app.ui.schedule

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sparkora.app.appContainer
import com.sparkora.app.data.api.ScheduleDto
import com.sparkora.app.ui.EmptyState
import com.sparkora.app.ui.LoadingBox
import com.sparkora.app.ui.StatusChip
import com.sparkora.app.ui.screenPadding
import com.sparkora.app.util.Dates
import java.time.LocalDate

@Composable
fun ScheduleScreen() {
    val container = LocalContext.current.appContainer()
    val vm: ScheduleViewModel = viewModel { ScheduleViewModel(container.repository) }
    val ui by vm.ui.collectAsState()

    Column(Modifier.fillMaxSize()) {
        WeekSelector(
            weekStart = ui.weekStart,
            onPrev = vm::previousWeek,
            onNext = vm::nextWeek,
            onToday = vm::thisWeek,
        )

        when {
            ui.loading -> LoadingBox()
            ui.error != null -> EmptyState(
                title = "Couldn't load your schedule",
                subtitle = ui.error,
            )
            else -> {
                val days = (0..6L).map { ui.weekStart.plusDays(it) }
                LazyColumn(
                    contentPadding = screenPadding(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    days.forEach { day ->
                        val jobs = ui.jobsByDay[day].orEmpty()
                        item(key = "header-$day") {
                            DayHeader(day = day, jobCount = jobs.size)
                        }
                        items(jobs, key = { "job-${it.id}" }) { job ->
                            ScheduleJobCard(job)
                        }
                    }
                    if (ui.jobsByDay.values.all { it.isEmpty() }) {
                        item {
                            EmptyState(
                                title = "Nothing scheduled this week",
                                subtitle = "Enjoy the quiet — or check another week.",
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WeekSelector(
    weekStart: LocalDate,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit,
) {
    val weekEnd = weekStart.plusDays(6)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onPrev) {
            Icon(Icons.Outlined.ChevronLeft, contentDescription = "Previous week")
        }
        Column(
            Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "${Dates.formatDayShort(weekStart)} – ${Dates.formatDayShort(weekEnd)}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            if (Dates.today() !in weekStart..weekEnd) {
                TextButton(onClick = onToday) { Text("Back to this week") }
            }
        }
        IconButton(onClick = onNext) {
            Icon(Icons.Outlined.ChevronRight, contentDescription = "Next week")
        }
    }
}

@Composable
private fun DayHeader(day: LocalDate, jobCount: Int) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            Dates.formatDayLong(day),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = if (day == Dates.today()) FontWeight.Bold else FontWeight.SemiBold,
            color = if (day == Dates.today()) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            modifier = Modifier.weight(1f),
        )
        Text(
            if (jobCount == 0) "No jobs" else "$jobCount job${if (jobCount > 1) "s" else ""}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ScheduleJobCard(job: ScheduleDto) {
    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
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
                if (!job.notes.isNullOrBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        job.notes,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            StatusChip(job.status)
        }
    }
}
