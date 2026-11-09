package com.koora.app.data

/*
 * SAFETY NET for the News tab.
 *
 * The registry in NewsRepository only lists FOOTBALL-SECTION feeds
 * (BBC/Sky/Guardian football, L'Équipe/RMC football, CNN Arabic sport,
 * France24 Arabic sport), so items are football by construction.
 * This filter catches the occasional off-topic headline a section feed
 * still lets through (a tennis story on a sport page, a political story
 * that slipped into a general feed). It is deliberately CONSERVATIVE:
 * better to let one stray item through than to silently drop real
 * football news — so only unambiguous markers are listed here.
 *
 * Pure Kotlin (no Android imports) so it is unit-testable on the JVM.
 */
object NewsFilter {

    private val offTopic = listOf(
        // --- politics / general news: English ---
        "election", "parliament", "senate", "government", "presidential",
        "diplomacy", "referendum", "immigration", "asylum",
        // --- politics / general news: French ---
        "élection", "parlement", "sénat", "gouvernement", "présidentielle",
        "diplomatie", "référendum",
        // --- politics / general news: Arabic ---
        "انتخابات", "برلمان", "الحكومة", "رئاسي", "الاحتلال", "قصف",
        "وزارة الداخلية", "وزارة الخارجية",
        // --- other sports that general-sport sections mix in: English ---
        "cricket", "golf", "tennis", "rugby", "formula 1", "basketball",
        "baseball", "hockey", "boxing", "ufc", "snooker", "darts",
        "cycling", "athletics", "nascar", "volleyball", "handball", "swimming",
        // --- other sports: French ---
        "formule 1", "basket", "boxe", "cyclisme", "natation", "judo",
        // --- other sports: Arabic ---
        "الغولف", "الكريكيت", "السلة", "الطائرة", "كرة اليد", "الهوكي",
        "الملاكمة", "الفورمولا", "الرغبي", "السباحة", "ألعاب القوى",
    )

    /** True when a headline looks like football news and should be shown. */
    fun isFootballNews(title: String): Boolean {
        val t = title.lowercase()
        return offTopic.none { t.contains(it) }
    }
}
