package org.regnant.android.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.regnant.android.data.*
import org.regnant.android.network.RegnantClient
import org.regnant.android.ui.theme.*

enum class GitTab {
    STATUS,
    BRANCHES,
    COMMITS,
    DIFF,
    PRS,
    RUNS
}

@Composable
fun GitHubScreen(
    client: RegnantClient,
    connectionStatus: ConnectionStatus
) {
    val scope = rememberCoroutineScope()
    var currentTab by remember { mutableStateOf(GitTab.STATUS) }

    var status by remember { mutableStateOf<GitStatusItem?>(null) }
    var branches by remember { mutableStateOf<List<GitBranchItem>>(emptyList()) }
    var commits by remember { mutableStateOf<List<GitCommitItem>>(emptyList()) }
    var diffText by remember { mutableStateOf("") }
    var prs by remember { mutableStateOf<List<GitHubPRItem>>(emptyList()) }
    var runs by remember { mutableStateOf<List<GitHubRunItem>>(emptyList()) }
    var ghUser by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    fun refreshAll() {
        scope.launch {
            isLoading = true
            try {
                val stRes = client.getGitStatus()
                if (stRes.isSuccess) status = stRes.getOrNull()

                val brRes = client.getGitBranches()
                if (brRes.isSuccess) branches = brRes.getOrDefault(emptyList())

                val cmRes = client.getGitCommits(limit = 20)
                if (cmRes.isSuccess) commits = cmRes.getOrDefault(emptyList())

                val dfRes = client.getGitDiff()
                if (dfRes.isSuccess) diffText = dfRes.getOrDefault("")

                val authRes = client.getGitHubAuth()
                if (authRes.isSuccess) {
                    ghUser = authRes.getOrNull()?.get("user")?.asString
                }

                val prRes = client.getGitHubPRs(limit = 10)
                if (prRes.isSuccess) prs = prRes.getOrDefault(emptyList())

                val runRes = client.getGitHubRuns(limit = 10)
                if (runRes.isSuccess) runs = runRes.getOrDefault(emptyList())
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(Unit) {
        refreshAll()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MainBackground)
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Git & GitHub Cockpit",
                    color = TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (ghUser != null) "Logged in as @$ghUser" else "Local Repository View",
                    color = AccentSage,
                    fontSize = 12.sp
                )
            }
            IconButton(onClick = { refreshAll() }) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = AccentSage)
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Navigation Tabs Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            GitTab.values().forEach { tab ->
                val isSelected = currentTab == tab
                Surface(
                    onClick = { currentTab = tab },
                    color = if (isSelected) AccentSage else SurfaceCard,
                    shape = RoundedCornerShape(6.dp),
                    border = if (isSelected) null else androidx.compose.foundation.BorderStroke(1.dp, CardBorder)
                ) {
                    Text(
                        text = tab.name,
                        color = if (isSelected) DeepNavy else TextPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Tab Content
        when (currentTab) {
            GitTab.STATUS -> {
                val st = status
                if (st != null) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                        border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Commit, contentDescription = null, tint = AccentSage)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Branch: ${st.branch}", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text("Tracking: ${st.tracking ?: "None"}", color = TextSecondary, fontSize = 12.sp)
                            Text("Ahead: ${st.ahead} | Behind: ${st.behind}", color = TextSecondary, fontSize = 12.sp)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = if (st.isClean) "Working tree is clean" else "Changes detected (${st.unstaged.size + st.staged.size} modified, ${st.untracked.size} untracked)",
                                color = if (st.isClean) AccentSage else CoralRed,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }

                    if (st.untracked.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text("Untracked Files:", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        LazyColumn(modifier = Modifier.weight(1f)) {
                            items(st.untracked) { f ->
                                Text("? $f", color = TextPrimary, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                            }
                        }
                    }
                } else {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = AccentSage)
                    }
                }
            }

            GitTab.BRANCHES -> {
                LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(branches) { b ->
                        Surface(
                            color = if (b.current) DeepNavy else SurfaceCard,
                            shape = RoundedCornerShape(6.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, if (b.current) AccentSage else CardBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(b.name, color = if (b.current) AccentSage else TextPrimary, fontWeight = if (b.current) FontWeight.Bold else FontWeight.Normal, fontSize = 13.sp)
                                if (b.current) {
                                    Text("HEAD", color = AccentSage, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }

            GitTab.COMMITS -> {
                LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(commits) { c ->
                        Surface(
                            color = SurfaceCard,
                            shape = RoundedCornerShape(6.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(c.subject, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                    Text(c.author, color = TextSecondary, fontSize = 11.sp)
                                    Text(c.shortHash, color = AccentSage, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }

            GitTab.DIFF -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(DeepNavy, RoundedCornerShape(6.dp))
                        .border(1.dp, CardBorder, RoundedCornerShape(6.dp))
                        .padding(10.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = if (diffText.isBlank()) "No working directory changes" else diffText,
                        color = if (diffText.isBlank()) TextSecondary else TextPrimary,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp
                    )
                }
            }

            GitTab.PRS -> {
                if (prs.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No open pull requests found", color = TextSecondary)
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(prs) { pr ->
                            Surface(
                                color = SurfaceCard,
                                shape = RoundedCornerShape(6.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                        Text("#${pr.number} ${pr.title}", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.weight(1f))
                                        Text(pr.state, color = if (pr.state == "OPEN") AccentSage else CoralRed, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("${pr.headRefName} -> ${pr.baseRefName}", color = TextSecondary, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                                }
                            }
                        }
                    }
                }
            }

            GitTab.RUNS -> {
                if (runs.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No recent workflow runs found", color = TextSecondary)
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(runs) { r ->
                            Surface(
                                color = SurfaceCard,
                                shape = RoundedCornerShape(6.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(r.name, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        Text("${r.headBranch} (${r.event ?: "push"})", color = TextSecondary, fontSize = 11.sp)
                                    }
                                    Text(
                                        text = r.conclusion ?: r.status,
                                        color = if (r.conclusion == "success") AccentSage else if (r.conclusion == "failure") CoralRed else TextSecondary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
