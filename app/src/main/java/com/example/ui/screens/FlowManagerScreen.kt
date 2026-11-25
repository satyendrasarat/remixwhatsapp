package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.AssistantTone
import com.example.data.ContactFilterMode
import com.example.data.FlowConfigEntity
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CoralDanger
import com.example.ui.theme.EmeraldBright
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FlowManagerScreen(
    config: FlowConfigEntity,
    onUpdateConfig: ((FlowConfigEntity) -> FlowConfigEntity) -> Unit,
    onOpenSimulator: () -> Unit,
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
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.flow_title),
                            style = MaterialTheme.typography.headlineMedium
                        )
                        Text(
                            text = "Control every stage of WhatsApp automation and human escalation guardrails.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    FilledTonalButton(
                        onClick = onOpenSimulator,
                        modifier = Modifier.testTag("flow_test_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Science,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Test")
                    }
                }
            }

            // NODE 1: WhatsApp Notification Trigger
            item {
                FlowPipelineNode(
                    stepNumber = "01",
                    title = stringResource(R.string.flow_node_trigger),
                    subtitle = "Listen for incoming messages & enforce contact filters",
                    icon = Icons.Default.NotificationsActive,
                    accentColor = MaterialTheme.colorScheme.primary,
                    showBottomConnector = true
                ) {
                    FlowSwitchRow(
                        title = "WhatsApp Personal",
                        subtitle = "Package: com.whatsapp",
                        checked = config.listenWhatsApp,
                        onCheckedChange = { enabled ->
                            onUpdateConfig { it.copy(listenWhatsApp = enabled) }
                        },
                        testTag = "flow_whatsapp_switch"
                    )
                    FlowSwitchRow(
                        title = "WhatsApp Business",
                        subtitle = "Package: com.whatsapp.w4b",
                        checked = config.listenWhatsAppBusiness,
                        onCheckedChange = { enabled ->
                            onUpdateConfig { it.copy(listenWhatsAppBusiness = enabled) }
                        },
                        testTag = "flow_whatsapp_biz_switch"
                    )
                    FlowSwitchRow(
                        title = "Ignore Group Chats",
                        subtitle = "Only automate 1-on-1 customer conversations",
                        checked = config.ignoreGroupChats,
                        onCheckedChange = { enabled ->
                            onUpdateConfig { it.copy(ignoreGroupChats = enabled) }
                        },
                        testTag = "flow_ignore_groups_switch"
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Reply Cooldown per Contact",
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                text = "${config.cooldownSeconds}s",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Slider(
                            value = config.cooldownSeconds.toFloat(),
                            onValueChange = { value ->
                                onUpdateConfig { it.copy(cooldownSeconds = value.roundToInt()) }
                            },
                            valueRange = 2f..60f,
                            steps = 28,
                            modifier = Modifier.testTag("flow_cooldown_slider")
                        )
                    }

                    Text(
                        text = "Contact Filter Mode",
                        style = MaterialTheme.typography.titleSmall
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        ContactFilterMode.entries.forEach { mode ->
                            FilterChip(
                                selected = config.contactFilterMode == mode.name,
                                onClick = {
                                    onUpdateConfig { it.copy(contactFilterMode = mode.name) }
                                },
                                label = { Text(mode.label) },
                                modifier = Modifier.testTag("filter_mode_${mode.name.lowercase()}")
                            )
                        }
                    }

                    if (config.contactFilterMode != ContactFilterMode.ALL.name) {
                        OutlinedTextField(
                            value = config.contactFilterList,
                            onValueChange = { text ->
                                onUpdateConfig { it.copy(contactFilterList = text) }
                            },
                            label = {
                                Text(
                                    if (config.contactFilterMode == ContactFilterMode.ALLOWLIST.name) {
                                        "Allowed Contact Names (comma-separated)"
                                    } else {
                                        "Blocked Contact Names (comma-separated)"
                                    }
                                )
                            },
                            placeholder = { Text("e.g. Alex Chen, VIP Support") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("flow_contact_list_input")
                        )
                    }
                }
            }

            // NODE 2: Human Handoff Guardrail (Coral Shield Card)
            item {
                FlowPipelineNode(
                    stepNumber = "02",
                    title = stringResource(R.string.flow_node_guardrail),
                    subtitle = "Pause bot & escalate to a human agent on live-agent request or frustration",
                    icon = Icons.Default.Shield,
                    accentColor = CoralDanger,
                    highlightedBorder = true,
                    showBottomConnector = true
                ) {
                    FlowSwitchRow(
                        title = "Live Representative Guardrail",
                        subtitle = "Transfer immediately when customer asks for a human agent",
                        checked = config.enableLiveAgentGuardrail,
                        onCheckedChange = { enabled ->
                            onUpdateConfig { it.copy(enableLiveAgentGuardrail = enabled) }
                        },
                        testTag = "guardrail_live_agent_switch"
                    )

                    FlowSwitchRow(
                        title = "Frustration Sentiment Guardrail",
                        subtitle = "Gemini scores emotional frustration (0–100%) and transfers above threshold",
                        checked = config.enableFrustrationGuardrail,
                        onCheckedChange = { enabled ->
                            onUpdateConfig { it.copy(enableFrustrationGuardrail = enabled) }
                        },
                        testTag = "guardrail_frustration_switch"
                    )

                    if (config.enableFrustrationGuardrail) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Frustration Escalation Threshold",
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Surface(
                                    color = CoralDanger.copy(alpha = 0.14f),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = "≥ ${config.frustrationThreshold}%",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = CoralDanger,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }
                            Slider(
                                value = config.frustrationThreshold.toFloat(),
                                onValueChange = { value ->
                                    onUpdateConfig { it.copy(frustrationThreshold = value.roundToInt()) }
                                },
                                valueRange = 30f..95f,
                                steps = 12,
                                colors = SliderDefaults.colors(
                                    thumbColor = CoralDanger,
                                    activeTrackColor = CoralDanger
                                ),
                                modifier = Modifier.testTag("guardrail_threshold_slider")
                            )
                            Text(
                                text = when {
                                    config.frustrationThreshold <= 50 -> "High sensitivity: transfers on mild dissatisfaction or concern."
                                    config.frustrationThreshold <= 75 -> "Balanced: transfers on clear frustration, complaints, or urgency."
                                    else -> "Strict: transfers only on severe anger or intense escalation."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    OutlinedTextField(
                        value = config.customEscalationKeywords,
                        onValueChange = { text ->
                            onUpdateConfig { it.copy(customEscalationKeywords = text) }
                        },
                        label = { Text("Instant Escalation Trigger Phrases") },
                        supportingText = { Text("Comma-separated phrases that always hand off to a human agent") },
                        minLines = 2,
                        maxLines = 3,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("guardrail_keywords_input")
                    )

                    OutlinedTextField(
                        value = config.handoffMessage,
                        onValueChange = { text ->
                            onUpdateConfig { it.copy(handoffMessage = text) }
                        },
                        label = { Text("Warm Handoff Auto-Reply") },
                        supportingText = { Text("Sent once when transferring chat to a human agent") },
                        minLines = 2,
                        maxLines = 4,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("guardrail_handoff_message_input")
                    )
                }
            }

            // NODE 3: PDF RAG Retrieval
            item {
                FlowPipelineNode(
                    stepNumber = "03",
                    title = stringResource(R.string.flow_node_rag),
                    subtitle = "Retrieve top matching PDF passages before composing reply",
                    icon = Icons.Default.Description,
                    accentColor = AmberWarning,
                    showBottomConnector = true
                ) {
                    FlowSwitchRow(
                        title = "Strict PDF Grounding Only",
                        subtitle = "Prevent hallucinations—only answer facts present in indexed PDFs",
                        checked = config.strictRagOnly,
                        onCheckedChange = { enabled ->
                            onUpdateConfig { it.copy(strictRagOnly = enabled) }
                        },
                        testTag = "rag_strict_switch"
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Retrieved Context Chunks (Top-K)",
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                text = "${config.topKChunks} chunks",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Slider(
                            value = config.topKChunks.toFloat(),
                            onValueChange = { value ->
                                onUpdateConfig { it.copy(topKChunks = value.roundToInt()) }
                            },
                            valueRange = 2f..8f,
                            steps = 5,
                            modifier = Modifier.testTag("rag_topk_slider")
                        )
                    }
                }
            }

            // NODE 4: Gemini Response Composer
            item {
                FlowPipelineNode(
                    stepNumber = "04",
                    title = stringResource(R.string.flow_node_composer),
                    subtitle = "Powered by Firebase AI Logic (Gemini 3.8 Flash)",
                    icon = Icons.Default.AutoAwesome,
                    accentColor = EmeraldBright,
                    showBottomConnector = false
                ) {
                    Text(
                        text = "Reply Tone & Persona",
                        style = MaterialTheme.typography.titleSmall
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        AssistantTone.entries.forEach { tone ->
                            FilterChip(
                                selected = config.tone == tone.name,
                                onClick = {
                                    onUpdateConfig { it.copy(tone = tone.name) }
                                },
                                label = { Text(tone.label) },
                                modifier = Modifier.testTag("tone_chip_${tone.name.lowercase()}")
                            )
                        }
                    }

                    OutlinedTextField(
                        value = config.customSystemPrompt,
                        onValueChange = { text ->
                            onUpdateConfig { it.copy(customSystemPrompt = text) }
                        },
                        label = { Text("Custom Assistant Instructions") },
                        minLines = 3,
                        maxLines = 5,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("composer_system_prompt_input")
                    )

                    FlowSwitchRow(
                        title = "Append Automated Signature",
                        subtitle = "Adds \"— ReplyFlow AI Assistant\" below auto-replies",
                        checked = config.appendBotSignature,
                        onCheckedChange = { enabled ->
                            onUpdateConfig { it.copy(appendBotSignature = enabled) }
                        },
                        testTag = "composer_signature_switch"
                    )
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun FlowPipelineNode(
    stepNumber: String,
    title: String,
    subtitle: String,
    icon: ImageVector,
    accentColor: Color,
    highlightedBorder: Boolean = false,
    showBottomConnector: Boolean,
    content: @Composable () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(
                width = if (highlightedBorder) 1.5.dp else 1.dp,
                color = if (highlightedBorder) accentColor.copy(alpha = 0.55f) else MaterialTheme.colorScheme.outlineVariant
            ),
            tonalElevation = 1.dp
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        color = accentColor.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "STAGE $stepNumber",
                            style = MaterialTheme.typography.labelSmall,
                            color = accentColor,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                content()
            }
        }

        if (showBottomConnector) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(26.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .width(2.5.dp)
                        .height(26.dp)
                        .background(MaterialTheme.colorScheme.outline)
                )
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(accentColor)
                )
            }
        }
    }
}

@Composable
private fun FlowSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleSmall)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(testTag)
        )
    }
}
