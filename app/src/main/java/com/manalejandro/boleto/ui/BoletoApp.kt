package com.manalejandro.boleto.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Casino
import androidx.compose.material.icons.automirrored.rounded.CompareArrows
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import com.manalejandro.boleto.R
import com.manalejandro.boleto.ui.screens.CompareScreen
import com.manalejandro.boleto.ui.screens.GenerateScreen
import com.manalejandro.boleto.ui.screens.HistoryScreen
import com.manalejandro.boleto.ui.screens.HomeScreen
import com.manalejandro.boleto.ui.screens.StatsScreen

enum class Screen(val labelRes: Int, val titleRes: Int, val icon: ImageVector) {
    HOME(R.string.nav_home, R.string.app_name, Icons.Rounded.Home),
    HISTORY(R.string.nav_history, R.string.history_title, Icons.Rounded.History),
    STATS(R.string.nav_stats, R.string.nav_stats, Icons.Rounded.BarChart),
    PLAY(R.string.nav_play, R.string.play_title, Icons.Rounded.Casino),
    COMPARE(R.string.compare_title, R.string.compare_title, Icons.AutoMirrored.Rounded.CompareArrows),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BoletoApp(viewModel: MainViewModel = viewModel()) {
    val state by viewModel.state.collectAsState()
    var screen by rememberSaveable { mutableStateOf(Screen.HOME) }
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val resources = LocalResources.current
    val appName = stringResource(R.string.app_name)
    val shareSubject = stringResource(R.string.play_share_subject)

    LaunchedEffect(Unit) {
        viewModel.messages.collect { message ->
            val text = when (message) {
                is AppMessage.SyncDone -> resources.getString(R.string.home_up_to_date, message.added)
                AppMessage.SyncFailed -> resources.getString(
                    R.string.home_update_error,
                    resources.getString(R.string.error_network),
                )
                AppMessage.TicketCopied -> resources.getString(R.string.play_copied)
                AppMessage.NotEnoughHistory -> resources.getString(R.string.history_empty)
            }
            snackbarHostState.showSnackbar(text)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(screen.titleRes)) },
                navigationIcon = {
                    if (screen == Screen.COMPARE) {
                        IconButton(onClick = { screen = Screen.PLAY }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = stringResource(R.string.action_back),
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                ),
            )
        },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                listOf(Screen.HOME, Screen.HISTORY, Screen.STATS, Screen.PLAY).forEach { item ->
                    val selected = screen == item || (item == Screen.PLAY && screen == Screen.COMPARE)
                    NavigationBarItem(
                        selected = selected,
                        onClick = { screen = item },
                        icon = { Icon(item.icon, contentDescription = null) },
                        label = { Text(stringResource(item.labelRes)) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                        ),
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            if (state.loading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                when (screen) {
                    Screen.HOME -> HomeScreen(
                        state = state,
                        onSync = viewModel::sync,
                        onGenerate = { screen = Screen.PLAY },
                    )

                    Screen.HISTORY -> HistoryScreen(state = state)

                    Screen.STATS -> StatsScreen(state = state)

                    Screen.PLAY -> GenerateScreen(
                        state = state,
                        onGenerate = viewModel::generate,
                        onCheck = viewModel::check,
                        onCompare = { screen = Screen.COMPARE },
                        onShare = { text -> shareText(context, text, shareSubject) },
                        onCopy = { text ->
                            copyText(context, text, appName)
                            viewModel.notifyCopied()
                        },
                    )

                    Screen.COMPARE -> CompareScreen(state = state, onRun = viewModel::compare)
                }
            }
        }
    }
}

private fun shareText(context: Context, text: String, subject: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
        putExtra(Intent.EXTRA_SUBJECT, subject)
    }
    context.startActivity(Intent.createChooser(intent, null))
}

private fun copyText(context: Context, text: String, label: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
}
