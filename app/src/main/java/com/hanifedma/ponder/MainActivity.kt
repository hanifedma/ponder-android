package com.hanifedma.ponder

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hanifedma.ponder.i18n.Tr
import com.hanifedma.ponder.notify.BatteryPolicy
import com.hanifedma.ponder.ui.HomeCallbacksFactory
import com.hanifedma.ponder.ui.Mode
import com.hanifedma.ponder.ui.PonderViewModel
import com.hanifedma.ponder.ui.UiEvent
import com.hanifedma.ponder.ui.screens.BusyOverlay
import com.hanifedma.ponder.ui.screens.DuplicateConfirmDialog
import com.hanifedma.ponder.ui.screens.DuplicatesOverlay
import com.hanifedma.ponder.ui.screens.HomeScreen
import com.hanifedma.ponder.ui.screens.LoginScreen
import com.hanifedma.ponder.ui.screens.MigrationDialog
import com.hanifedma.ponder.ui.screens.SettingsDialog
import com.hanifedma.ponder.ui.screens.ShuffleOverlay
import com.hanifedma.ponder.ui.theme.PonderTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val viewModel: PonderViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Hold the launch screen until the app knows whether it is signed in, so
        // the login screen never flashes for someone who already is.
        splash.setKeepOnScreenCondition { viewModel.state.value.booting }

        // Only on a genuinely new launch — not when the process is being restored.
        if (savedInstanceState == null) consumeSharedText(intent)

        setContent { PonderRoot(viewModel) }
    }

    /**
     * Coming to the foreground is the one moment Android reliably allows a
     * foreground service to be started, so it is where a keep-alive service that
     * was killed while the app was away gets brought back. It is also when to
     * re-read permissions the person may have just changed in system settings.
     */
    override fun onStart() {
        super.onStart()
        viewModel.onAppForeground()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consumeSharedText(intent)
    }

    /** Text shared or selected in another app lands in the composer. */
    private fun consumeSharedText(intent: Intent?) {
        if (intent == null) return
        val text = when (intent.action) {
            Intent.ACTION_SEND ->
                intent.getStringExtra(Intent.EXTRA_TEXT)

            Intent.ACTION_PROCESS_TEXT ->
                intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()

            else -> null
        }
        if (!text.isNullOrBlank()) viewModel.prefillFromShare(text)
        // A browser share usually carries the page title; it makes a good source.
        intent.getStringExtra(Intent.EXTRA_SUBJECT)
            ?.takeIf { it.isNotBlank() && intent.action == Intent.ACTION_SEND }
            ?.let { viewModel.setComposerSource(it) }
        // Don't re-apply the same share if the activity is resumed later.
        intent.action = null
    }
}

@Composable
private fun PonderRoot(viewModel: PonderViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val tr = remember(state.lang) { Tr(state.lang) }

    PonderTheme(darkTheme = state.dark, tr = tr) {
        val colors = PonderTheme.colors
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val snackbarHostState = remember { SnackbarHostState() }

        // Keep the system bars' icons legible against whichever theme is active.
        val activity = context as? ComponentActivity
        LaunchedEffect(state.dark, activity) {
            val window = activity?.window ?: return@LaunchedEffect
            WindowCompat.getInsetsController(window, window.decorView).apply {
                isAppearanceLightStatusBars = !state.dark
                isAppearanceLightNavigationBars = !state.dark
            }
        }

        val exportLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument("application/pdf")
        ) { uri -> uri?.let(viewModel::exportTo) }

        // Android 13+ will not show a single notification until this is granted,
        // so it is asked for once, as soon as there is a screen behind it.
        val notificationPermission = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { viewModel.onNotificationPermissionResult() }

        LaunchedEffect(state.booting) {
            if (state.booting) return@LaunchedEffect
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return@LaunchedEffect
            if (!viewModel.shouldAskNotificationPermission()) return@LaunchedEffect
            val granted = ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
            viewModel.onNotificationPermissionAsked()
            if (granted) {
                viewModel.onNotificationPermissionResult()
            } else {
                runCatching {
                    notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        }

        val showMessage: (String) -> Unit = { message ->
            scope.launch { snackbarHostState.showSnackbar(message) }
        }

        LaunchedEffect(viewModel) {
            viewModel.events.collect { event ->
                when (event) {
                    is UiEvent.Snack -> scope.launch {
                        val result = snackbarHostState.showSnackbar(
                            message = event.message,
                            actionLabel = event.actionLabel,
                            duration = if (event.long) SnackbarDuration.Long else SnackbarDuration.Short,
                        )
                        if (result == SnackbarResult.ActionPerformed) event.onAction?.invoke()
                    }

                    is UiEvent.RequestExportLocation ->
                        runCatching { exportLauncher.launch(event.fileName) }
                            .onFailure { showMessage(tr("pdf.err")) }

                    is UiEvent.OpenPdf ->
                        if (!openPdf(context, event.uri)) showMessage(tr("pdf.noViewer"))
                }
            }
        }

        // Kept stable across recompositions so the memoized link spans in each
        // entry card are not rebuilt on every frame.
        val openUrl: (String) -> Unit = remember(context, tr) {
            { url -> if (!openExternally(context, url)) showMessage(tr("pdf.noViewer")) }
        }

        val callbacks = remember(viewModel, activity, openUrl) {
            HomeCallbacksFactory.create(viewModel, activity, openUrl)
        }

        Box(
            Modifier
                .fillMaxSize()
                .background(colors.bg)
        ) {
            when {
                state.booting -> BootMark()

                state.mode == Mode.LOGIN -> LoginScreen(
                    signingIn = state.signingIn,
                    errorText = state.loginError,
                    onSignIn = callbacks.signIn,
                    onUseLocal = viewModel::useLocalMode,
                )

                else -> HomeScreen(state = state, callbacks = callbacks)
            }

            state.shuffle?.let { shuffle ->
                BackHandler(enabled = true, onBack = viewModel::closeShuffle)
                ShuffleOverlay(
                    shuffle = shuffle,
                    entry = state.shuffleEntry,
                    space = state.space,
                    onSetTag = viewModel::setShuffleTag,
                    onNext = viewModel::shuffleNext,
                    onClose = viewModel::closeShuffle,
                    onOpenUrl = openUrl,
                )
            }

            state.duplicateGroups?.let { groups ->
                BackHandler(enabled = true, onBack = viewModel::closeDuplicates)
                DuplicatesOverlay(
                    groups = groups,
                    onDelete = viewModel::deleteFromDuplicates,
                    onClose = viewModel::closeDuplicates,
                    onOpenUrl = openUrl,
                )
            }

            state.duplicateConfirm?.let { matches ->
                DuplicateConfirmDialog(
                    matches = matches,
                    onCancel = viewModel::dismissDuplicateConfirm,
                    onAddAnyway = viewModel::confirmAddAnyway,
                    onOpenUrl = openUrl,
                )
            }

            if (state.settingsOpen) {
                SettingsDialog(
                    startupSpaceKey = state.startupSpaceKey,
                    defaultSort = state.defaultSort,
                    notifyEnabled = state.notifyEnabled,
                    notifySpaceKey = state.notifySpaceKey,
                    keepAlive = state.keepAlive,
                    notifyBlocked = state.notifyBlocked,
                    notifyPoolCount = state.notifyPoolCount,
                    batteryUnrestricted = state.batteryUnrestricted,
                    onSetStartupSpace = viewModel::setStartupSpace,
                    onSetDefaultSort = viewModel::setDefaultSort,
                    onSetNotifyEnabled = viewModel::setNotifyEnabled,
                    onSetNotifySpace = viewModel::setNotifySpace,
                    onSetKeepAlive = viewModel::setKeepAlive,
                    onOpenNotificationSettings = {
                        if (!openNotificationSettings(context)) showMessage(tr("err.noScreen"))
                    },
                    onAllowBackground = {
                        if (!requestBackgroundAllowance(context)) showMessage(tr("err.noScreen"))
                    },
                    onClose = viewModel::closeSettings,
                )
            }

            state.migrationPrompt?.let { prompt ->
                MigrationDialog(
                    count = prompt.count,
                    onMove = viewModel::acceptMigration,
                    onKeep = viewModel::declineMigration,
                )
            }

            state.busyMessage?.let { BusyOverlay(it) }

            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(12.dp),
            )
        }
    }
}

/** The quotation mark shown while the app decides which screen to open. */
@Composable
private fun BootMark() {
    val colors = PonderTheme.colors
    Box(
        Modifier
            .fillMaxSize()
            .background(colors.bg),
        contentAlignment = Alignment.Center,
    ) {
        Text("❝", color = colors.accent, fontSize = 46.sp)
    }
}

private fun openExternally(context: Context, url: String): Boolean = try {
    context.startActivity(
        Intent(Intent.ACTION_VIEW, Uri.parse(url))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
    true
} catch (e: ActivityNotFoundException) {
    false
} catch (e: SecurityException) {
    false
}

/**
 * Ponder's own page in the system notification settings — the only place a
 * system-level block on its notifications can be lifted.
 */
private fun openNotificationSettings(context: Context): Boolean {
    val intents = buildList {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            add(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            )
        }
        // Pre-Oreo, and as a fallback wherever the screen above is missing.
        add(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.fromParts("package", context.packageName, null))
        )
    }
    return intents.any { startExternal(context, it) }
}

/**
 * Asks Android to stop putting Ponder to sleep. The one-tap dialog is not
 * present on every build, so the full battery-optimisation list backs it up.
 */
private fun requestBackgroundAllowance(context: Context): Boolean =
    startExternal(context, BatteryPolicy.requestIntent(context)) ||
        startExternal(context, BatteryPolicy.settingsIntent())

private fun startExternal(context: Context, intent: Intent): Boolean = try {
    context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    true
} catch (e: ActivityNotFoundException) {
    false
} catch (e: SecurityException) {
    false
}

private fun openPdf(context: Context, uri: Uri): Boolean = try {
    context.startActivity(
        Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/pdf")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
    true
} catch (e: ActivityNotFoundException) {
    false
} catch (e: SecurityException) {
    false
}
