package com.sparkora.app.ui.payslips

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.padding
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sparkora.app.appContainer
import com.sparkora.app.data.api.PayslipDto
import com.sparkora.app.ui.EmptyState
import com.sparkora.app.ui.LoadingBox
import com.sparkora.app.ui.StatusChip
import com.sparkora.app.ui.money
import com.sparkora.app.ui.screenPadding
import com.sparkora.app.util.Dates

@Composable
fun PayslipsScreen() {
    val container = LocalContext.current.appContainer()
    val vm: PayslipsViewModel = viewModel { PayslipsViewModel(container.repository) }
    val ui by vm.ui.collectAsState()

    when {
        ui.loading -> LoadingBox()
        ui.error != null -> EmptyState(
            title = "Couldn't load payslips",
            subtitle = ui.error,
        )
        ui.payslips.isEmpty() -> EmptyState(
            title = "No payslips yet",
            subtitle = "Payslips appear here once your manager issues them.",
        )
        else -> LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = screenPadding(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(ui.payslips, key = { it.id }) { payslip ->
                PayslipCard(payslip)
            }
        }
    }
}

@Composable
private fun PayslipCard(payslip: PayslipDto) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        Dates.formatMonth(payslip.month),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (!payslip.payslipNumber.isNullOrBlank()) {
                        Text(
                            payslip.payslipNumber,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                StatusChip(payslip.status)
            }

            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth()) {
                PayFigure(
                    label = "Hours",
                    value = payslip.totalHours?.let { "%.1f".format(it) } ?: "—",
                    modifier = Modifier.weight(1f),
                )
                PayFigure(
                    label = "Gross",
                    value = money(payslip.grossPay),
                    modifier = Modifier.weight(1f),
                )
                PayFigure(
                    label = "Net",
                    value = money(payslip.netPay),
                    emphasise = true,
                    modifier = Modifier.weight(1f),
                )
            }

            val deductions = listOfNotNull(payslip.socialCharges, payslip.taxEstimate)
                .filter { it > 0.0 }
            if (deductions.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))
                Text(
                    "Deductions: ${money(deductions.sum())}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun PayFigure(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    emphasise: Boolean = false,
) {
    Column(modifier) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = if (emphasise) FontWeight.Bold else FontWeight.Medium,
            color = if (emphasise) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        )
    }
}
