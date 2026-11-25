package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.ConversationEntity
import com.example.data.ConversationStatus
import com.example.data.FlowConfigEntity
import com.example.data.SentimentLevel
import com.example.ui.DashboardMetrics
import com.example.ui.components.SentimentPill
import com.example.ui.components.StatusPill
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CoralDanger
import com.example.ui.theme.DeepForest
import com.example.ui.theme.EmeraldBright
import com.example.ui.theme.MintAccent

@Composable
fun DashboardScreen(
    config: FlowConfigEntity,
    metrics: DashboardMetrics,
    isNotificationAccessGranted: Boolean,
    recentConversations: List<ConversationEntity>,
    onToggleAutomation: (Boolean) -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onOpenHandoffQueue: () -> Unit,
    onOpenSimulator: () -> Unit,
    onOpenKnowledge: () -> Unit,
    onOpenFlowManager: () -> Unit,
    onOpenConversation: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 840.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Hero Command Card with generated architectural emerald artwork
            item {
                HeroCommandCard(
                    isAutomationEnabled = config.isAutomationEnabled,
                    activePdfCount = metrics.activePdfCount,
                    frustrationThreshold = config.frustrationThreshold,
                    onToggleAutomation = onToggleAutomation,
                    onOpenSimulator = onOpenSimulator
                )
            }

            // Notification Listener Status Card
            item {
                NotificationAccessCard(
                    isGranted = isNotificationAccessGranted,
                    listenWhatsApp = config.listenWhatsApp,
                    listenWhatsAppBusiness = config.listenWhatsAppBusiness,
                    onOpenSettings = onOpenNotificationSettings
                )
            }

            // Urgent Human Handoff Banner when escalations exist
            if (metrics.pendingHandoffs > 0) {
                item {
                    HandoffAlertCard(
                        pendingCount = metrics.pendingHandoffs,
                        onOpenQueue = onOpenHandoffQueue
                    )
                }
            }

            // 2x2 Operational KPI Grid
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        MetricTile(
                            title = "Active PDFs",
                            value = "${metrics.activePdfCount}",
                            subtitle = "${metrics.totalChunkCount} RAG chunks",
                            icon = Icons.Default.Description,
                            accent = MaterialTheme.colorScheme.primary,
                            onClick = onOpenKnowledge,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("kpi_pdfs_card")
                        )
                        MetricTile(
                            title = "Auto-Replies",
                            value = "${metrics.aiRepliesSent}",
                            subtitle = "Gemini 3.8 Flash",
                            icon = Icons.Default.AutoAwesome,
                            accent = EmeraldBright,
                            onClick = onOpenSimulator,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("kpi_replies_card")
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        MetricTile(
                            title = "Guardrail Handoffs",
                            value = "${metrics.guardrailTransfers}",
                            subtitle = "Threshold ≥ ${config.frustrationThreshold}%",
                            icon = Icons.Default.Shield,
                            accent = CoralDanger,
                            onClick = onOpenFlowManager,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("kpi_guardrails_card")
                        )
                        MetricTile(
                            title = "Flow Pipeline",
                            value = if (config.strictRagOnly) "Strict" else "Flex",
                            subtitle = "Top-${config.topKChunks} PDF RAG",
                            icon = Icons.Default.AccountTree,
                            accent = AmberWarning,
                            onClick = onOpenFlowManager,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("kpi_flow_card")
                        )
                    }
                }
            }

            // Recent Activity Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Recent Automated Activity",
                        style = MaterialTheme.typography.titleLarge
                    )
                    FilledTonalButton(
                        onClick = onOpenSimulator,
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                        modifier = Modifier.testTag("dashboard_simulate_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Science,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.test_pipeline_cta))
                    }
                }
            }

            if (recentConversations.isEmpty()) {
                item {
                    EmptyActivityCard(
                        hasPdfs = metrics.activePdfCount > 0,
                        onOpenKnowledge = onOpenKnowledge,
                        onOpenSimulator = onOpenSimulator
                    )
                }
            } else {
                items(recentConversations.take(5), key = { it.id }) { conv ->
                    RecentConversationRow(
                        conversation = conv,
                        onClick = { onOpenConversation(conv.id) }
                    )
                }
            }

            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun HeroCommandCard(
    isAutomationEnabled: Boolean,
    activePdfCount: Int,
    frustrationThreshold: Int,
    onToggleAutomation: (Boolean) -> Unit,
    onOpenSimulator: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = DeepForest)
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Image(
                painter = painterResource(id = R.drawable.img_hero_flow),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize()
            )
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                DeepForest.copy(alpha = 0.92f),
                                DeepForest.copy(alpha = 0.62f)
                            )
                        )
                    )
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Surface(
                            color = if (isAutomationEnabled) MintAccent.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(999.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(if (isAutomationEnabled) MintAccent else Color.LightGray)
                                )
                                Text(
                                    text = if (isAutomationEnabled) {
                                        stringResource(R.string.engine_active)
                                    } else {
                                        stringResource(R.string.engine_paused)
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isAutomationEnabled) MintAccent else Color.White
                                )
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = "ReplyFlow",
                            style = MaterialTheme.typography.displaySmall,
                            color = Color.White
                        )
                        Text(
                            text = stringResource(R.string.dashboard_subtitle),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.78f)
                        )
                    }

                    Switch(
                        checked = isAutomationEnabled,
                        onCheckedChange = onToggleAutomation,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = DeepForest,
                            checkedTrackColor = MintAccent,
                            uncheckedThumbColor = Color.White,
                            uncheckedTrackColor = Color.White.copy(alpha = 0.25f)
                        ),
                        modifier = Modifier.testTag("master_automation_switch")
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "$activePdfCount PDFs armed • Guardrail ≥ $frustrationThreshold%",
                        style = MaterialTheme.typography.labelMedium,
                        color = MintAccent.copy(alpha = 0.92f)
                    )
                    Button(
                        onClick = onOpenSimulator,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MintAccent,
                            contentColor = DeepForest
                        ),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                        modifier = Modifier.testTag("hero_test_button")
                    ) {
                        Text("Test Chat", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationAccessCard(
    isGranted: Boolean,
    listenWhatsApp: Boolean,
    listenWhatsAppBusiness: Boolean,
    onOpenSettings: () -> Unit
) {
    val containerColor = if (isGranted) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
    } else {
        MaterialTheme.colorScheme.tertiaryContainer
    }
    val iconTint = if (isGranted) MaterialTheme.colorScheme.primary else AmberWarning

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = containerColor,
        border = BorderStroke(1.dp, iconTint.copy(alpha = 0.35f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                color = iconTint.copy(alpha = 0.16f),
                shape = CircleShape
            ) {
                Icon(
                    imageVector = if (isGranted) Icons.Default.NotificationsActive else Icons.Default.NotificationsOff,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.padding(10.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isGranted) {
                        stringResource(R.string.listener_connected)
                    } else {
                        stringResource(R.string.listener_disconnected)
                    },
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                val targets = buildList {
                    if (listenWhatsApp) add("WhatsApp")
                    if (listenWhatsAppBusiness) add("WA Business")
                }.joinToString(" & ").ifBlank { "No target apps enabled" }

                Text(
                    text = if (isGranted) {
                        "Listening to $targets via Android NotificationListenerService"
                    } else {
                        "Enable ReplyFlow in Android Notification Access to auto-reply to live WhatsApp messages"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            FilledTonalButton(
                onClick = onOpenSettings,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                modifier = Modifier.testTag("grant_notification_access_button")
            ) {
                Text(if (isGranted) "Settings" else stringResource(R.string.listener_setup_action))
            }
        }
    }
}

@Composable
private fun HandoffAlertCard(
    pendingCount: Int,
    onOpenQueue: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenQueue)
            .testTag("handoff_alert_card"),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        border = BorderStroke(1.dp, CoralDanger.copy(alpha = 0.45f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                color = CoralDanger,
                shape = CircleShape
            ) {
                Icon(
                    imageVector = Icons.Default.SupportAgent,
                    contentDescription = "Human handoff required",
                    tint = Color.White,
                    modifier = Modifier.padding(10.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${stringResource(R.string.handoff_alert_title)} ($pendingCount)",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Bot replies paused by guardrail. Tap to take over conversation.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.85f)
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = "Open handoff queue",
                tint = MaterialTheme.colorScheme.onErrorContainer
            )
        }
    }
}

@Composable
private fun MetricTile(
    title: String,
    value: String,
    subtitle: String,
    icon: ImageVector,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        tonalElevation = 1.dp
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(18.dp)
                )
            }
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun RecentConversationRow(
    conversation: ConversationEntity,
    onClick: () -> Unit
) {
    val status = ConversationStatus.entries.firstOrNull { it.name == conversation.status }
        ?: ConversationStatus.AI_ACTIVE
    val sentiment = SentimentLevel.fromRaw(conversation.lastSentiment)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("recent_conv_${conversation.id}"),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = if (status == ConversationStatus.HANDOFF_REQUIRED) 1.5.dp else 1.dp,
            color = if (status == ConversationStatus.HANDOFF_REQUIRED) {
                CoralDanger.copy(alpha = 0.55f)
            } else {
                MaterialTheme.colorScheme.outlineVariant
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = conversation.contactName,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                StatusPill(status = status)
            }

            Text(
                text = conversation.lastMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SentimentPill(
                    sentiment = sentiment,
                    frustrationScore = conversation.lastFrustrationScore
                )
                if (conversation.isSimulated) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "SIMULATED",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyActivityCard(
    hasPdfs: Boolean,
    onOpenKnowledge: () -> Unit,
    onOpenSimulator: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Default.AutoAwesome,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(34.dp)
            )
            Text(
                text = if (hasPdfs) "Ready for incoming WhatsApp chats" else "Start in 2 quick steps",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = if (hasPdfs) {
                    "Your PDF knowledge base is armed. Simulate a WhatsApp message or enable Notification Access to test live replies and frustration handoffs."
                } else {
                    "1. Import a PDF document (or generate our sample policy PDF) in PDF RAG.\n2. Test the Gemini Flow Manager & Guardrails right here."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (!hasPdfs) {
                    Button(
                        onClick = onOpenKnowledge,
                        modifier = Modifier.testTag("empty_go_knowledge_button")
                    ) {
                        Text("Add PDF Knowledge")
                    }
                }
                FilledTonalButton(
                    onClick = onOpenSimulator,
                    modifier = Modifier.testTag("empty_try_simulator_button")
                ) {
                    Text("Simulate Chat")
                }
            }
        }
    }
}
