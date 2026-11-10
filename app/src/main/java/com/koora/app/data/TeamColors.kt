package com.koora.app.data

/*
 * Team badge colors (v1.5.3 "Night Stadium").
 *
 * LEGAL NOTE: we do NOT ship club crests or any trademarked artwork.
 * Badges are generated locally: a colored circle + the team's TLA code
 * (or derived initials). Colors below are widely-known approximate kit
 * colors for recognition only.
 *
 * Matching is a lowercase "contains" on the club key so football-data.org
 * long names ("Manchester United FC", "Stade Rennais FC 1901") match.
 * Unknown teams get a deterministic color from a fixed palette (same
 * team -> same colors, every launch).
 */
object TeamColors {

    data class BadgeColors(val bg: Long, val fg: Long)

    private val map: Map<String, BadgeColors> = buildMap {
        // Premier League
        put("manchester united", BadgeColors(0xFFDA291C, 0xFFFFFFFF))
        put("manchester city", BadgeColors(0xFF6CABDD, 0xFF0A2D3C))
        put("liverpool", BadgeColors(0xFFC8102E, 0xFFFFFFFF))
        put("arsenal", BadgeColors(0xFFEF0107, 0xFFFFFFFF))
        put("chelsea", BadgeColors(0xFF034694, 0xFFFFFFFF))
        put("tottenham", BadgeColors(0xFF132257, 0xFFFFFFFF))
        put("newcastle", BadgeColors(0xFF241F20, 0xFFFFFFFF))
        put("west ham", BadgeColors(0xFF7A263A, 0xFF9FD3ED))
        put("aston villa", BadgeColors(0xFF670E36, 0xFF95BFE5))
        put("everton", BadgeColors(0xFF003399, 0xFFFFFFFF))
        put("brighton", BadgeColors(0xFF0057B8, 0xFFFFFFFF))
        put("crystal palace", BadgeColors(0xFF1B458F, 0xFFC4122E))
        put("fulham", BadgeColors(0xFF101010, 0xFFFFFFFF))
        put("brentford", BadgeColors(0xFFE30613, 0xFFFFFFFF))
        put("nottingham", BadgeColors(0xFFDD0000, 0xFFFFFFFF))
        put("wolverhampton", BadgeColors(0xFFFDB913, 0xFF231F20))
        put("bournemouth", BadgeColors(0xFFB50E12, 0xFFFFFFFF))
        put("luton", BadgeColors(0xFFF78F1E, 0xFF0A2D5C))
        put("burnley", BadgeColors(0xFF6C1D45, 0xFF99D6EA))
        put("sheffield united", BadgeColors(0xFFEE2737, 0xFFFFFFFF))
        // La Liga
        put("real madrid", BadgeColors(0xFFF4F6F8, 0xFF1D4ED8))
        put("barcelona", BadgeColors(0xFFA50044, 0xFFEDBB00))
        put("atlético", BadgeColors(0xFFCB3524, 0xFFFFFFFF))
        put("atletico", BadgeColors(0xFFCB3524, 0xFFFFFFFF))
        put("sevilla", BadgeColors(0xFFD80027, 0xFFFFFFFF))
        put("villarreal", BadgeColors(0xFFFFE667, 0xFF005187))
        put("real sociedad", BadgeColors(0xFF0067B1, 0xFFFFFFFF))
        put("athletic", BadgeColors(0xFFEE2523, 0xFFFFFFFF))
        put("valencia", BadgeColors(0xFFF1A71E, 0xFF14264B))
        put("girona", BadgeColors(0xFFCD2534, 0xFFFFFFFF))
        put("betis", BadgeColors(0xFF00954C, 0xFFFFFFFF))
        put("osasuna", BadgeColors(0xFF0A346F, 0xFFD91A21))
        put("celta", BadgeColors(0xFF1D4E89, 0xFF8AC3EE))
        put("mallorca", BadgeColors(0xFFE20613, 0xFF000000))
        // Serie A
        put("inter", BadgeColors(0xFF0068A8, 0xFFFFFFFF))
        put("milan", BadgeColors(0xFFFB090B, 0xFFFFFFFF))
        put("juventus", BadgeColors(0xFF1A1A1A, 0xFFFFFFFF))
        put("napoli", BadgeColors(0xFF12A0D7, 0xFFFFFFFF))
        put("roma", BadgeColors(0xFF8E1F2F, 0xFFF0BC42))
        put("lazio", BadgeColors(0xFF87D8F7, 0xFF0B1F3F))
        put("atalanta", BadgeColors(0xFF1E71B8, 0xFF1D1D1B))
        put("fiorentina", BadgeColors(0xFF582C83, 0xFFFFFFFF))
        put("bologna", BadgeColors(0xFFA81438, 0xFF1A2F48))
        put("torino", BadgeColors(0xFF881010, 0xFFFFFFFF))
        // Bundesliga
        put("bayern", BadgeColors(0xFFDC052D, 0xFFFFFFFF))
        put("dortmund", BadgeColors(0xFFFDE100, 0xFF1D1D1B))
        put("leverkusen", BadgeColors(0xFFE32221, 0xFF1D1D1B))
        put("leipzig", BadgeColors(0xFFDD0741, 0xFFFFFFFF))
        put("frankfurt", BadgeColors(0xFFE1000F, 0xFF1D1D1B))
        put("stuttgart", BadgeColors(0xFFE32219, 0xFFFFFFFF))
        put("wolfsburg", BadgeColors(0xFF65B32E, 0xFFFFFFFF))
        put("gladbach", BadgeColors(0xFF1D1D1B, 0xFFFFFFFF))
        put("mainz", BadgeColors(0xFFC3141E, 0xFFFFFFFF))
        put("freiburg", BadgeColors(0xFFE2001A, 0xFFFFFFFF))
        put("union berlin", BadgeColors(0xFFEB1923, 0xFFF5C400))
        put("hoffenheim", BadgeColors(0xFF1C63B7, 0xFFFFFFFF))
        put("werder", BadgeColors(0xFF1D9053, 0xFFFFFFFF))
        put("augsburg", BadgeColors(0xFFBA3733, 0xFFFFFFFF))
        // Ligue 1 (football-data long names: "Stade Rennais FC 1901", "LOSC Lille"...)
        put("paris saint-germain", BadgeColors(0xFF004170, 0xFFDA291C))
        put("psg", BadgeColors(0xFF004170, 0xFFDA291C))
        put("marseille", BadgeColors(0xFF2FAEE0, 0xFFFFFFFF))
        put("lyonnais", BadgeColors(0xFF1B1F63, 0xFFDA291C))
        put("monaco", BadgeColors(0xFFE63312, 0xFFFFFFFF))
        put("lille", BadgeColors(0xFFE01E13, 0xFFFFFFFF))
        put("rennais", BadgeColors(0xFFE23326, 0xFF0A2240))
        put("nice", BadgeColors(0xFFC8102E, 0xFF1D1D1B))
        put("lens", BadgeColors(0xFFE30613, 0xFFFFF200))
        put("brest", BadgeColors(0xFFE30613, 0xFFFFFFFF))
        put("toulouse", BadgeColors(0xFF4B2E83, 0xFFB795FF))
        put("nantes", BadgeColors(0xFFFCD502, 0xFF0F6B39))
        put("strasbourg", BadgeColors(0xFF0D5EAF, 0xFFFFFFFF))
        put("reims", BadgeColors(0xFFE30613, 0xFFFFFFFF))
        put("montpellier", BadgeColors(0xFFF37021, 0xFF0B2C5F))
        put("le havre", BadgeColors(0xFF0071BC, 0xFF0B2C5F))
        put("metz", BadgeColors(0xFF7E1F2D, 0xFFFFFFFF))
        // Eredivisie / Primeira
        put("ajax", BadgeColors(0xFFD2122E, 0xFFFFFFFF))
        put("psv", BadgeColors(0xFFED1C24, 0xFFFFFFFF))
        put("feyenoord", BadgeColors(0xFFE30613, 0xFF1D1D1B))
        put("alkmaar", BadgeColors(0xFFE30613, 0xFFFFFFFF))
        put("benfica", BadgeColors(0xFFE30613, 0xFFFFFFFF))
        put("porto", BadgeColors(0xFF00428C, 0xFFFFFFFF))
        put("sporting", BadgeColors(0xFF008057, 0xFFFFFFFF))
        // Rest of Europe / Türkiye
        put("celtic", BadgeColors(0xFF018749, 0xFFFFFFFF))
        put("rangers", BadgeColors(0xFF1B458F, 0xFFFFFFFF))
        put("galatasaray", BadgeColors(0xFFA90432, 0xFFFDB912))
        put("fenerbahçe", BadgeColors(0xFFFFED00, 0xFF00295B))
        put("beşiktaş", BadgeColors(0xFF1D1D1B, 0xFFFFFFFF))
        put("olympiacos", BadgeColors(0xFFE30613, 0xFFFFFFFF))
        // South America (Brasileirão / Libertadores)
        put("flamengo", BadgeColors(0xFFC1272D, 0xFF1D1D1B))
        put("palmeiras", BadgeColors(0xFF006437, 0xFFFFFFFF))
        put("corinthians", BadgeColors(0xFF1D1D1B, 0xFFFFFFFF))
        put("são paulo", BadgeColors(0xFFFE0000, 0xFF1D1D1B))
        put("gremio", BadgeColors(0xFF0D80BF, 0xFF1D1D1B))
        put("internacional", BadgeColors(0xFFE5050F, 0xFFFFFFFF))
        put("boca juniors", BadgeColors(0xFF103F79, 0xFFFDB913))
        put("river plate", BadgeColors(0xFFE5E7EB, 0xFFD00027))
        put("fluminense", BadgeColors(0xFF7A1128, 0xFF00613C))
        // Africa & Middle East (Morocco focus)
        put("raja", BadgeColors(0xFF00A651, 0xFFFFFFFF))
        put("wydad", BadgeColors(0xFFC8102E, 0xFFFFFFFF))
        put("al ahly", BadgeColors(0xFFC8102E, 0xFFFFFFFF))
        put("ahly", BadgeColors(0xFFC8102E, 0xFFFFFFFF))
        put("zamalek", BadgeColors(0xFFE5E7EB, 0xFFC8102E))
        put("espérance", BadgeColors(0xFFFCD502, 0xFFC8102E))
        put("esperance", BadgeColors(0xFFFCD502, 0xFFC8102E))
        // National teams
        put("morocco", BadgeColors(0xFFC1272D, 0xFFFFFFFF))
        put("maroc", BadgeColors(0xFFC1272D, 0xFFFFFFFF))
        put("france", BadgeColors(0xFF21304D, 0xFFFFFFFF))
        put("spain", BadgeColors(0xFFC60B1E, 0xFFFFC400))
        put("germany", BadgeColors(0xFFF3F4F6, 0xFF1D1D1F))
        put("england", BadgeColors(0xFFF3F4F6, 0xFF1B2A5B))
        put("portugal", BadgeColors(0xFF046A38, 0xFFDA291C))
        put("netherlands", BadgeColors(0xFFF36C21, 0xFFFFFFFF))
        put("italy", BadgeColors(0xFF0066B2, 0xFFFFFFFF))
        put("belgium", BadgeColors(0xFFE30613, 0xFF1D1D1B))
        put("croatia", BadgeColors(0xFFE63946, 0xFFFFFFFF))
        put("senegal", BadgeColors(0xFF00853F, 0xFFFFFFFF))
        put("egypt", BadgeColors(0xFFC8102E, 0xFFFFFFFF))
        put("algeria", BadgeColors(0xFF006233, 0xFFFFFFFF))
        put("tunisia", BadgeColors(0xFFE70013, 0xFFFFFFFF))
        put("nigeria", BadgeColors(0xFF008751, 0xFFFFFFFF))
        put("ivory coast", BadgeColors(0xFFF77F00, 0xFF009A44))
        put("cameroon", BadgeColors(0xFF007A5E, 0xFFE3120B))
        put("ghana", BadgeColors(0xFF006B3F, 0xFFFCD116))
        put("saudi arabia", BadgeColors(0xFF006C35, 0xFFFFFFFF))
        put("qatar", BadgeColors(0xFF8A1538, 0xFFFFFFFF))
        put("japan", BadgeColors(0xFF1D3B8B, 0xFFFFFFFF))
        put("korea", BadgeColors(0xFFCD2E3A, 0xFFFFFFFF))
        put("usa", BadgeColors(0xFF1F2A63, 0xFFFFFFFF))
        put("mexico", BadgeColors(0xFF006847, 0xFFFFFFFF))
        put("argentina", BadgeColors(0xFF75AADB, 0xFF0F2D52))
        put("brazil", BadgeColors(0xFF009739, 0xFFFFDF00))
        put("uruguay", BadgeColors(0xFF5CBFEB, 0xFF0F2D52))
        put("colombia", BadgeColors(0xFFFCD116, 0xFF003893))
        put("ecuador", BadgeColors(0xFFFFDD00, 0xFF034EA2))
        put("poland", BadgeColors(0xFFDC143C, 0xFFFFFFFF))
        put("austria", BadgeColors(0xFFED2939, 0xFFFFFFFF))
        put("switzerland", BadgeColors(0xFFDA291C, 0xFFFFFFFF))
        put("denmark", BadgeColors(0xFFC60C30, 0xFFFFFFFF))
        put("serbia", BadgeColors(0xFF0C4076, 0xFFFFFFFF))
        put("ukraine", BadgeColors(0xFF0057B7, 0xFFFFD700))
        put("turkey", BadgeColors(0xFFE30A17, 0xFFFFFFFF))
        put("scotland", BadgeColors(0xFF1B3C8C, 0xFFFFFFFF))
        put("wales", BadgeColors(0xFF00AD63, 0xFFFFFFFF))
    }

    /** Deterministic fallback palette for teams not in the map. */
    private val fallback = listOf(
        BadgeColors(0xFF1D4ED8, 0xFFFFFFFF), BadgeColors(0xFF0F766E, 0xFFFFFFFF),
        BadgeColors(0xFF7C3AED, 0xFFFFFFFF), BadgeColors(0xFFB45309, 0xFFFFFFFF),
        BadgeColors(0xFFBE123C, 0xFFFFFFFF), BadgeColors(0xFF0369A1, 0xFFFFFFFF),
        BadgeColors(0xFF4D7C0F, 0xFFFFFFFF), BadgeColors(0xFF9333EA, 0xFFFFFFFF),
        BadgeColors(0xFF334155, 0xFFFFFFFF), BadgeColors(0xFF0E7490, 0xFFFFFFFF),
        BadgeColors(0xFFB91C1C, 0xFFFFFFFF), BadgeColors(0xFF374151, 0xFFFFFFFF)
    )

    fun colorsFor(teamName: String): BadgeColors {
        val n = teamName.lowercase()
        // Earliest-position match wins ("Raja Club Athletic" -> "raja" at 0,
        // NOT "athletic" at 10); longer key breaks ties at the same position.
        var best: BadgeColors? = null
        var bestAt = Int.MAX_VALUE
        var bestLen = -1
        map.forEach { (key, colors) ->
            val at = n.indexOf(key)
            if (at >= 0 && (at < bestAt || (at == bestAt && key.length > bestLen))) {
                best = colors
                bestAt = at
                bestLen = key.length
            }
        }
        return best
            ?: fallback[((teamName.hashCode()) % fallback.size + fallback.size) % fallback.size]
    }

    /** True when the team is in the known-colors map (drives Match of the Day fame). */
    fun isKnown(teamName: String): Boolean {
        val n = teamName.lowercase()
        return map.keys.any { it in n }
    }

    /** Initials when the API gives no TLA: "Manchester United" -> "MU". */
    fun initialsFor(teamName: String): String {
        val skip = setOf("fc", "cf", "sc", "ac", "afc", "cfc", "as", "ss", "ssd", "calcio", "club", "de", "the", "1901")
        val words = teamName.split(" ", "-", "·")
            .map { it.filter { c -> c.isLetterOrDigit() } }
            .filter { it.isNotEmpty() && it.lowercase() !in skip }
        val letters = if (words.size >= 2) {
            words.take(3).map { it.first() }
        } else {
            (words.firstOrNull() ?: teamName).take(2).map { it }
        }
        return letters.joinToString("").uppercase().ifEmpty { teamName.take(2).uppercase() }
    }
}
