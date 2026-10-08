package wt.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import wt.app.chart.MiniLineChart
import wt.app.chart.WeightChart
import wt.core.Safety
import wt.core.alerts.Alert
import wt.core.alerts.Severity
import wt.core.dashboard.Dashboard
import wt.core.summary.WeekSummary
import kotlin.math.abs

@Composable
fun DashboardScreen(d: Dashboard, modifier: Modifier = Modifier) {
    Column(
        modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        d.alerts.forEach { AlertCard(it) }
        ChartCard(d)
        ForecastTiles(d)
        EnergyCard(d)
        WeeklyTable(d.weekly)
        BodyTrendCard(d)
        Disclaimer()
    }
}

@Composable
private fun AlertCard(alert: Alert) {
    val warning = alert.severity == Severity.WARNING
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (warning) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
        ),
    ) {
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(if (warning) Icons.Default.Warning else Icons.Default.Info, contentDescription = null)
            Column {
                Text(alert.title, style = MaterialTheme.typography.titleSmall)
                Text(alert.message, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChartCard(d: Dashboard) {
    val c = LocalSeriesColors.current
    SectionCard("Planned vs realistic") {
        WeightChart(d, Modifier.fillMaxWidth().height(260.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            LegendItem(c.planned, "Planned", dashed = true)
            LegendItem(c.realistic, "Realistic trend")
            LegendItem(c.band, "80% band", thick = true)
            LegendItem(c.average, "7-day avg")
            LegendItem(c.raw, "Weigh-ins")
        }
    }
}

@Composable
private fun ForecastTiles(d: Dashboard) {
    val f = d.forecast
    val c = LocalSeriesColors.current
    if (f == null) {
        SectionCard("Forecast") {
            Text(
                "Log at least 3 weigh-ins on different days to see the realistic trend. " +
                    "Daily weigh-ins for a week or two give a reliable forecast.",
            )
            d.latestWeight?.let { Text("Latest: ${kg(it.kg)} on ${it.date} · plan today ${kg(d.plan.at(d.asOf))}") }
        }
        return
    }
    if (f.lowConfidence) {
        Text(
            "Low confidence: fewer than 7 weigh-ins in the last 21 days, so the forecast uses all data and may swing a lot.",
            style = MaterialTheme.typography.bodySmall,
            color = c.warn,
        )
    }
    TileRow { m ->
        StatTile(
            "Trend weight", kg(f.trendToday), m,
            sub = "plan ${kg(f.plannedToday)} · ${signedKg(f.gapKgToday)}",
            valueColor = if (f.gapKgToday <= 0.2) c.good else c.warn,
        )
        StatTile("Rate", "%+.2f kg/wk".format(f.kgPerWeek), m, sub = "fit on ${f.fit.n} weigh-ins since ${short(f.fit.windowStart)}")
    }
    TileRow { m ->
        StatTile(
            "On ${f.goalDate}", kg(f.predictedAtGoal), m,
            sub = "80%%: %.1f–%.1f · %s vs plan".format(f.bandAtGoal.first, f.bandAtGoal.second, signedKg(f.gapKgAtGoal)),
            valueColor = if (f.gapKgAtGoal <= 0.2) c.good else c.warn,
        )
        val eta = f.eta
        StatTile(
            "Reach ${"%.1f".format(f.goalKg)} kg", eta?.toString() ?: "Not reached", m,
            sub = if (eta == null) "trend is flat or rising" else buildString {
                val gap = f.gapDays ?: 0
                append(if (gap == 0L) "on plan" else if (gap > 0) "$gap days late" else "${abs(gap)} days early")
                append(" · ${f.etaEarly?.let(::short) ?: "?"} to ${f.etaLate?.let(::short) ?: "never"}")
            },
            valueColor = if ((f.gapDays ?: Long.MAX_VALUE) <= 3) c.good else c.warn,
        )
    }
}

@Composable
private fun EnergyCard(d: Dashboard) {
    val e = d.energy ?: return
    SectionCard("Energy balance (estimate)") {
        TileRow { m ->
            StatTile("Actual deficit", kcal(e.actualDeficit) + "/d", m, sub = "from the trend · 7700 kcal/kg")
            StatTile(
                "Expected deficit", kcal(e.expectedDeficit) + "/d", m,
                sub = "food ${kcal(e.expectedDeficit - e.runNetKcalPerDay)} + runs ${kcal(e.runNetKcalPerDay)}",
            )
        }
        val required = e.requiredKgPerWeek
        if (required == null) {
            Text("The goal date has passed. Re-baseline the plan to set a new target.")
        } else {
            val action = when {
                abs(e.intakeChange) < 25 -> "You're on pace. Keep intake as it is."
                e.intakeChange < 0 -> "Eat about ${kcal(-e.intakeChange)} less per day to get back on plan."
                else -> "You're ahead of plan; you could eat about ${kcal(e.intakeChange)} more per day."
            }
            Text(action, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(
                "Needs %.2f kg/week from here%s. Estimated intake ≈ %s, suggested ≈ %s/day."
                    .format(required, if (e.requiredCapped) " (capped at ${Safety.MAX_LOSS_KG_PER_WEEK} kg/week)" else "", kcal(e.estimatedIntake), kcal(e.targetIntake)),
                style = MaterialTheme.typography.bodySmall,
            )
            if (e.intakeFloored) {
                Text(
                    "Limited to ${Safety.MIN_INTAKE_KCAL.toInt()} kcal/day. Consider re-baselining instead of eating less.",
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalSeriesColors.current.warn,
                )
            }
        }
        Text(
            "Maintenance ≈ ${kcal(e.maintenanceKcal)} (BMR × activity factor) plus running; adjust both in Plan.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun WeeklyTable(weeks: List<WeekSummary>) {
    if (weeks.isEmpty()) return
    SectionCard("Weekly") {
        val header = MaterialTheme.typography.labelMedium
        TableRow(listOf("Week", "Avg", "Change", "Runs", "km", "Rest"), header)
        HorizontalDivider()
        weeks.forEach { w ->
            TableRow(
                listOf(
                    "W${w.index} ${short(w.start)}",
                    w.avgKg?.let { "%.1f".format(it) } ?: "–",
                    w.changeKg?.let { "%+.1f".format(it) } ?: "–",
                    w.runs.toString(),
                    "%.1f".format(w.km),
                    w.restDays.toString(),
                ),
                MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

private val columnWeights = listOf(2.2f, 1.2f, 1.3f, 0.9f, 1f, 0.9f)

@Composable
private fun TableRow(cells: List<String>, style: androidx.compose.ui.text.TextStyle) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        cells.forEachIndexed { i, text ->
            Text(
                text, style = style, modifier = Modifier.weight(columnWeights[i]),
                textAlign = if (i == 0) TextAlign.Start else TextAlign.End,
            )
        }
    }
}

@Composable
private fun BodyTrendCard(d: Dashboard) {
    if (d.body.isEmpty()) return
    val c = LocalSeriesColors.current
    SectionCard("Body composition") {
        val first = d.body.first()
        val last = d.body.last()
        Text("Body fat %.1f%% (%+.1f) · visceral %.1f (%+.1f)".format(last.fatPct, last.fatPct - first.fatPct, last.visceral, last.visceral - first.visceral))
        Text("Body fat %", style = MaterialTheme.typography.labelMedium)
        MiniLineChart(d.body.map { it.date to it.fatPct }, c.realistic, Modifier.fillMaxWidth().height(90.dp))
        Text("Visceral fat", style = MaterialTheme.typography.labelMedium)
        MiniLineChart(d.body.map { it.date to it.visceral }, c.planned, Modifier.fillMaxWidth().height(90.dp))
    }
}
