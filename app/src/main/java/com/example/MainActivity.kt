package com.example

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ai.configureAppCheck
import com.example.service.HandoffNotifier
import com.example.ui.AppTab
import com.example.ui.ReplyFlowViewModel
import com.example.ui.components.WhatsAppSimulatorSheet
import com.example.ui.screens.ConversationDetailScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.FlowManagerScreen
import com.example.ui.screens.InboxScreen
import com.example.ui.screens.KnowledgeScreen
import com.example.ui.theme.ReplyFlowTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // STEP 1: Configure Firebase App Check with launch intent BEFORE UI, ViewModels, or Firebase operations.
        configureAppCheck(this, intent)

        // STEP 2: Enable full edge-to-edge rendering and ensure handoff notification channel exists.
        enableEdgeToEdge()
        HandoffNotifier.ensureChannel(this)

        val initialConversationId = intent?.getStringExtra(HandoffNotifier.EXTRA_CONVERSATION_ID)

        setContent {
            ReplyFlowTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ReplyFlowApp(initialConversationId = initialConversationId)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        configureAppCheck(this, intent)
    }
}

private data class NavItemSpec(
    val tab: AppTab,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

private val navItems = listOf(
    NavItemSpec(
        tab = AppTab.DASHBOARD,
        labelRes = R.string.nav_dashboard,
        selectedIcon = Icons.Filled.Dashboard,
        unselectedIcon = Icons.Outlined.Dashboard,
        testTag = "nav_tab_dashboard"
    ),
    NavItemSpec(
        tab = AppTab.KNOWLEDGE,
        labelRes = R.string.nav_knowledge,
        selectedIcon = Icons.Filled.Description,
        unselectedIcon = Icons.Outlined.Description,
        testTag = "nav_tab_knowledge"
    ),
    NavItemSpec(
        tab = AppTab.FLOW_MANAGER,
        labelRes = R.string.nav_flow,
        selectedIcon = Icons.Filled.AccountTree,
        unselectedIcon = Icons.Outlined.AccountTree,
        testTag = "nav_tab_flow"
    ),
    NavItemSpec(
        tab = AppTab.INBOX,
        labelRes = R.string.nav_inbox,
        selectedIcon = Icons.Filled.ChatBubble,
        unselectedIcon = Icons.Outlined.ChatBubbleOutline,
        testTag = "nav_tab_inbox"
    )
)

@Composable
fun ReplyFlowApp(
    initialConversationId: String?,
    viewModel: ReplyFlowViewModel = viewModel()
) {
    val context = LocalContext.current

    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
    val selectedConversation by viewModel.selectedConversation.collectAsStateWithLifecycle()
    val selectedMessages by viewModel.selectedMessages.collectAsStateWithLifecycle()
    val flowConfig by viewModel.flowConfig.collectAsStateWithLifecycle()
    val documents by viewModel.documents.collectAsStateWithLifecycle()
    val conversations by viewModel.conversations.collectAsStateWithLifecycle()
    val metrics by viewModel.metrics.collectAsStateWithLifecycle()
    val inboxFilter by viewModel.inboxFilter.collectAsStateWithLifecycle()
    val isNotificationAccessGranted by viewModel.isNotificationAccessGranted.collectAsStateWithLifecycle()
    val pdfOperation by viewModel.pdfOperation.collectAsStateWithLifecycle()
    val simulatorOperation by viewModel.simulatorOperation.collectAsStateWithLifecycle()
    val showSimulatorSheet by viewModel.showSimulatorSheet.collectAsStateWithLifecycle()

    // Open escalated conversation directly if launched from a Human Handoff notification
    LaunchedEffect(initialConversationId) {
        if (!initialConversationId.isNullOrBlank()) {
            viewModel.openConversation(initialConversationId)
        }
    }

    // Request POST_NOTIFICATIONS on Android 13+ so human agents receive live handoff alerts
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { /* Gracefully continues even if denied; in-app alerts still work */ }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    // Zero-permission Storage Access Framework PDF document picker
    val pdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.importPdf(uri)
        }
    }

    // Re-check Notification Listener Access whenever returning from Android System Settings
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refreshNotificationAccessStatus()
    }

    val openNotificationSettings: () -> Unit = {
        val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
    }

    // Full-screen Conversation Detail view when a thread is open
    val activeConversation = selectedConversation
    if (activeConversation != null) {
        ConversationDetailScreen(
            conversation = activeConversation,
            messages = selectedMessages,
            onBack = { viewModel.closeConversation() },
            onSendHumanReply = { reply -> viewModel.sendHumanReply(activeConversation.id, reply) },
            onSimulateFollowUp = { contact, msg -> viewModel.simulateIncomingWhatsApp(contact, msg) },
            onTakeOver = { viewModel.takeOverChat(activeConversation.id) },
            onResumeAi = { viewModel.resumeAiChat(activeConversation.id) },
            onResolve = { viewModel.resolveChat(activeConversation.id) },
            onDelete = { viewModel.deleteChat(activeConversation.id) }
        )
        return
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavigationRail = maxWidth >= 600.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            bottomBar = {
                if (!useNavigationRail) {
                    NavigationBar {
                        navItems.forEach { item ->
                            val selected = selectedTab == item.tab
                            NavigationBarItem(
                                selected = selected,
                                onClick = { viewModel.selectTab(item.tab) },
                                icon = {
                                    NavTabIcon(
                                        item = item,
                                        selected = selected,
                                        pendingHandoffs = metrics.pendingHandoffs
                                    )
                                },
                                label = { Text(stringResource(item.labelRes)) },
                                modifier = Modifier.testTag(item.testTag)
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useNavigationRail) {
                    NavigationRail {
                        navItems.forEach { item ->
                            val selected = selectedTab == item.tab
                            NavigationRailItem(
                                selected = selected,
                                onClick = { viewModel.selectTab(item.tab) },
                                icon = {
                                    NavTabIcon(
                                        item = item,
                                        selected = selected,
                                        pendingHandoffs = metrics.pendingHandoffs
                                    )
                                },
                                label = { Text(stringResource(item.labelRes)) },
                                modifier = Modifier.testTag(item.testTag)
                            )
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f)) {
                    when (selectedTab) {
                        AppTab.DASHBOARD -> DashboardScreen(
                            config = flowConfig,
                            metrics = metrics,
                            isNotificationAccessGranted = isNotificationAccessGranted,
                            recentConversations = conversations,
                            onToggleAutomation = { enabled ->
                                viewModel.updateFlowConfig { it.copy(isAutomationEnabled = enabled) }
                            },
                            onOpenNotificationSettings = openNotificationSettings,
                            onOpenHandoffQueue = { viewModel.openHandoffQueue() },
                            onOpenSimulator = { viewModel.setSimulatorSheetVisible(true) },
                            onOpenKnowledge = { viewModel.selectTab(AppTab.KNOWLEDGE) },
                            onOpenFlowManager = { viewModel.selectTab(AppTab.FLOW_MANAGER) },
                            onOpenConversation = { id -> viewModel.openConversation(id) }
                        )

                        AppTab.KNOWLEDGE -> KnowledgeScreen(
                            documents = documents,
                            banner = pdfOperation,
                            onDismissBanner = { viewModel.clearPdfBanner() },
                            onImportPdfClick = { pdfPickerLauncher.launch(arrayOf("application/pdf")) },
                            onCreateSamplePdfClick = { viewModel.createSamplePdf() },
                            onToggleDocumentEnabled = { docId, enabled ->
                                viewModel.toggleDocumentEnabled(docId, enabled)
                            },
                            onRetryDocument = { doc -> viewModel.retryDocument(doc) },
                            onDeleteDocument = { doc -> viewModel.deleteDocument(doc) },
                            observeChunks = { docId -> viewModel.observeChunksForDocument(docId) }
                        )

                        AppTab.FLOW_MANAGER -> FlowManagerScreen(
                            config = flowConfig,
                            onUpdateConfig = { transform -> viewModel.updateFlowConfig(transform) },
                            onOpenSimulator = { viewModel.setSimulatorSheetVisible(true) }
                        )

                        AppTab.INBOX -> InboxScreen(
                            conversations = conversations,
                            selectedFilter = inboxFilter,
                            onSelectFilter = { filter -> viewModel.setInboxFilter(filter) },
                            onOpenConversation = { id -> viewModel.openConversation(id) },
                            onOpenSimulator = { viewModel.setSimulatorSheetVisible(true) }
                        )
                    }
                }
            }
        }
    }

    if (showSimulatorSheet) {
        WhatsAppSimulatorSheet(
            banner = simulatorOperation,
            onDismissBanner = { viewModel.clearSimulatorBanner() },
            onDismiss = { viewModel.setSimulatorSheetVisible(false) },
            onSimulate = { contact, message ->
                viewModel.simulateIncomingWhatsApp(contact, message)
            }
        )
    }
}

@Composable
private fun NavTabIcon(
    item: NavItemSpec,
    selected: Boolean,
    pendingHandoffs: Int
) {
    val iconVector = if (selected) item.selectedIcon else item.unselectedIcon
    if (item.tab == AppTab.INBOX && pendingHandoffs > 0) {
        BadgedBox(
            badge = {
                Badge {
                    Text(text = if (pendingHandoffs > 9) "9+" else "$pendingHandoffs")
                }
            }
        ) {
            Icon(
                imageVector = iconVector,
                contentDescription = stringResource(item.labelRes)
            )
        }
    } else {
        Icon(
            imageVector = iconVector,
            contentDescription = stringResource(item.labelRes)
        )
    }
}
