package jp.tsuzutore.app.domain

/** The next unresolved side of a logical set. Saved outcomes, including skips, stay resolved. */
data class PendingExerciseSet(val setNumber: Int, val side: Side)

fun nextPendingSet(exercise: Exercise, results: List<SetResult>): PendingExerciseSet? {
    val resolved = results.filter { it.exerciseId == exercise.id }.map { it.setNumber to it.side }.toSet()
    val sides = if (exercise.bilateral) listOf(Side.LEFT, Side.RIGHT) else listOf(Side.BOTH)
    for (setNumber in 1..exercise.sets) {
        for (side in sides) {
            if ((setNumber to side) !in resolved) return PendingExerciseSet(setNumber, side)
        }
    }
    return null
}

/** Number of logical sets whose required sides are all resolved (completed, partial, or skipped). */
fun completedExerciseSets(exercise: Exercise, results: List<SetResult>): Int {
    val resolved = results.filter { it.exerciseId == exercise.id }.map { it.setNumber to it.side }.toSet()
    val sides = if (exercise.bilateral) listOf(Side.LEFT, Side.RIGHT) else listOf(Side.BOTH)
    return (1..exercise.sets).count { setNumber -> sides.all { (setNumber to it) in resolved } }
}
