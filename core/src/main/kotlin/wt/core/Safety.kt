package wt.core

/** Hard limits that every suggestion in the app respects. */
object Safety {
    const val MIN_INTAKE_KCAL = 1800.0
    const val MAX_LOSS_KG_PER_WEEK = 1.0
    const val UNREALISTIC_KG_PER_WEEK = 0.7
    const val KCAL_PER_KG = 7700.0

    const val DISCLAIMER = "Estimates, not medical advice."

    /** Caps a desired loss rate (positive kg/week) at the safe maximum. */
    fun capLossPerWeek(kgPerWeek: Double): Double = kgPerWeek.coerceAtMost(MAX_LOSS_KG_PER_WEEK)

    fun floorIntake(kcal: Double): Double = kcal.coerceAtLeast(MIN_INTAKE_KCAL)
}
