package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.KnowledgeChunkEntity
import com.example.data.PdfDocumentEntity
import com.example.ui.OperationBanner
import com.example.ui.components.OperationBannerCard
import com.example.ui.theme.CoralDanger
import kotlinx.coroutines.flow.Flow

@Composable
fun KnowledgeScreen(
    documents: List<PdfDocumentEntity>,
    banner: OperationBanner,
    onDismissBanner: () -> Unit,
    onImportPdfClick: () -> Unit,
    onCreateSamplePdfClick: () -> Unit,
    onToggleDocumentEnabled: (Long, Boolean) -> Unit,
    onRetryDocument: (PdfDocumentEntity) -> Unit,
    onDeleteDocument: (PdfDocumentEntity) -> Unit,
    observeChunks: (Long) -> Flow<List<KnowledgeChunkEntity>>,
    modifier: Modifier = Modifier
) {
    val isIndexing = banner is OperationBanner.Loading

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 840.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = stringResource(R.string.knowledge_title),
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Text(
                        text = "Upload PDF guides, catalogs, or support policies. Gemini extracts structured RAG chunks so WhatsApp replies stay accurate and cited.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = onImportPdfClick,
                        enabled = !isIndexing,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("import_pdf_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.UploadFile,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.knowledge_import_pdf))
                    }

                    FilledTonalButton(
                        onClick = onCreateSamplePdfClick,
                        enabled = !isIndexing,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("create_sample_pdf_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.knowledge_generate_sample))
                    }
                }
            }

            item {
                OperationBannerCard(
                    banner = banner,
                    onDismiss = onDismissBanner
                )
            }

            if (documents.isEmpty()) {
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PictureAsPdf,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .padding(14.dp)
                                        .size(32.dp)
                                )
                            }
                            Text(
                                text = stringResource(R.string.knowledge_empty_title),
                                style = MaterialTheme.typography.titleLarge
                            )
                            Text(
                                text = stringResource(R.string.knowledge_empty_desc),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(documents, key = { it.id }) { doc ->
                    PdfDocumentCard(
                        doc = doc,
                        chunksFlow = observeChunks(doc.id),
                        isBusy = isIndexing,
                        onToggleEnabled = { enabled -> onToggleDocumentEnabled(doc.id, enabled) },
                        onRetry = { onRetryDocument(doc) },
                        onDelete = { onDeleteDocument(doc) }
                    )
                }
            }

            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun PdfDocumentCard(
    doc: PdfDocumentEntity,
    chunksFlow: Flow<List<KnowledgeChunkEntity>>,
    isBusy: Boolean,
    onToggleEnabled: (Boolean) -> Unit,
    onRetry: () -> Unit,
    onDelete: () -> Unit
) {
    var expanded by rememberSaveable(doc.id) { mutableStateOf(false) }
    val chunks by chunksFlow.collectAsStateWithLifecycle(initialValue = emptyList())

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("pdf_doc_card_${doc.id}"),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        tonalElevation = 1.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    color = when (doc.indexingStatus) {
                        "ERROR" -> MaterialTheme.colorScheme.errorContainer
                        else -> MaterialTheme.colorScheme.primaryContainer
                    },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (doc.indexingStatus == "INDEXING") {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .padding(10.dp)
                                .size(22.dp),
                            strokeWidth = 2.5.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.PictureAsPdf,
                            contentDescription = "PDF document",
                            tint = if (doc.indexingStatus == "ERROR") CoralDanger else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = doc.fileName,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val sizeKb = (doc.fileSizeBytes / 1024L).coerceAtLeast(1L)
                    Text(
                        text = "${doc.pageCount} pages • ${doc.chunkCount} chunks • ${sizeKb} KB",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Switch(
                    checked = doc.isEnabled,
                    enabled = doc.indexingStatus == "READY",
                    onCheckedChange = onToggleEnabled,
                    modifier = Modifier.testTag("pdf_toggle_${doc.id}")
                )
            }

            Text(
                text = doc.errorMessage ?: doc.summary,
                style = MaterialTheme.typography.bodySmall,
                color = if (doc.indexingStatus == "ERROR") {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (doc.indexingStatus == "ERROR") {
                    TextButton(
                        onClick = onRetry,
                        enabled = !isBusy,
                        modifier = Modifier.testTag("pdf_retry_${doc.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("Retry Indexing")
                    }
                } else if (doc.chunkCount > 0) {
                    TextButton(
                        onClick = { expanded = !expanded },
                        modifier = Modifier.testTag("pdf_expand_${doc.id}")
                    ) {
                        Icon(
                            imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(if (expanded) "Hide Indexed Chunks" else "Inspect ${doc.chunkCount} RAG Chunks")
                    }
                } else {
                    Spacer(Modifier.width(1.dp))
                }

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.testTag("pdf_delete_${doc.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "Delete PDF document",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            AnimatedVisibility(visible = expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    chunks.forEach { chunk ->
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = chunk.sectionTitle,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        text = "p.${chunk.pageNumber}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Text(
                                    text = chunk.content,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                if (chunk.keywords.isNotBlank()) {
                                    Text(
                                        text = "Tags: ${chunk.keywords}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
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
