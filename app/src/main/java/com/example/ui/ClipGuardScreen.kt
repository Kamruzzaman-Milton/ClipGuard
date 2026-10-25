package com.example.ui

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.ContentPasteOff
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.model.AppFilter
import com.example.model.AppItem
import com.example.model.BatchConfirmation
import com.example.model.BatchProgressState
import com.example.model.ShizukuStatus
import com.example.ui.theme.AmoledCardSurface
import com.example.ui.theme.AmoledDivider
import com.example.ui.theme.AmoledElevatedSurface
import com.example.ui.theme.ClipGuardTypography
import com.example.ui.theme.HyperBlue
import com.example.ui.theme.LightCardSurface
import com.example.ui.theme.LightDivider
import com.example.ui.theme.LightSecondarySurface
import com.example.ui.theme.StatusDanger
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.StatusWarning
import com.example.viewmodel.ClipGuardViewModel
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.rememberTopAppBarState
import top.yukonga.miuix.kmp.extra.SuperDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun ClipGuardScreen(
    viewModel: ClipGuardViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val isDark = isSystemInDarkTheme()

    LaunchedEffect(viewModel) {
        viewModel.toastEvents.collect { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    val scrollBehavior = MiuixScrollBehavior(rememberTopAppBarState())
    var showActionMenuDialog by remember { mutableStateOf(false) }
    val showConfirmDialog = remember { mutableStateOf(false) }
    val showProgressDialog = remember { mutableStateOf(false) }

    showConfirmDialog.value = uiState.batchConfirmation != null
    showProgressDialog.value = uiState.batchProgress != null

    val filteredApps = remember(
        uiState.allApps,
        uiState.searchQuery,
        uiState.selectedFilter
    ) {
        uiState.filteredApps
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = stringResource(R.string.app_name),
                largeTitle = stringResource(R.string.app_name),
                scrollBehavior = scrollBehavior,
                color = MiuixTheme.colorScheme.background,
                actions = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        IconButton(
                            onClick = { viewModel.loadAllApps() },
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("refresh_apps_button")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Refresh,
                                contentDescription = stringResource(R.string.action_refresh),
                                tint = MiuixTheme.colorScheme.onBackground
                            )
                        }
                        IconButton(
                            onClick = { showActionMenuDialog = true },
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("top_bar_menu_button")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.MoreVert,
                                contentDescription = "Batch Actions Menu",
                                tint = MiuixTheme.colorScheme.onBackground
                            )
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MiuixTheme.colorScheme.background)
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 680.dp)
            ) {
                // Shizuku status banner (hidden when Ready, shown with action button otherwise)
                AnimatedVisibility(
                    visible = !uiState.shizukuStatus.isReady,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    ShizukuStatusBanner(
                        status = uiState.shizukuStatus,
                        isDark = isDark,
                        onActionClick = { viewModel.handleShizukuBannerAction() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                    )
                }

                // Active Shizuku summary pill + stats header
                SummaryHeaderRow(
                    shizukuStatus = uiState.shizukuStatus,
                    filteredCount = filteredApps.size,
                    blockedCount = uiState.blockedCount,
                    isDark = isDark,
                    onQuickAllowAll = { viewModel.requestBatchOperation(allow = true) },
                    onQuickBlockAll = { viewModel.requestBatchOperation(allow = false) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                )

                // Search bar using Miuix TextField
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    TextField(
                        value = uiState.searchQuery,
                        onValueChange = { viewModel.updateSearchQuery(it) },
                        label = stringResource(R.string.search_placeholder),
                        singleLine = true,
                        useLabelAsPlaceholder = true,
                        backgroundColor = if (isDark) AmoledCardSurface else LightSecondarySurface,
                        cornerRadius = 18.dp,
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.Search,
                                contentDescription = null,
                                tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                modifier = Modifier
                                    .padding(start = 14.dp, end = 4.dp)
                                    .size(20.dp)
                            )
                        },
                        trailingIcon = {
                            if (uiState.searchQuery.isNotEmpty()) {
                                IconButton(
                                    onClick = { viewModel.updateSearchQuery("") },
                                    modifier = Modifier
                                        .size(40.dp)
                                        .testTag("clear_search_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Clear,
                                        contentDescription = "Clear search",
                                        tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("search_text_field")
                    )
                }

                // Filter chips: All / User / System / Blocked
                FilterChipsRow(
                    selectedFilter = uiState.selectedFilter,
                    totalCount = uiState.totalCount,
                    userCount = uiState.userCount,
                    systemCount = uiState.systemCount,
                    blockedCount = uiState.blockedCount,
                    isDark = isDark,
                    onSelectFilter = { viewModel.selectFilter(it) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                )

                // Main content area: Loading / Empty / LazyColumn of apps
                if (uiState.isLoadingApps) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            InfiniteProgressIndicator()
                            Text(
                                text = "Scanning installed packages…",
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                fontSize = 14.sp
                            )
                        }
                    }
                } else if (filteredApps.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Shield,
                                contentDescription = null,
                                tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                modifier = Modifier.size(44.dp)
                            )
                            Text(
                                text = stringResource(R.string.empty_apps_message),
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .testTag("apps_lazy_column"),
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            top = 6.dp,
                            bottom = 28.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(
                            items = filteredApps,
                            key = { "${it.packageName}_${it.uid}" }
                        ) { app ->
                            AppPermissionCard(
                                app = app,
                                isDark = isDark,
                                onToggle = { allowed ->
                                    viewModel.toggleAppClipboard(app, allowed)
                                }
                            )
                        }
                        item {
                            Spacer(
                                modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)
                            )
                        }
                    }
                }
            }
        }
    }

    // Top bar menu dialog ("Allow all" / "Block all" for the filtered list)
    val menuDialogState = remember { mutableStateOf(false) }
    menuDialogState.value = showActionMenuDialog
    SuperDialog(
        title = "Filtered List Actions",
        summary = "${filteredApps.size} apps in current '${uiState.selectedFilter.labelResName}' filter",
        show = menuDialogState,
        onDismissRequest = { showActionMenuDialog = false }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            MenuOptionRow(
                title = stringResource(R.string.action_allow_all),
                subtitle = "Set READ_CLIPBOARD to MODE_ALLOWED for ${filteredApps.size} filtered apps",
                icon = Icons.Outlined.DoneAll,
                accentColor = HyperBlue,
                isDark = isDark,
                testTag = "menu_allow_all_item",
                onClick = {
                    showActionMenuDialog = false
                    viewModel.requestBatchOperation(allow = true)
                }
            )
            MenuOptionRow(
                title = stringResource(R.string.action_block_all),
                subtitle = "Set READ_CLIPBOARD to MODE_IGNORED for ${filteredApps.size} filtered apps",
                icon = Icons.Outlined.ContentPasteOff,
                accentColor = StatusDanger,
                isDark = isDark,
                testTag = "menu_block_all_item",
                onClick = {
                    showActionMenuDialog = false
                    viewModel.requestBatchOperation(allow = false)
                }
            )
            Spacer(modifier = Modifier.height(4.dp))
            TextButton(
                text = stringResource(R.string.dialog_cancel),
                onClick = { showActionMenuDialog = false },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("menu_cancel_button")
            )
        }
    }

    // Batch Confirmation Dialog
    val confirmation: BatchConfirmation? = uiState.batchConfirmation
    SuperDialog(
        title = if (confirmation?.allow == true) {
            stringResource(R.string.dialog_allow_all_title)
        } else {
            stringResource(R.string.dialog_block_all_title)
        },
        summary = if (confirmation != null) {
            val modeName = if (confirmation.allow) "MODE_ALLOWED (Allow)" else "MODE_IGNORED (Block)"
            "Apply $modeName to ${confirmation.targetApps.size} apps in the current filtered list?"
        } else {
            ""
        },
        show = showConfirmDialog,
        onDismissRequest = { viewModel.dismissBatchConfirmation() }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TextButton(
                text = stringResource(R.string.dialog_cancel),
                onClick = { viewModel.dismissBatchConfirmation() },
                modifier = Modifier
                    .weight(1f)
                    .testTag("batch_cancel_button")
            )
            TextButton(
                text = stringResource(R.string.dialog_confirm),
                onClick = { viewModel.executeConfirmedBatchOperation() },
                colors = ButtonDefaults.textButtonColorsPrimary(),
                modifier = Modifier
                    .weight(1f)
                    .testTag("batch_confirm_button")
            )
        }
    }

    // Batch Progress Dialog
    val progress: BatchProgressState? = uiState.batchProgress
    SuperDialog(
        title = stringResource(R.string.batch_progress_title),
        summary = if (progress != null) {
            "${progress.processedCount} / ${progress.totalCount} • ${progress.currentPackage}"
        } else {
            ""
        },
        show = showProgressDialog,
        onDismissRequest = { /* non-dismissable while running */ }
    ) {
        if (progress != null) {
            val fraction by animateFloatAsState(
                targetValue = if (progress.totalCount > 0) {
                    (progress.processedCount.toFloat() / progress.totalCount.toFloat()).coerceIn(0f, 1f)
                } else {
                    0f
                },
                label = "batch_progress_fraction"
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                LinearProgressIndicator(
                    progress = fraction,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("batch_progress_indicator")
                )
            }
        }
    }
}

@Composable
private fun ShizukuStatusBanner(
    status: ShizukuStatus,
    isDark: Boolean,
    onActionClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val (title, description, buttonLabel, accentColor, icon) = when (status) {
        ShizukuStatus.NotInstalled -> BannerSpec(
            title = stringResource(R.string.shizuku_not_installed_title),
            description = stringResource(R.string.shizuku_not_installed_desc),
            actionText = stringResource(R.string.shizuku_action_install),
            accent = StatusDanger,
            icon = Icons.Outlined.ErrorOutline
        )
        ShizukuStatus.NotRunning -> BannerSpec(
            title = stringResource(R.string.shizuku_not_running_title),
            description = stringResource(R.string.shizuku_not_running_desc),
            actionText = stringResource(R.string.shizuku_action_open),
            accent = StatusWarning,
            icon = Icons.Outlined.WarningAmber
        )
        ShizukuStatus.PermissionNotGranted -> BannerSpec(
            title = stringResource(R.string.shizuku_perm_needed_title),
            description = stringResource(R.string.shizuku_perm_needed_desc),
            actionText = stringResource(R.string.shizuku_action_grant),
            accent = HyperBlue,
            icon = Icons.Outlined.Security
        )
        ShizukuStatus.PermissionDenied -> BannerSpec(
            title = stringResource(R.string.shizuku_perm_denied_title),
            description = stringResource(R.string.shizuku_perm_denied_desc),
            actionText = stringResource(R.string.shizuku_action_retry),
            accent = StatusDanger,
            icon = Icons.Outlined.ErrorOutline
        )
        ShizukuStatus.Ready -> return
    }

    Card(
        modifier = modifier
            .border(
                width = 1.dp,
                color = if (isDark) AmoledDivider else LightDivider,
                shape = RoundedCornerShape(20.dp)
            )
            .testTag("shizuku_status_banner"),
        cornerRadius = 20.dp,
        insideMargin = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        color = if (isDark) AmoledCardSurface else LightCardSurface
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(
                        if (isDark) AmoledElevatedSurface else accentColor.copy(alpha = 0.14f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(22.dp)
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = title,
                    color = MiuixTheme.colorScheme.onSurface,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = description,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(HyperBlue)
                    .clickable(onClick = onActionClick)
                    .padding(horizontal = 14.dp, vertical = 9.dp)
                    .testTag("shizuku_banner_action_button"),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = buttonLabel,
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

private data class BannerSpec(
    val title: String,
    val description: String,
    val actionText: String,
    val accent: Color,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)

@Composable
private fun SummaryHeaderRow(
    shizukuStatus: ShizukuStatus,
    filteredCount: Int,
    blockedCount: Int,
    isDark: Boolean,
    onQuickAllowAll: () -> Unit,
    onQuickBlockAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(
                        if (shizukuStatus.isReady) StatusSuccess else StatusWarning
                    )
            )
            Text(
                text = if (shizukuStatus.isReady) {
                    "Shizuku Active • $filteredCount shown ($blockedCount blocked)"
                } else {
                    "$filteredCount apps shown • $blockedCount blocked"
                },
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            SmallActionPill(
                label = stringResource(R.string.action_allow_all),
                isDark = isDark,
                testTag = "quick_allow_all_pill",
                onClick = onQuickAllowAll
            )
            SmallActionPill(
                label = stringResource(R.string.action_block_all),
                isDark = isDark,
                testTag = "quick_block_all_pill",
                onClick = onQuickBlockAll
            )
        }
    }
}

@Composable
private fun SmallActionPill(
    label: String,
    isDark: Boolean,
    testTag: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (isDark) AmoledCardSurface else LightSecondarySurface)
            .border(
                width = 1.dp,
                color = if (isDark) AmoledDivider else LightDivider,
                shape = RoundedCornerShape(10.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp)
            .testTag(testTag)
    ) {
        Text(
            text = label,
            color = HyperBlue,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun FilterChipsRow(
    selectedFilter: AppFilter,
    totalCount: Int,
    userCount: Int,
    systemCount: Int,
    blockedCount: Int,
    isDark: Boolean,
    onSelectFilter: (AppFilter) -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    Row(
        modifier = modifier
            .horizontalScroll(scrollState)
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AppFilter.entries.forEach { filter ->
            val count = when (filter) {
                AppFilter.ALL -> totalCount
                AppFilter.USER -> userCount
                AppFilter.SYSTEM -> systemCount
                AppFilter.BLOCKED -> blockedCount
            }
            val selected = filter == selectedFilter
            val bgColor by animateColorAsState(
                targetValue = when {
                    selected -> HyperBlue
                    isDark -> AmoledCardSurface
                    else -> LightSecondarySurface
                },
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                label = "chip_bg"
            )
            val textColor by animateColorAsState(
                targetValue = if (selected) {
                    Color.White
                } else {
                    MiuixTheme.colorScheme.onSurface
                },
                label = "chip_text"
            )

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(bgColor)
                    .border(
                        width = 1.dp,
                        color = when {
                            selected -> HyperBlue
                            isDark -> AmoledDivider
                            else -> LightDivider
                        },
                        shape = RoundedCornerShape(14.dp)
                    )
                    .clickable { onSelectFilter(filter) }
                    .padding(horizontal = 14.dp, vertical = 8.dp)
                    .testTag("filter_chip_${filter.name.lowercase()}"),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "${filter.labelResName} ($count)",
                    color = textColor,
                    style = ClipGuardTypography.chipLabel
                )
            }
        }
    }
}

@Composable
private fun AppPermissionCard(
    app: AppItem,
    isDark: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = if (isDark) AmoledDivider else LightDivider,
                shape = RoundedCornerShape(20.dp)
            )
            .clickable { onToggle(!app.isClipboardAllowed) }
            .testTag("app_card_${app.packageName}"),
        cornerRadius = 20.dp,
        insideMargin = PaddingValues(horizontal = 16.dp, vertical = 13.dp),
        color = if (isDark) AmoledCardSurface else LightCardSurface
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // App Icon
            if (app.icon != null) {
                Image(
                    bitmap = app.icon,
                    contentDescription = app.appName,
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(13.dp))
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(13.dp))
                        .background(
                            Brush.linearGradient(
                                colors = listOf(HyperBlue, Color(0xFF3B5BFF))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = app.appName.take(1).uppercase(),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                }
            }

            // App Name + Package Name + Badges
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = app.appName,
                        color = MiuixTheme.colorScheme.onSurface,
                        style = ClipGuardTypography.appTitle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (app.isSystemApp) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(
                                    if (isDark) AmoledElevatedSurface else LightSecondarySurface
                                )
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.system_badge),
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                style = ClipGuardTypography.badgeText
                            )
                        }
                    }
                }

                Text(
                    text = app.packageName,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    style = ClipGuardTypography.packageMono,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Single Miuix Switch (ON = allowed, OFF = blocked)
            Switch(
                checked = app.isClipboardAllowed,
                onCheckedChange = { newAllowed -> onToggle(newAllowed) },
                enabled = !app.isToggling,
                modifier = Modifier.testTag("app_switch_${app.packageName}")
            )
        }
    }
}

@Composable
private fun MenuOptionRow(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accentColor: Color,
    isDark: Boolean,
    testTag: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (isDark) AmoledCardSurface else LightSecondarySurface)
            .border(
                width = 1.dp,
                color = if (isDark) AmoledDivider else LightDivider,
                shape = RoundedCornerShape(16.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = accentColor,
            modifier = Modifier.size(22.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = MiuixTheme.colorScheme.onSurface,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = subtitle,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                fontSize = 12.sp
            )
        }
    }
}
