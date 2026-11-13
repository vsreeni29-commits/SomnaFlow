package com.somnaflow.app.data

/** kind: 0 = multiple choice, 1 = clock time (minutes after midnight), 2 = minutes, 3 = hours x10 */
class Question(val text: String, val options: List<String> = emptyList(), val kind: Int = 0, val default: Int = 0)

class Questionnaire(
    val id: String, val title: String, val about: String, val intro: String,
    val questions: List<Question>, val max: Int,
    val score: (IntArray) -> Int, val band: (Int) -> String
)

object Questionnaires {
    private val FREQ = listOf("Not during the past month", "Less than once a week", "Once or twice a week", "Three or more times a week")
    private val TWO_WEEKS = listOf("Not at all", "Several days", "More than half the days", "Nearly every day")

    private fun bucket(v: Int, vararg limits: Int): Int { for (i in limits.indices) if (v <= limits[i]) return i; return limits.size }

    /** PSQI component scoring (Buysse et al., 1989). Returns the seven component scores. */
    fun psqiComponents(a: IntArray): IntArray {
        val bed = a[0]; val latency = a[1]; val up = a[2]; val hours = a[3] / 10.0
        val c1 = a[14]
        val c2 = bucket(bucket(latency, 15, 30, 60) + a[4], 0, 2, 4)
        val c3 = if (hours > 7) 0 else if (hours >= 6) 1 else if (hours >= 5) 2 else 3
        val inBed = ((up - bed + 1440) % 1440) / 60.0
        val eff = if (inBed <= 0) 100.0 else hours / inBed * 100
        val c4 = if (eff >= 85) 0 else if (eff >= 75) 1 else if (eff >= 65) 2 else 3
        val c5 = bucket((5..13).sumOf { a[it] }, 0, 9, 18)
        val c6 = a[15]
        val c7 = bucket(a[16] + a[17], 0, 2, 4)
        return intArrayOf(c1, c2, c3, c4, c5, c6, c7)
    }
    val PSQI_COMPONENTS = listOf("Subjective quality", "Sleep latency", "Sleep duration", "Sleep efficiency", "Disturbances", "Sleep medication", "Daytime dysfunction")

    val PSQI = Questionnaire(
        "psqi", "Sleep Quality Index (PSQI)", "Sleep disturbance baseline · 18 questions",
        "These questions are about your usual sleep habits during the past month only. Answer for the majority of days and nights.",
        listOf(
            Question("During the past month, what time have you usually gone to bed?", kind = 1, default = 23 * 60),
            Question("How long (in minutes) has it usually taken you to fall asleep each night?", kind = 2, default = 20),
            Question("What time have you usually got up in the morning?", kind = 1, default = 7 * 60),
            Question("How many hours of actual sleep did you get at night? (This may differ from the hours spent in bed.)", kind = 3, default = 70),
            Question("How often have you had trouble sleeping because you could not get to sleep within 30 minutes?", FREQ),
            Question("…because you woke up in the middle of the night or early morning?", FREQ),
            Question("…because you had to get up to use the bathroom?", FREQ),
            Question("…because you could not breathe comfortably?", FREQ),
            Question("…because you coughed or snored loudly?", FREQ),
            Question("…because you felt too cold?", FREQ),
            Question("…because you felt too hot?", FREQ),
            Question("…because you had bad dreams?", FREQ),
            Question("…because you had pain?", FREQ),
            Question("…for any other reason?", FREQ),
            Question("During the past month, how would you rate your sleep quality overall?", listOf("Very good", "Fairly good", "Fairly bad", "Very bad")),
            Question("How often have you taken medicine (prescribed or over the counter) to help you sleep?", FREQ),
            Question("How often have you had trouble staying awake while driving, eating meals, or engaging in social activity?", FREQ),
            Question("How much of a problem has it been for you to keep up enough enthusiasm to get things done?",
                listOf("No problem at all", "Only a very slight problem", "Somewhat of a problem", "A very big problem"))
        ), 21,
        { psqiComponents(it).sum() },
        { if (it <= 5) "Good sleep quality" else if (it <= 10) "Moderate sleep disturbance" else "Severe sleep disturbance" }
    )

    val PHQ9 = Questionnaire(
        "phq9", "Mood Check (PHQ-9)", "Depression screen · 9 questions",
        "Over the last 2 weeks, how often have you been bothered by any of the following problems?",
        listOf(
            "Little interest or pleasure in doing things",
            "Feeling down, depressed, or hopeless",
            "Trouble falling or staying asleep, or sleeping too much",
            "Feeling tired or having little energy",
            "Poor appetite or overeating",
            "Feeling bad about yourself — or that you are a failure or have let yourself or your family down",
            "Trouble concentrating on things, such as reading the newspaper or watching television",
            "Moving or speaking so slowly that other people could have noticed? Or the opposite — being so fidgety or restless that you have been moving around a lot more than usual",
            "Thoughts that you would be better off dead or of hurting yourself in some way"
        ).map { Question(it, TWO_WEEKS) }, 27,
        { it.sum() },
        { if (it <= 4) "Minimal symptoms" else if (it <= 9) "Mild symptoms" else if (it <= 14) "Moderate symptoms" else if (it <= 19) "Moderately severe symptoms" else "Severe symptoms" }
    )

    val GAD7 = Questionnaire(
        "gad7", "Anxiety Check (GAD-7)", "Anxiety screen · 7 questions",
        "Over the last 2 weeks, how often have you been bothered by the following problems?",
        listOf(
            "Feeling nervous, anxious, or on edge",
            "Not being able to stop or control worrying",
            "Worrying too much about different things",
            "Trouble relaxing",
            "Being so restless that it is hard to sit still",
            "Becoming easily annoyed or irritable",
            "Feeling afraid as if something awful might happen"
        ).map { Question(it, TWO_WEEKS) }, 21,
        { it.sum() },
        { if (it <= 4) "Minimal anxiety" else if (it <= 9) "Mild anxiety" else if (it <= 14) "Moderate anxiety" else "Severe anxiety" }
    )

    val ALL = listOf(PSQI, PHQ9, GAD7)
    fun byId(id: String) = ALL.first { it.id == id }
}
