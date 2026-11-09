package com.koora.app

import com.koora.app.data.NewsFilter
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/*
 * Locks the v1.4.2 news fix: users complained the News tab showed
 * political stories and other sports. The feed registry is now
 * football-sections-only, and NewsFilter is the safety net — this test
 * pins its behavior on REAL headlines from the live feeds.
 */
class NewsFilterTest {

    @Test
    fun `football headlines pass (EN, FR, AR)`() {
        // Real headlines observed in the verified football feeds
        assertTrue(
            NewsFilter.isFootballNews(
                "Barcelona move clear at the top after Fermín López's double stuns Atlético Madrid"
            )
        )
        assertTrue(
            NewsFilter.isFootballNews("Premier League: 10 talking points from the weekend's action")
        )
        assertTrue(
            NewsFilter.isFootballNews("Kane misses penalty as Silva snatches Dortmund draw in Der Klassiker")
        )
        assertTrue(
            NewsFilter.isFootballNews("DIRECT. Equipe de France: toutes les infos sur les Bleus avant le match de Ligue 1")
        )
        assertTrue(
            NewsFilter.isFootballNews("Simeone fier de la performance exceptionnelle de ses joueurs malgré la défaite")
        )
        assertTrue(
            NewsFilter.isFootballNews("برشلونة يقلب الطاولة على أتلتيكو مدريد بثنائية لوبيس ويعزز صدارته للدوري الإسباني")
        )
        assertTrue(
            NewsFilter.isFootballNews("كأس السوبر الأفريقية: اتحاد العاصمة الجزائري يفوز على صن داونز الجنوب أفريقي")
        )
        assertTrue(
            NewsFilter.isFootballNews("ريال مدريد يواصل سلسلة انتصاراته بالدوري الإسباني")
        )
    }

    @Test
    fun `political headlines are dropped`() {
        assertFalse(NewsFilter.isFootballNews("US election: voters head to the polls in pivotal Senate race"))
        assertFalse(NewsFilter.isFootballNews("Parliament debates the new budget proposal"))
        assertFalse(NewsFilter.isFootballNews("Les élections législatives : le gouvernement annonce son plan"))
        assertFalse(NewsFilter.isFootballNews("برلمان المغرب يصادق على مشروع قانون جديد"))
        assertFalse(NewsFilter.isFootballNews("الانتخابات الرئاسية الأمريكية: النتائج الكاملة"))
    }

    @Test
    fun `other-sports headlines are dropped`() {
        assertFalse(NewsFilter.isFootballNews("Los Angeles Golf Club make history by winning inaugural WTGL match"))
        assertFalse(NewsFilter.isFootballNews("Tennis: Sabalenka reaches the final in Riyadh"))
        assertFalse(NewsFilter.isFootballNews("Le tennis revient à Roland-Garros avec un tableau renouvelé"))
        assertFalse(NewsFilter.isFootballNews("فوز مفاجئ في نهائي كرة السلة الأمريكية"))
    }

    @Test
    fun `club-president headlines are NOT over-filtered`() {
        // "president" alone must NOT trigger the filter — club presidents
        // are everyday football news. Only presidential-election markers do.
        assertTrue(NewsFilter.isFootballNews("Le président du PSG s'exprime sur le mercato"))
        assertTrue(NewsFilter.isFootballNews("رئيس النادي يعلن تمديد عقد المدرب"))
    }
}
