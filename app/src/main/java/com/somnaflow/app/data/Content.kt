package com.somnaflow.app.data

import com.somnaflow.app.audio.Beat
import com.somnaflow.app.audio.Fader
import com.somnaflow.app.audio.MixConfig

enum class Kind(val label: String) {
    ENTRAIN("Brainwave"), SOUNDSCAPE("Soundscapes"), STORY("Sleep Stories"), SLEEP_MED("Sleep Meditations"), DAY_MED("Daytime Mindfulness")
}

/**
 * A playable item. Narrated items carry a [script]: one entry per paragraph; "[n]" inside the text
 * means "pause n seconds". Everything is generated on the phone, so the whole library works offline.
 */
class Track(
    val id: String, val title: String, val subtitle: String, val kind: Kind, val mix: MixConfig,
    val minutes: Int = 0, val script: List<String> = emptyList(), val hue: Float = 270f
)

object Content {
    private fun f(master: Float = 0.8f, nature: Float = 0.75f, melody: Float = 0.6f, white: Float = 0f, pink: Float = 0f, brown: Float = 0f,
                  bass: Float = 0.5f, mid: Float = 0.5f, treble: Float = 0.5f) =
        listOf(master, 0.5f, nature, melody, white, pink, brown, bass, mid, treble)

    private const val BREATH = "Let your breath drift in, slowly. [4] And let it drift out, slower still. [6] Again, in. [4] And out. [7]"

    private fun story(vararg paragraphs: String): List<String> =
        listOf("Settle into your bed, and let your eyes close. There is nothing you need to do now except listen. [3] $BREATH") +
            paragraphs.flatMapIndexed { i, p -> if (i % 2 == 1) listOf(p, BREATH) else listOf(p) } +
            listOf("And now there is nothing more to follow. [4] Only the sounds around you. [4] Only your slow, easy breath. [6] Sleep well.")

    val TRACKS: List<Track> = listOf(
        // ---- Module 1.1: brainwave entrainment ----
        Track("theta", "Theta Sleep Induction", "6 Hz binaural beat · 30:60 dB under rotating nature sounds", Kind.ENTRAIN,
            MixConfig(f(), beat = Beat.THETA, csLock = true, randomize = true), hue = 275f),
        Track("spindle", "Spindle Accelerator", "0.5–4 Hz delta sweep over steady pink noise", Kind.ENTRAIN,
            MixConfig(f(pink = 0.7f, treble = 0.4f), beat = Beat.SWEEP, csLock = true), hue = 250f),
        Track("theta_rain", "Theta Rain", "6 Hz theta beat beneath gentle rain", Kind.ENTRAIN,
            MixConfig(f(), nature = setOf("rain"), beat = Beat.THETA, csLock = true), hue = 262f),
        // ---- Module 1.2: layered soundscapes ----
        Track("rain_piano", "Rainy Night Piano", "Soft piano with rain on the window", Kind.SOUNDSCAPE,
            MixConfig(f(nature = 0.6f, melody = 0.65f), nature = setOf("rain"), melody = "piano"), hue = 230f),
        Track("campfire", "Campfire Under Stars", "Crackling fire, night wind and ambient pads", Kind.SOUNDSCAPE,
            MixConfig(f(nature = 0.7f, melody = 0.45f), nature = setOf("campfire", "wind"), melody = "pad"), hue = 20f),
        Track("ocean_pad", "Ocean Drift", "Slow waves with warm ambient pads", Kind.SOUNDSCAPE,
            MixConfig(f(melody = 0.5f), nature = setOf("ocean"), melody = "pad"), hue = 200f),
        Track("forest_chimes", "Forest Chimes", "Birdsong, leaves and distant wind chimes", Kind.SOUNDSCAPE,
            MixConfig(f(), nature = setOf("forest", "chimes")), hue = 150f),
        Track("river", "Mountain River", "A steady stream over stones", Kind.SOUNDSCAPE, MixConfig(f(), nature = setOf("river")), hue = 185f),
        Track("waterfall", "Hidden Waterfall", "Deep, constant falling water", Kind.SOUNDSCAPE, MixConfig(f(), nature = setOf("waterfall")), hue = 210f),
        Track("brown", "Deep Brown Noise", "Low, warm masking noise", Kind.SOUNDSCAPE, MixConfig(f(brown = 0.8f, bass = 0.6f)), hue = 30f),
        Track("pink", "Pink Noise", "Balanced noise for steady sleep", Kind.SOUNDSCAPE, MixConfig(f(pink = 0.75f)), hue = 320f),
        Track("white", "White Noise", "Bright, even masking noise", Kind.SOUNDSCAPE, MixConfig(f(white = 0.7f, treble = 0.4f)), hue = 0f),

        // ---- Module 2.1: sleep stories ----
        Track("st_lighthouse", "The Lighthouse Keeper's Evening", "A slow walk up a spiral stair above a quiet sea", Kind.STORY,
            MixConfig(f(nature = 0.5f, melody = 0.3f), nature = setOf("ocean"), melody = "pad"), 14, story(
                "At the far end of a long, pale beach stands an old white lighthouse. The day is ending. The sand still holds a little of the sun's warmth, and you can feel it, soft and even, beneath your feet as you walk toward the door.",
                "Inside, the air is cool and smells faintly of salt and old wood. A spiral stair curls upward. You begin to climb, unhurried, one step, and then another. Your hand slides along a rail worn smooth by a hundred years of evenings just like this one.",
                "With every turn of the stair, the sound of the waves grows softer and rounder. Through each small window you see the sea, a little darker each time, a little calmer. There is no need to count the steps. They simply pass beneath you.",
                "At the top is a small round room with a deep chair and a woollen blanket. You sit. The chair holds your whole weight, your shoulders, your back, your heavy legs. The blanket settles over you, warm and slow.",
                "Above you the great lamp turns. Its light sweeps out across the water, and returns. Out, and returns. You notice your breath has found the same rhythm. Out across the water. And home again.",
                "Far below, the tide draws back over the sand with a long, quiet hush. Each wave carries a little more of the day away with it. The thoughts you carried up the stairs grow light, and drift, like small boats heading out past the light.",
                "The keeper has nothing left to do tonight. The lamp turns by itself. The sea keeps its own time. And you can simply rest here, watching the slow light pass, and pass again, until your eyes grow too heavy to follow it."
            ), hue = 215f),
        Track("st_train", "Night Train Through the Mountains", "A warm sleeper cabin and the rhythm of the rails", Kind.STORY,
            MixConfig(f(nature = 0.45f, brown = 0.35f), nature = setOf("wind"), melody = "pad"), 14, story(
                "You step aboard a night train in a small mountain station. The platform is quiet, the lamps are amber, and the air smells of pine and cold stone. A conductor nods, and shows you to your cabin.",
                "The cabin is small and warm. There is a narrow bed with thick, clean sheets, a little lamp with a golden shade, and a wide window. You lie down, and the mattress receives you, steady and soft.",
                "With a gentle pull, the train begins to move. The wheels find their rhythm on the rails. A slow, even, rocking rhythm, that you feel more than you hear. It moves through the bed, and through you.",
                "Outside the window, dark forests slide past. Now and then a small village goes by, a few sleeping houses, one lit window, and then trees again. You do not need to know their names. They simply appear, and are gone.",
                "The train climbs higher. Snow lies blue under the moon. The air in the cabin stays warm, and your blanket is heavy on your chest in a way that feels safe. Your hands grow loose. Your jaw unclenches. Your forehead smooths.",
                "Somewhere ahead, the engine hums, steady and certain. Someone else is driving tonight. Someone else is watching the track. All that is asked of you is to be carried.",
                "The rhythm goes on, mile after mile. Each sway of the carriage rocks you a little deeper into the pillow. The window becomes a soft blur of dark and silver, and the long night carries you gently on."
            ), hue = 245f),
        Track("st_garden", "The Garden After Rain", "Wet leaves, warm earth and a slow evening", Kind.STORY,
            MixConfig(f(nature = 0.55f, melody = 0.3f), nature = setOf("rain", "forest"), melody = "piano"), 13, story(
                "It has been raining all afternoon, and now the rain is thinning to a soft, steady patter. You open a wooden gate and step into a walled garden. The air is cool and green, and everything is shining.",
                "A gravel path leads between beds of lavender and sage. As you walk, your sleeve brushes the leaves, and the scent rises around you, clean and sweet. You breathe it in slowly, all the way down.",
                "Drops are falling from the leaves of an old apple tree, one, and then another, into a stone basin. Each drop makes a small round sound, and a ring that spreads, and fades. You watch the rings widen until the water is still again.",
                "Under the tree is a wide bench, dry beneath the branches, with a folded quilt. You sit, and then you lie back. The wood is smooth and still warm. The quilt is heavy across your legs.",
                "Above you the leaves move a little in the last of the rain. Through them, the sky is turning from grey to a deep, soft violet. A blackbird sings a few slow notes, and stops, and the garden is quiet again.",
                "You feel the weight of your heels, your calves, your hips, resting on the bench. Your shoulders widen. Your arms grow long and heavy. The earth beneath the garden is holding everything, and it is holding you too.",
                "The rain slows, drop by drop. The light fades, shade by shade. There is nowhere to be, and nothing to tend. The garden will look after itself tonight, while you rest beneath the tree."
            ), hue = 140f),
        Track("st_cabin", "The Cabin by the Frozen Lake", "Firelight, wool blankets and falling snow", Kind.STORY,
            MixConfig(f(nature = 0.55f), nature = setOf("campfire", "wind")), 13, story(
                "Snow is falling in large, slow flakes as you reach a small cabin at the edge of a frozen lake. Light glows in the window. You stamp the snow from your boots and step inside, and the door closes softly behind you.",
                "A fire is already burning in the stone hearth. The room smells of cedar and woodsmoke. You hang up your coat, and feel the warmth begin at your face, and your hands, and slowly spread.",
                "Near the fire is a low bed piled with wool blankets. You lie down and pull them up to your chin. They are heavier than you expected, and the weight settles you, like a hand resting on your back.",
                "The fire murmurs and crackles. Now and then a log shifts and sends up a few bright sparks that rise, and fade. Shadows move slowly on the wooden ceiling, and you watch them without needing to make them into anything.",
                "Outside, the snow keeps falling. It covers the path you walked in on. It covers the lake, the trees, the roof above you. Every sound in the world is being wrapped in something soft.",
                "Your feet are warm now. Your legs are warm. The warmth has reached your chest, and your breath is slow and low in your belly. With each breath out, you sink a little further into the bed.",
                "The fire will burn low and steady for hours. The snow will keep its quiet watch. There is nothing left to carry, and nothing to keep track of. Just warmth, and stillness, and the long, kind dark."
            ), hue = 18f),
        Track("st_river", "Drifting Down the Slow River", "A flat-bottomed boat, cushions and evening light", Kind.STORY,
            MixConfig(f(nature = 0.55f, melody = 0.3f), nature = setOf("river", "forest"), melody = "pad"), 13, story(
                "At the edge of a wide, slow river, a flat wooden boat is waiting for you. It is lined with cushions and a soft blanket. You step in, lie back, and with the smallest push, the bank lets you go.",
                "The current is gentle and takes its time. You do not need to steer. The river has made this journey every evening for a thousand years, and it knows the way.",
                "Willow branches trail in the water as you pass. Their leaves brush the surface with a faint whisper. The light through them is green and gold, and it moves across your closed eyelids, slow and warm.",
                "You let one hand rest over the side. The water is cool and silky, and it slips between your fingers without asking anything of you. You feel the boat rock, very slightly, with each breath.",
                "A heron stands at the bank, perfectly still, and watches you drift by. Dragonflies hover, and are gone. The river bends, and bends again, and each bend is quieter than the last.",
                "The sky is deepening now. The first stars come out, one at a time, with no hurry at all. The water turns dark and smooth as glass, and the boat seems to float between two skies.",
                "Thoughts may come. You can set each one on the water, like a leaf, and watch it float away behind you. The river carries it all. The river carries you. Onward, slower, and softer, into the night."
            ), hue = 190f),

        // ---- Module 2.1: sleep-specific meditations (lying down, eyes closed) ----
        Track("sm_bodyscan", "Body Scan for Sleep", "Release each part of the body, lying down", Kind.SLEEP_MED,
            MixConfig(f(nature = 0.45f, melody = 0.35f), nature = setOf("rain"), melody = "pad"), 10, listOf(
                "Lie on your back or your side, whichever is most comfortable. There is no correct posture here. Let the bed take your weight. [4] $BREATH",
                "Bring your attention to your feet. Notice any warmth, or tingling, or heaviness. [4] As you breathe out, let your feet soften and fall outward. [6]",
                "Move up to your calves and knees. [3] Nothing to fix. Just notice, and let them grow heavy. [6] Your thighs and hips, sinking into the mattress. [7]",
                "Feel your belly rise as you breathe in. [4] And fall as you breathe out. [6] Let the breath be slow and low, without effort. [6]",
                "Your lower back softens. [4] Your upper back widens. [4] Your shoulders drop away from your ears. [7]",
                "Your arms are heavy. [3] Your elbows, your wrists, your hands. Let each finger uncurl. [7]",
                "Soften your neck and your jaw. Let your teeth part slightly. [5] Smooth the space between your eyebrows. [5] Let your eyes rest, still and heavy, behind their lids. [7]",
                "Now feel the whole body at once, heavy, warm, and breathing by itself. [8] If a thought arrives, let it pass, and come back to the weight of your body in the bed. [8] There is nothing more to do. [6] Let sleep come when it is ready."
            ), hue = 285f),
        Track("sm_478", "4-7-8 Slow Breathing", "A lengthened exhale to calm the nervous system", Kind.SLEEP_MED,
            MixConfig(f(nature = 0.45f), nature = setOf("ocean")), 6,
            listOf("Rest in any comfortable position. We will breathe in for four, hold softly for seven, and breathe out slowly for eight. If that feels like too much, simply make your out-breath longer than your in-breath. [3]") +
                List(6) { "Breathe in through your nose. [4] Hold, gently. [7] And breathe out slowly through your mouth. [8]" } +
                listOf("Now let your breathing return to its own rhythm. [5] Notice how much slower and quieter it has become. [8] Rest here."), hue = 300f),
        Track("sm_pmr", "Progressive Muscle Release", "Tense, then release, from toes to forehead", Kind.SLEEP_MED,
            MixConfig(f(nature = 0.45f, brown = 0.3f), nature = setOf("waterfall")), 8, listOf(
                "Lying comfortably, we will gently tense each muscle group for a few seconds, and then let it go completely. Keep every squeeze mild and painless. [3]",
                "Curl your toes and tighten your feet. [5] And release. Feel the difference. [8]",
                "Tighten your calves and thighs. [5] And let them go, heavy against the bed. [8]",
                "Gently tighten your belly and your lower back. [5] And soften. [8]",
                "Make loose fists, and tighten your arms. [5] Release, and let your hands fall open. [8]",
                "Lift your shoulders toward your ears. [5] And let them drop. [8]",
                "Scrunch your face, softly. [4] And let it smooth out completely, forehead, eyes, cheeks, jaw. [9]",
                "Your whole body is loose and warm now. [6] With every breath out, sink a little deeper. [8] Rest."
            ), hue = 265f),

        // ---- Daytime-only general mindfulness (upright, alert) ----
        Track("dm_breath", "Mindful Breathing", "Seated focus practice · daytime only", Kind.DAY_MED,
            MixConfig(f(nature = 0.4f), nature = setOf("forest")), 6, listOf(
                "Sit upright, with your back tall and your feet on the floor. Keep your eyes open with a soft gaze, or closed if you prefer. This is a practice for alertness and clarity. [3]",
                "Bring your full attention to the breath at the tip of your nose. Feel the cool air coming in. [4] And the warmer air going out. [5]",
                "Silently count each out-breath, from one up to ten. [20] If you lose count, simply and kindly begin again at one. [25]",
                "Notice where the mind has wandered. Name it, planning, remembering, worrying, and return to the breath. [25]",
                "Sit tall. Feel alert, and steady. [15] Keep following the breath, one at a time. [30]",
                "To finish, take one deeper breath. Notice the room around you, and carry this steadiness into the rest of your day."
            ), hue = 45f),
        Track("dm_noting", "Open Awareness", "Noting sounds, sensations and thoughts · daytime only", Kind.DAY_MED,
            MixConfig(f(nature = 0.4f), nature = setOf("river")), 6, listOf(
                "Sit in an upright, dignified posture. This practice sharpens attention, so it is best done during the day. [3]",
                "Open your attention to sounds. Near sounds, far sounds. Let each one come and go without naming it good or bad. [25]",
                "Now notice sensations in the body. Contact with the chair, the air on your skin, the movement of breathing. [25]",
                "Now watch thoughts as they arise. Label each one lightly, thinking, and let it pass like a cloud. [30]",
                "Rest as the one who is noticing. Wide awake, and at ease. [30]",
                "Take a full breath, stretch if you like, and return to your day."
            ), hue = 60f),
        Track("dm_reset", "Three-Minute Reset", "A short midday pause · daytime only", Kind.DAY_MED,
            MixConfig(f(nature = 0.4f), nature = setOf("wind", "chimes")), 3, listOf(
                "Pause whatever you are doing, and sit or stand tall. [3] First, notice. What thoughts are here? What feelings? What sensations? [15]",
                "Second, gather. Bring all of your attention to the breath in your belly. In. [4] And out. [5] Stay with it for a few more breaths. [25]",
                "Third, widen. Feel your whole body breathing, your posture, your face. [15] Carry this wider awareness into the next thing you do."
            ), hue = 35f)
    )

    fun byId(id: String): Track? = TRACKS.firstOrNull { it.id == id }

    /** General (upright, alerting) meditation is only offered in daytime: from wake time until 3 hours before bed. */
    fun daytimeAllowed(nowMin: Int, wakeMin: Int, bedMin: Int): Boolean {
        val sinceWake = (nowMin - wakeMin + 1440) % 1440
        val day = (bedMin - 180 - wakeMin + 2880) % 1440
        return sinceWake <= day
    }

    /** Stepped-care ordering: the more disturbed the sleep profile, the more sleep stories and sleep meditations lead. */
    fun recommended(severity: Int): List<Track> {
        val ids = when (severity) {
            3 -> listOf("st_lighthouse", "sm_bodyscan", "st_cabin", "sm_478", "theta")
            2 -> listOf("st_train", "theta", "sm_pmr", "st_garden", "spindle")
            else -> listOf("theta", "rain_piano", "st_river", "spindle", "ocean_pad")
        }
        return ids.mapNotNull { byId(it) }
    }

    val EMOTIONS = mapOf(
        -3 to listOf("Overwhelmed", "Hopeless", "Angry", "Panicked", "Drained"),
        -2 to listOf("Anxious", "Sad", "Stressed", "Frustrated", "Lonely"),
        -1 to listOf("Worried", "Irritated", "Restless", "Tired", "Disappointed"),
        0 to listOf("Indifferent", "Calm", "Content", "Drained", "Neutral"),
        1 to listOf("Peaceful", "Relieved", "Hopeful", "Satisfied", "Calm"),
        2 to listOf("Happy", "Grateful", "Confident", "Proud", "Relaxed"),
        3 to listOf("Joyful", "Amazed", "Excited", "Loved", "Brave")
    )
    val VALENCE = listOf("Very Unpleasant", "Unpleasant", "Slightly Unpleasant", "Neutral", "Slightly Pleasant", "Pleasant", "Very Pleasant")
    val IMPACTS = listOf("Work", "Family", "Health", "Partner", "Friends", "Money", "Fitness", "Community", "Current events", "Weather", "Tasks", "Identity")

    /** id, title, group */
    val TASKS = listOf(
        Triple("daylight", "Get 80+ minutes of outdoor daylight", "Circadian"),
        Triple("exercise", "Move for 20+ minutes (not within 2 h of bed)", "Exercise"),
        Triple("caffeine", "No caffeine after 2 PM", "Hygiene"),
        Triple("meal", "Finish heavy meals and alcohol 3 h before bed", "Hygiene"),
        Triple("room", "Bedroom cool, dark and quiet", "Environment"),
        Triple("screens", "Screens away once wind-down starts", "Environment"),
        Triple("checkin", "Complete tonight's state-of-mind check-in", "Relaxation"),
        Triple("stretch", "Do the guided wind-down stretches", "Relaxation")
    )

    val STRETCHES = listOf(
        Triple("Neck release", 40, "Sit or stand tall. Let your right ear fall toward your right shoulder. Breathe slowly. Halfway through, change sides."),
        Triple("Shoulder rolls", 30, "Roll both shoulders slowly backward in big circles, then let them drop heavy."),
        Triple("Seated forward fold", 45, "Sit on the edge of the bed, hinge at the hips and let your arms and head hang toward the floor."),
        Triple("Knees to chest", 45, "Lie on your back and hug both knees gently in. Rock a little from side to side."),
        Triple("Supine twist", 50, "Still on your back, let both knees fall to the left and look right. Halfway through, change sides."),
        Triple("Legs up the wall", 60, "Rest your legs up against the wall or headboard. Arms loose, palms up, breathing slow.")
    )
}
