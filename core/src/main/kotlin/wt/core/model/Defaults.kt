package wt.core.model

import java.time.LocalDate

/** Starting data used to seed a fresh install. */
object Defaults {
    val START: LocalDate = LocalDate.of(2026, 10, 8)
    val GOAL_DATE: LocalDate = LocalDate.of(2027, 1, 7)
    const val START_KG = 88.3
    const val GOAL_KG = 82.0

    val profile = Profile(
        sex = "male",
        birthYear = 1994,
        heightCm = 170.0,
        bmrKcal = 1818.0,
        goalKg = GOAL_KG,
        goalDate = GOAL_DATE,
    )

    val planCheckpoints = listOf(
        Checkpoint(START, START_KG),
        Checkpoint(LocalDate.of(2026, 11, 8), 86.2),
        Checkpoint(LocalDate.of(2026, 12, 8), 84.0),
        Checkpoint(GOAL_DATE, GOAL_KG),
    )

    val startBody = BodyComp(
        date = START,
        fatPct = 29.2,
        visceral = 16.0,
        muscleKg = 60.1,
        skeletalPct = 37.0,
        leanKg = 62.5,
        bmrKcal = 1818.0,
    )

    val startWeight = WeightEntry(START, START_KG)

    /** Sample runs; the user assigns dates in the app. */
    val sampleRuns = listOf(
        Run(null, 2.81, 20 * 60 + 16),
        Run(null, 2.37, 15 * 60 + 46),
        Run(null, 3.45, 24 * 60 + 19),
        Run(null, 3.14, 21 * 60 + 23),
        Run(null, 2.54, 17 * 60 + 28),
    )
}
