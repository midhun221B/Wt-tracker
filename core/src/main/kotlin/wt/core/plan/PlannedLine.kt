package wt.core.plan

import wt.core.model.Checkpoint
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Piecewise-linear planned weight through user-editable checkpoints. */
class PlannedLine(checkpoints: List<Checkpoint>) {
    val checkpoints: List<Checkpoint> = checkpoints.sortedBy { it.date }

    init {
        require(this.checkpoints.size >= 2) { "A plan needs at least a start and a goal" }
        require(this.checkpoints.zipWithNext().all { (a, b) -> a.date < b.date }) { "Checkpoint dates must be distinct" }
    }

    val start: Checkpoint get() = checkpoints.first()
    val goal: Checkpoint get() = checkpoints.last()

    /** Planned kg on [date]; flat before the start and after the goal. */
    fun at(date: LocalDate): Double {
        if (date <= start.date) return start.kg
        if (date >= goal.date) return goal.kg
        val (a, b) = checkpoints.zipWithNext().first { (_, b) -> date <= b.date }
        val span = ChronoUnit.DAYS.between(a.date, b.date).toDouble()
        val t = ChronoUnit.DAYS.between(a.date, date) / span
        return a.kg + (b.kg - a.kg) * t
    }

    /** Planned date the line first reaches [kg], or null if it never does. */
    fun dateReaching(kg: Double): LocalDate? {
        if (start.kg <= kg) return start.date
        for ((a, b) in checkpoints.zipWithNext()) {
            if (b.kg <= kg) {
                val span = ChronoUnit.DAYS.between(a.date, b.date).toDouble()
                val days = span * (a.kg - kg) / (a.kg - b.kg)
                return a.date.plusDays(Math.ceil(days - 1e-9).toLong())
            }
        }
        return null
    }
}
