package wt.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import wt.app.chart.WeightChart
import wt.core.summary.goalProgress
import wt.core.Safety
import wt.core.alerts.Alert
import wt.core.alerts.Severity
import wt.core.dashboard.Dashboard
import wt.core.summary.WeekSummary
import kotlin.math.abs

@Composable
fun DashboardScreen(d: Dashboard, modifier: Modifier = Modifier) {
    Column(
        modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        d.alerts.forEach { AlertCard(it) }
        ProgressCard(d)
        ChartCard(d)
        ForecastTiles(d)
        WeeksCard(d.weekly)
        BodyTiles(d)
        Disclaimer()
    }
}

@Composable
private fun AlertCard(alert: Alert) {
    val warning = alert.severity == Severity.WARNING
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (warning) Color(0xFF2E2614) else Palette.Card,
            contentColor = Palette.Text,
        ),
    ) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(
                if (warning) Icons.Default.Warning else Icons.Default.Info,
                contentDescription = null,
                tint = if (warning) Palette.Warn else Palette.Accent,
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(alert.title, style = MaterialTheme.typography.titleSmall)
                Text(alert.message, style = MaterialTheme.typography.bodyMedium, color = Palette.Muted)
            }
        }
    }
}

/** Ring toward the goal, with the trend, rate and gap vs plan beside it. */
@Composable
private fun ProgressCard(d: Dashboard) {
    val f = d.forecast
    val goalKg = d.plan.goal.kg
    // Same rule as the "Weigh-in saved" card: first weigh-in to today's trend.
    val g = goalProgress(d.weights, d.asOf, goalKg, d.plan.start.kg)

    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Palette.Card, contentColor = Palette.Text),
    ) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            ProgressRing(g.fraction.toFloat(), Modifier.size(150.dp)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("%.1f".format(g.currentKg), style = numberStyle(50.sp))
                    Text(if (g.fromTrend) "kg trend" else "kg latest", style = MaterialTheme.typography.labelMedium, color = Palette.Muted)
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (f != null) {
                    RingStat("%+.2f kg".format(f.kgPerWeek), "per week", Palette.Accent)
                }
                RingStat("%.1f of %.1f kg".format(g.lostKg, g.totalKg), "lost toward %.0f kg".format(goalKg))
                if (f != null) {
                    val over = f.gapKgToday > 0.05
                    RingStat(
                        "%+.1f kg".format(f.gapKgToday),
                        if (over) "above plan" else "below plan",
                        if (over) Palette.Warn else Palette.Accent,
                    )
                } else {
                    Text("Log 3 days to see your trend", style = MaterialTheme.typography.labelMedium, color = Palette.Muted)
                }
            }
        }
    }
}

@Composable
private fun RingStat(value: String, label: String, color: Color = Palette.Text) {
    Column {
        Text(value, style = numberStyle(24.sp), color = color)
        Text(label, style = MaterialTheme.typography.labelMedium, color = Palette.Muted)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChartCard(d: Dashboard) {
    val c = LocalSeriesColors.current
    SectionCard("Plan vs reality", trailing = "${dayMonth(d.plan.start.date)} – ${dayMonth(d.plan.goal.date)}") {
        WeightChart(d, Modifier.fillMaxWidth().height(220.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            LegendItem(c.planned, "Planned", dashed = true)
            LegendItem(c.realistic, "Realistic")
            LegendItem(c.band, "80% range", thick = true)
        }
        if (d.forecast?.lowConfidence == true) {
            Text(
                "Low confidence: fewer than 7 weigh-ins in the last 21 days, so the forecast can swing a lot.",
                style = MaterialTheme.typography.labelMedium,
                color = Palette.Warn,
            )
        }
    }
}

@Composable
private fun ForecastTiles(d: Dashboard) {
    val f = d.forecast ?: return
    val e = d.energy
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TileRow { m ->
            StatTile(
                "Forecast for ${dayMonth(f.goalDate)}", "%.1f kg".format(f.predictedAtGoal), m,
                sub = "range %.1f–%.1f".format(f.bandAtGoal.first, f.bandAtGoal.second),
            )
            val eta = f.eta
            val gap = f.gapDays
            StatTile(
                "%.0f kg reached".format(f.goalKg), eta?.let(::dayMonth) ?: "Not yet", m,
                sub = when {
                    eta == null -> "trend is flat or rising"
                    gap == null || gap == 0L -> "on plan"
                    gap > 0 -> "$gap days after plan"
                    else -> "${abs(gap)} days before plan"
                },
                subColor = if ((gap ?: Long.MAX_VALUE) > 3) Palette.Warn else Palette.Accent,
            )
        }
        if (e != null) {
            TileRow { m ->
                StatTile(
                    "Eating now", "≈" + roughKcal(e.estimatedIntake), m,
                    sub = "kcal/day · deficit ${roughDeficit(e.actualDeficit)}",
                )
                // Difference of the rounded figures, so the three numbers on screen agree.
                val change = round50(e.targetIntake) - round50(e.estimatedIntake)
                StatTile(
                    if (e.requiredKgPerWeek == null) "Goal date passed" else "To get back on plan",
                    if (e.requiredKgPerWeek == null) "Re-baseline" else "≈" + roughKcal(e.targetIntake),
                    m,
                    sub = when {
                        e.requiredKgPerWeek == null -> "in Plan"
                        change == 0 -> "kcal/day · on pace"
                        change < 0 -> "kcal/day · %,d less".format(-change)
                        else -> "kcal/day · %,d more".format(change)
                    },
                    highlight = true,
                )
            }
            Text(
                "The plan assumes ≈${roughKcal(e.plannedIntake)} kcal/day (deficit ${roughDeficit(e.expectedDeficit)}). " +
                    "Worked out from your weight trend, not food logs, so treat these as estimates.",
                style = MaterialTheme.typography.labelMedium,
                color = Palette.Muted,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
            if (e.intakeFloored) {
                Text(
                    "Limited to ${Safety.MIN_INTAKE_KCAL.toInt()} kcal/day. Consider re-baselining instead of eating less.",
                    style = MaterialTheme.typography.labelMedium,
                    color = Palette.Warn,
                )
            }
        }
    }
}

/** A deficit rounded to 10 kcal ("360"). */
private fun roughDeficit(v: Double) = "%,d".format((Math.round(v / 10.0) * 10).toInt())

/** Last five weeks: km run as bars, average weight underneath. */
@Composable
private fun WeeksCard(weeks: List<WeekSummary>) {
    if (weeks.isEmpty()) return
    val shown = weeks.take(5).reversed() // weekly is newest first
    val maxKm = shown.maxOf { it.km }.coerceAtLeast(1.0)
    val newest = shown.last()
    val summary = "This week %.1f km".format(newest.km) + (newest.avgKg?.let { " · %.1f kg".format(it) } ?: "")
    CollapsibleSection("Weeks", summary) {
        Text("km run and weight, last five weeks", style = MaterialTheme.typography.labelMedium, color = Palette.Muted)
        Row(Modifier.fillMaxWidth().height(120.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Bottom) {
            shown.forEach { w ->
                val current = w == newest
                Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                    Text("%.1f".format(w.km), style = MaterialTheme.typography.labelSmall, color = if (current) Palette.Accent else Palette.Muted)
                    Box(
                        Modifier.padding(top = 6.dp).fillMaxWidth()
                            .height((90 * (w.km / maxKm)).dp.coerceAtLeast(4.dp))
                            .background(if (current) Palette.Accent else Palette.CardHigh, RoundedCornerShape(6.dp)),
                    )
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            shown.forEach { w ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        w.avgKg?.let { "%.1f".format(it) } ?: "–",
                        style = numberStyle(20.sp),
                        color = if (w == newest) Palette.Accent else Palette.Text,
                    )
                    Text(
                        weekLabel(w) + (w.changeKg?.let { " · %+.1f".format(it) } ?: ""),
                        style = MaterialTheme.typography.labelSmall,
                        color = Palette.Muted,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
private fun BodyTiles(d: Dashboard) {
    if (d.body.isEmpty()) return
    val first = d.body.first()
    val last = d.body.last()
    CollapsibleSection("Body", "Fat %.1f%% · visceral %.1f".format(last.fatPct, last.visceral)) {
        TileRow { m ->
            StatTile("Body fat", "%.1f%%".format(last.fatPct), m, sub = "%+.1f since start".format(last.fatPct - first.fatPct))
            StatTile("Visceral fat", "%.1f".format(last.visceral), m, sub = "%+.1f since start".format(last.visceral - first.visceral))
        }
    }
}
