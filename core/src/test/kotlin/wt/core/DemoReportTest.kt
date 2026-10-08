package wt.core

import wt.core.alerts.evaluateAlerts
import wt.core.energy.energyBalance
import wt.core.model.Defaults
import wt.core.model.Run
import wt.core.plan.PlannedLine
import wt.core.plan.rebaseline
import wt.core.trend.fitTrend
import wt.core.trend.forecast
import kotlin.test.Test

/** Prints a sample dashboard for a synthetic month (0.38 kg/week, noisy, some skipped days). */
class DemoReportTest {
    @Test
    fun printSampleForecast() {
        val weights = Synthetic.series(days = 30, kgPerWeek = 0.38, noiseSd = 0.35, seed = 42, logProbability = 0.85)
        val asOf = weights.last().date
        val runs = (0 until 30).filter { it % 7 !in setOf(2, 5) }
            .map { Run(Defaults.START.plusDays(it.toLong()), 3.0, 21 * 60) }
        val plan = PlannedLine(Defaults.planCheckpoints)
        val fit = fitTrend(weights, asOf)!!
        val f = forecast(fit, plan)
        val e = energyBalance(fit, runs, Defaults.profile)
        val alerts = evaluateAlerts(weights, runs, emptySet(), asOf, e.estimatedIntake)
        val rb = rebaseline(weights, asOf, Defaults.GOAL_DATE, Defaults.GOAL_KG)

        println(
            """
            |==== Sample forecast as of $asOf (${weights.size} weigh-ins, ${runs.size} runs) ====
            |Trend today       %.1f kg   (plan %.1f kg, gap %+.1f kg)
            |Trend rate        %.2f kg/week  (fit on ${fit.n} points since ${fit.windowStart}${if (fit.lowConfidence) ", LOW CONFIDENCE" else ""})
            |On ${f.goalDate}  %.1f kg predicted, 80%% band %.1f–%.1f kg (gap vs plan %+.1f kg)
            |82 kg reached     ${f.eta ?: "not reached"} (range ${f.etaEarly ?: "?"} … ${f.etaLate ?: "not reached"}), ${f.gapDays?.let { "%+d days vs plan".format(it) } ?: ""}
            |Actual deficit    %.0f kcal/day   expected %.0f (food 350 + runs %.0f)
            |Needed rate       %.2f kg/week → intake change %+.0f kcal/day, target ≈ %.0f kcal/day
            |Re-baseline       %.1f kg → 82.0 kg needs %.2f kg/week${if (rb.unrealistic) " (UNREALISTIC)" else ""}
            |Alerts            ${alerts.joinToString("; ") { it.title }.ifEmpty { "none" }}
            |${Safety.DISCLAIMER}
            """.trimMargin().format(
                f.trendToday, f.plannedToday, f.gapKgToday,
                f.kgPerWeek,
                f.predictedAtGoal, f.bandAtGoal.first, f.bandAtGoal.second, f.gapKgAtGoal,
                e.actualDeficit, e.expectedDeficit, e.runNetKcalPerDay,
                e.requiredKgPerWeek, e.intakeChange, e.targetIntake,
                rb.startKg, rb.requiredKgPerWeek,
            ),
        )
    }
}
