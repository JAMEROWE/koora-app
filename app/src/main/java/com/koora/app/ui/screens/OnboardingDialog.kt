package com.koora.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.koora.app.R
import com.koora.app.data.FavoriteTeamsStore
import com.koora.app.data.Match

/**
 * First-launch personalization: pick favorite leagues and teams once;
 * Koora pins those leagues to the top of every match day and opens the
 * sections that hold a favorite team's match. Shown once on first
 * launch, re-openable any time from Settings. Local storage only.
 */
@Composable
fun OnboardingDialog(window: List<Match>, onDone: () -> Unit) {
    val context = LocalContext.current
    // Settings re-opens this dialog without a loaded window: the stored
    // picks still render (checked) so they can be reviewed and changed.
    val storedLeagues = remember { FavoriteTeamsStore.leagues(context) }
    val storedTeams = remember { FavoriteTeamsStore.teams(context) }
    val leagues = remember(window) {
        (window.mapNotNull { m -> m.competition?.let { it.code to it.name } } +
            storedLeagues.map { it to it })
            .filter { it.first.isNotBlank() }
            .distinctBy { it.first }
            .sortedBy { it.second }
    }
    val teams = remember(window) {
        (window.flatMap { listOf(it.homeTeam.name, it.awayTeam.name) } + storedTeams)
            .distinct()
            .sorted()
    }
    var pickedLeagues by remember { mutableStateOf(FavoriteTeamsStore.leagues(context)) }
    var pickedTeams by remember { mutableStateOf(FavoriteTeamsStore.teams(context)) }
    var query by remember { mutableStateOf("") }

    fun dismiss() {
        FavoriteTeamsStore.markOnboardingDone(context)
        onDone()
    }

    Dialog(onDismissRequest = { dismiss() }) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            tonalElevation = 6.dp,
            modifier = Modifier.testTag("onboarding_dialog")
        ) {
            Column(
                Modifier
                    .padding(20.dp)
                    .heightIn(max = 560.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    stringResource(R.string.onboarding_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.onboarding_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(16.dp))

                Text(
                    stringResource(R.string.onboarding_leagues),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(4.dp))
                leagues.forEach { (code, name) ->
                    val checked = code in pickedLeagues
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                pickedLeagues =
                                    if (checked) pickedLeagues - code else pickedLeagues + code
                            }
                    ) {
                        Checkbox(checked = checked, onCheckedChange = null)
                        Text(name, style = MaterialTheme.typography.bodyMedium)
                    }
                }

                Spacer(Modifier.height(16.dp))
                Text(
                    stringResource(R.string.onboarding_teams),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(4.dp))
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text(stringResource(R.string.onboarding_search_teams)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("onboarding_team_search")
                )
                val q = query.trim().lowercase()
                teams.filter { q.isEmpty() || it.lowercase().contains(q) }
                    .take(40)
                    .forEach { name ->
                        val checked = name in pickedTeams
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    pickedTeams =
                                        if (checked) pickedTeams - name else pickedTeams + name
                                }
                        ) {
                            Checkbox(checked = checked, onCheckedChange = null)
                            Text(name, style = MaterialTheme.typography.bodyMedium)
                        }
                    }

                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth()) {
                    TextButton(onClick = { dismiss() }) {
                        Text(stringResource(R.string.onboarding_skip))
                    }
                    Spacer(Modifier.weight(1f))
                    Button(
                        onClick = {
                            FavoriteTeamsStore.setAll(context, pickedTeams, pickedLeagues)
                            dismiss()
                        },
                        modifier = Modifier.testTag("onboarding_save")
                    ) {
                        Text(stringResource(R.string.onboarding_save))
                    }
                }
                Spacer(Modifier.width(1.dp))
            }
        }
    }
}
