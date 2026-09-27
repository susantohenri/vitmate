package com.vitmate.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import com.vitmate.app.data.repository.AppLanguage
import com.vitmate.app.data.repository.AppThemeMode
import com.vitmate.app.ui.downloads.DownloadsScreen
import com.vitmate.app.ui.downloads.DownloadsViewModel
import com.vitmate.app.ui.downloads.DownloadsViewModelFactory
import com.vitmate.app.ui.home.HomeScreen
import com.vitmate.app.ui.home.HomeViewModel
import com.vitmate.app.ui.home.HomeViewModelFactory
import com.vitmate.app.ui.navigation.NavDestination
import com.vitmate.app.ui.settings.SettingsScreen
import com.vitmate.app.ui.settings.SettingsViewModel
import com.vitmate.app.ui.settings.SettingsViewModelFactory
import com.vitmate.app.ui.terms.TermsScreen
import com.vitmate.app.ui.theme.BrandPrimary
import com.vitmate.app.ui.theme.VitmateTheme
import java.util.Locale

class MainActivity : ComponentActivity() {

    private val homeViewModel: HomeViewModel by viewModels {
        val app = application as VitmateApp
        HomeViewModelFactory(
            app.remoteConfigRepository,
            app.downloadRepository,
            app.preferencesRepository,
            app.adMobManager
        )
    }

    private val downloadsViewModel: DownloadsViewModel by viewModels {
        val app = application as VitmateApp
        DownloadsViewModelFactory(app.downloadRepository)
    }

    private val settingsViewModel: SettingsViewModel by viewModels {
        val app = application as VitmateApp
        SettingsViewModelFactory(
            app.preferencesRepository,
            app.adMobManager
        )
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* Permission result handled */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val app = application as VitmateApp

        // Request UMP Consent and initialize AdMob (Section 16)
        app.adMobManager.requestConsentAndInit(this)

        // Request Notification permission for Android 13+ (API 33+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        // Handle shared URL from Android Share Sheet
        handleShareSheetIntent(intent)

        setContent {
            val themeMode by settingsViewModel.themeMode.collectAsState()
            val language by settingsViewModel.language.collectAsState()

            // Dynamic locale wrapper for in-app language switching
            val currentLocale = when (language) {
                AppLanguage.INDONESIAN -> Locale("in")
                AppLanguage.ENGLISH -> Locale("en")
                AppLanguage.SYSTEM -> Locale.getDefault()
            }

            val configuration = LocalConfiguration.current
            val updatedConfig = android.content.res.Configuration(configuration).apply {
                setLocale(currentLocale)
            }
            val localizedContext = LocalContext.current.createConfigurationContext(updatedConfig)

            CompositionLocalProvider(
                LocalConfiguration provides updatedConfig,
                LocalContext provides localizedContext
            ) {
                VitmateTheme(themeMode = themeMode) {
                    MainAppScaffold(
                        homeViewModel = homeViewModel,
                        downloadsViewModel = downloadsViewModel,
                        settingsViewModel = settingsViewModel
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleShareSheetIntent(intent)
    }

    private fun handleShareSheetIntent(intent: Intent?) {
        if (intent == null) return
        if (intent.action == Intent.ACTION_SEND && intent.type?.startsWith("text/") == true) {
            val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
            if (!sharedText.isNullOrBlank()) {
                val extractedUrl = extractUrl(sharedText)
                homeViewModel.onPasteUrl(extractedUrl)
            }
        }
    }

    private fun extractUrl(text: String): String {
        val urlRegex = Regex("https?://[^\\s]+")
        val match = urlRegex.find(text)
        return match?.value ?: text.trim()
    }
}

@Composable
fun MainAppScaffold(
    homeViewModel: HomeViewModel,
    downloadsViewModel: DownloadsViewModel,
    settingsViewModel: SettingsViewModel
) {
    var currentRoute by rememberSaveable { mutableStateOf(NavDestination.Home.route) }

    Scaffold(
        bottomBar = {
            // Show bottom navigation bar on top-level screens
            if (currentRoute != NavDestination.Terms.route) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface
                ) {
                    NavigationBarItem(
                        selected = currentRoute == NavDestination.Home.route,
                        onClick = { currentRoute = NavDestination.Home.route },
                        icon = {
                            Icon(
                                imageVector = if (currentRoute == NavDestination.Home.route) Icons.Filled.Home else Icons.Outlined.Home,
                                contentDescription = stringResource(R.string.nav_home)
                            )
                        },
                        label = { Text(stringResource(R.string.nav_home)) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = BrandPrimary,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    )

                    NavigationBarItem(
                        selected = currentRoute == NavDestination.Downloads.route,
                        onClick = { currentRoute = NavDestination.Downloads.route },
                        icon = {
                            Icon(
                                imageVector = if (currentRoute == NavDestination.Downloads.route) Icons.Filled.Download else Icons.Outlined.Download,
                                contentDescription = stringResource(R.string.nav_downloads)
                            )
                        },
                        label = { Text(stringResource(R.string.nav_downloads)) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = BrandPrimary,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    )

                    NavigationBarItem(
                        selected = currentRoute == NavDestination.Settings.route,
                        onClick = { currentRoute = NavDestination.Settings.route },
                        icon = {
                            Icon(
                                imageVector = if (currentRoute == NavDestination.Settings.route) Icons.Filled.Settings else Icons.Outlined.Settings,
                                contentDescription = stringResource(R.string.nav_settings)
                            )
                        },
                        label = { Text(stringResource(R.string.nav_settings)) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = BrandPrimary,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (currentRoute) {
                NavDestination.Home.route -> {
                    HomeScreen(
                        viewModel = homeViewModel,
                        onNavigateToDownloads = { currentRoute = NavDestination.Downloads.route }
                    )
                }
                NavDestination.Downloads.route -> {
                    DownloadsScreen(viewModel = downloadsViewModel)
                }
                NavDestination.Settings.route -> {
                    SettingsScreen(
                        viewModel = settingsViewModel,
                        onNavigateToTerms = { currentRoute = NavDestination.Terms.route }
                    )
                }
                NavDestination.Terms.route -> {
                    TermsScreen(
                        onNavigateBack = { currentRoute = NavDestination.Settings.route }
                    )
                }
            }
        }
    }
}
