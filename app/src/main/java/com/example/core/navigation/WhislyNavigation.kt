package com.example.core.navigation

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.MicNone
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.components.GooeyNavItem
import com.example.components.WhislyGooeyNavBar
import com.example.core.theme.WhislyDesign
import com.example.presentation.WhislyViewModel
import com.example.presentation.about.AboutWhislyScreen
import com.example.presentation.conversation.ConversationScreen
import com.example.presentation.home.HomeScreen
import com.example.presentation.languages.LanguagesScreen
import com.example.presentation.onboarding.WhislyOnboardingScreen
import com.example.presentation.onboarding.WhislySplashScreen
import com.example.presentation.profile.ProfileScreen
import com.example.presentation.saved.HistoryScreen
import com.example.presentation.saved.SavedPhrasesScreen
import com.example.presentation.settings.SettingsScreen
import com.example.presentation.text_translation.TextTranslationScreen
import com.example.presentation.translate.TranslateScreen

object WhislyRoutes {
    const val SPLASH = "splash"
    const val ONBOARDING = "onboarding"
    const val HOME = "home"
    const val TRANSLATE = "translate"
    const val HISTORY = "history"
    const val PROFILE = "profile"
    const val CONVERSATION = "conversation"
    const val LANGUAGES = "languages"
    const val SAVED = "saved"
    const val TEXT_MODE = "text_translation"
    const val SETTINGS = "settings"
    const val ABOUT = "about"
}

/**
 * The 4 Primary Bottom Navigation Destinations:
 * 1. Home
 * 2. Translate
 * 3. History
 * 4. Profile
 */
val GooeyPrimaryDestinations = listOf(
    GooeyNavItem(
        route = WhislyRoutes.HOME,
        label = "Home",
        selectedIcon = Icons.Filled.Home,
        unselectedIcon = Icons.Outlined.Home,
        testTag = "nav_tab_home"
    ),
    GooeyNavItem(
        route = WhislyRoutes.TRANSLATE,
        label = "Translate",
        selectedIcon = Icons.Filled.Mic,
        unselectedIcon = Icons.Outlined.MicNone,
        testTag = "nav_tab_translate"
    ),
    GooeyNavItem(
        route = WhislyRoutes.HISTORY,
        label = "History",
        selectedIcon = Icons.Filled.History,
        unselectedIcon = Icons.Outlined.History,
        testTag = "nav_tab_history"
    ),
    GooeyNavItem(
        route = WhislyRoutes.PROFILE,
        label = "Profile",
        selectedIcon = Icons.Filled.Person,
        unselectedIcon = Icons.Outlined.PersonOutline,
        testTag = "nav_tab_profile"
    )
)

@Composable
fun WhislyAppRoot(
    viewModel: WhislyViewModel,
    startInTranslateDirectly: Boolean = true
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: WhislyRoutes.TRANSLATE
    val colors = WhislyDesign.colors

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val languages by viewModel.languagesState.collectAsStateWithLifecycle()
    val installedModels by viewModel.installedModelsState.collectAsStateWithLifecycle()
    val savedPhrases by viewModel.savedPhrasesState.collectAsStateWithLifecycle()
    val historyItems by viewModel.historyState.collectAsStateWithLifecycle()
    val settings by viewModel.settingsState.collectAsStateWithLifecycle()
    val pipelineStage by viewModel.pipelineStage.collectAsStateWithLifecycle()
    val amplitudes by viewModel.waveformAmplitudes.collectAsStateWithLifecycle()
    val isPlayingAudio by viewModel.isPlayingAudio.collectAsStateWithLifecycle()
    val audioDiagnostics by viewModel.audioDiagnostics.collectAsStateWithLifecycle()

    // Bottom nav hidden on Splash, Onboarding, and About Whisly Screen (Phase 5)
    val showBottomBar = currentRoute !in setOf(
        WhislyRoutes.SPLASH,
        WhislyRoutes.ONBOARDING,
        WhislyRoutes.ABOUT
    )

    val activeTabRoute = when (currentRoute) {
        WhislyRoutes.HOME, WhislyRoutes.CONVERSATION -> WhislyRoutes.HOME
        WhislyRoutes.TRANSLATE, WhislyRoutes.TEXT_MODE -> WhislyRoutes.TRANSLATE
        WhislyRoutes.HISTORY, WhislyRoutes.SAVED -> WhislyRoutes.HISTORY
        WhislyRoutes.PROFILE, WhislyRoutes.LANGUAGES, WhislyRoutes.SETTINGS, WhislyRoutes.ABOUT -> WhislyRoutes.PROFILE
        else -> WhislyRoutes.TRANSLATE
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        containerColor = colors.background,
        bottomBar = {
            if (showBottomBar) {
                WhislyGooeyNavBar(
                    items = GooeyPrimaryDestinations,
                    currentRoute = activeTabRoute,
                    lowEndMode = uiState.lowEndPerformanceMode,
                    onNavigate = { targetRoute ->
                        if (currentRoute != targetRoute) {
                            navController.navigate(targetRoute) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = if (startInTranslateDirectly) WhislyRoutes.TRANSLATE else WhislyRoutes.SPLASH,
            enterTransition = { fadeIn(animationSpec = tween(220, easing = FastOutSlowInEasing)) },
            exitTransition = { fadeOut(animationSpec = tween(200, easing = FastOutSlowInEasing)) },
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            composable(WhislyRoutes.SPLASH) {
                WhislySplashScreen(
                    onSplashFinished = {
                        val onboarded = settings?.onboardingCompleted == true
                        val nextRoute = if (onboarded) WhislyRoutes.TRANSLATE else WhislyRoutes.ONBOARDING
                        navController.navigate(nextRoute) {
                            popUpTo(WhislyRoutes.SPLASH) { inclusive = true }
                        }
                    }
                )
            }

            composable(WhislyRoutes.ONBOARDING) {
                WhislyOnboardingScreen(
                    onFinishOnboarding = { src, tgt ->
                        viewModel.completeOnboarding(src, tgt)
                        navController.navigate(WhislyRoutes.TRANSLATE) {
                            popUpTo(WhislyRoutes.ONBOARDING) { inclusive = true }
                        }
                    }
                )
            }

            composable(WhislyRoutes.HOME) {
                HomeScreen(
                    uiState = uiState,
                    languages = languages,
                    recentHistory = historyItems,
                    isDarkTheme = uiState.isDarkTheme,
                    lowEndMode = uiState.lowEndPerformanceMode,
                    onToggleDarkTheme = viewModel::toggleDarkTheme,
                    onSelectSource = viewModel::selectSourceLanguage,
                    onSelectTarget = viewModel::selectTargetLanguage,
                    onSwapLanguages = viewModel::swapLanguages,
                    onOpenTranslateVoice = { navController.navigate(WhislyRoutes.TRANSLATE) },
                    onOpenConversationMode = { navController.navigate(WhislyRoutes.CONVERSATION) },
                    onOpenTextMode = { navController.navigate(WhislyRoutes.TEXT_MODE) },
                    onOpenLanguagesManager = { navController.navigate(WhislyRoutes.LANGUAGES) },
                    onOpenSavedPhrases = { navController.navigate(WhislyRoutes.SAVED) },
                    onOpenHistory = { navController.navigate(WhislyRoutes.HISTORY) },
                    onSpeakHistoryItem = viewModel::speakArbitraryText
                )
            }

            composable(WhislyRoutes.TRANSLATE) {
                TranslateScreen(
                    uiState = uiState,
                    languages = languages,
                    settings = settings,
                    pipelineStage = pipelineStage,
                    amplitudes = amplitudes,
                    isPlayingAudio = isPlayingAudio,
                    hasMicPermission = viewModel.audioProcessor.hasRecordPermission(),
                    onSelectSource = viewModel::selectSourceLanguage,
                    onSelectTarget = viewModel::selectTargetLanguage,
                    onSwapLanguages = viewModel::swapLanguages,
                    onConfirmAmbiguousSourceLanguage = viewModel::confirmAmbiguousLanguage,
                    onToggleRecording = viewModel::toggleMainRecording,
                    onPermissionDenied = viewModel::onPermissionDenied,
                    onTranscriptChanged = viewModel::updateRecognizedTranscript,
                    onConfirmAndTranslate = viewModel::confirmTranscriptAndTranslate,
                    onTranslatedTextEdited = viewModel::updateTranslatedSentenceBeforePlayback,
                    onPlayAudio = viewModel::playCurrentTranslatedVoice,
                    onStopAudio = viewModel::stopAudioPlayback,
                    onSavePhrase = { viewModel.saveCurrentTranslationToPhrases("General") },
                    onDismissError = viewModel::dismissError,
                    onNavigateToLanguages = {
                        navController.navigate(WhislyRoutes.LANGUAGES) {
                            launchSingleTop = true
                        }
                    },
                    onNavigateToTextMode = { navController.navigate(WhislyRoutes.TEXT_MODE) },
                    onNavigateToHistory = { navController.navigate(WhislyRoutes.HISTORY) },
                    onNavigateToSettings = { navController.navigate(WhislyRoutes.SETTINGS) },
                    onToggleDarkTheme = viewModel::toggleDarkTheme
                )
            }

            composable(WhislyRoutes.HISTORY) {
                HistoryScreen(
                    historyItems = historyItems,
                    onSpeakItem = viewModel::speakArbitraryText,
                    onDeleteItem = viewModel::deleteHistoryItem,
                    onClearAll = viewModel::clearAllHistory,
                    onBack = {
                        if (!navController.popBackStack()) {
                            navController.navigate(WhislyRoutes.TRANSLATE)
                        }
                    }
                )
            }

            composable(WhislyRoutes.PROFILE) {
                ProfileScreen(
                    settings = settings,
                    languages = languages,
                    telemetry = viewModel.getRuntimeTelemetry(),
                    isDarkTheme = uiState.isDarkTheme,
                    lowEndPerformanceMode = uiState.lowEndPerformanceMode,
                    onToggleDarkTheme = viewModel::setDarkTheme,
                    onToggleLowEndMode = viewModel::setLowEndPerformanceMode,
                    onToggleOfflineOnly = viewModel::setOfflineOnlyMode,
                    onSpeechSpeedChange = viewModel::setSpeechSpeed,
                    onOpenLanguagesManager = { navController.navigate(WhislyRoutes.LANGUAGES) },
                    onOpenSavedPhrases = { navController.navigate(WhislyRoutes.SAVED) },
                    onOpenAdvancedSettings = { navController.navigate(WhislyRoutes.SETTINGS) },
                    onOpenAboutWhisly = { navController.navigate(WhislyRoutes.ABOUT) }
                )
            }

            composable(WhislyRoutes.CONVERSATION) {
                ConversationScreen(
                    uiState = uiState,
                    languages = languages,
                    onSelectSource = viewModel::selectSourceLanguage,
                    onSelectTarget = viewModel::selectTargetLanguage,
                    onSwapLanguages = viewModel::swapLanguages,
                    onSpeakerTurnAction = viewModel::toggleConversationTurnRecording,
                    onSubmitTextTurn = viewModel::submitConversationTextTurn,
                    onPlayTurnAudio = { turn ->
                        viewModel.speakArbitraryText(turn.translatedText, turn.targetLangCode)
                    },
                    onClearConversation = viewModel::clearConversation
                )
            }

            composable(WhislyRoutes.LANGUAGES) {
                LanguagesScreen(
                    languages = languages,
                    installedModels = installedModels,
                    availableStorageBytes = viewModel.repository.modelManager.getAvailableStorageBytes(),
                    statusBanner = uiState.statusBannerMessage,
                    searchQuery = uiState.languageSearchQuery,
                    onSearchQueryChange = viewModel::updateLanguageSearchQuery,
                    onInstallLanguage = viewModel::installLanguagePack,
                    onRemoveLanguage = viewModel::removeLanguagePack,
                    onClearAllExtraPacks = viewModel::clearAllExtraPacks
                )
            }

            composable(WhislyRoutes.SAVED) {
                SavedPhrasesScreen(
                    phrases = savedPhrases,
                    searchQuery = uiState.savedSearchQuery,
                    selectedCategory = uiState.savedSelectedCategory,
                    onSearchQueryChange = viewModel::updateSavedSearchQuery,
                    onCategoryChange = viewModel::updateSavedCategory,
                    onSpeakPhrase = viewModel::speakArbitraryText,
                    onDeletePhrase = viewModel::deleteSavedPhrase,
                    onOpenHistory = { navController.navigate(WhislyRoutes.HISTORY) }
                )
            }

            composable(WhislyRoutes.TEXT_MODE) {
                TextTranslationScreen(
                    uiState = uiState,
                    languages = languages,
                    onSelectSource = viewModel::selectSourceLanguage,
                    onSelectTarget = viewModel::selectTargetLanguage,
                    onSwapLanguages = viewModel::swapLanguages,
                    onInputChange = viewModel::updateTextModeInput,
                    onTranslateClick = viewModel::runTextModeTranslation,
                    onClearInput = viewModel::clearTextModeInput,
                    onListenResult = {
                        viewModel.speakArbitraryText(uiState.textModeOutput, uiState.targetLangCode)
                    },
                    onSavePhrase = { viewModel.saveCurrentTranslationToPhrases("General") },
                    onBack = { navController.popBackStack() }
                )
            }

            composable(WhislyRoutes.SETTINGS) {
                SettingsScreen(
                    settings = settings,
                    telemetry = viewModel.getRuntimeTelemetry(),
                    audioDiagnostics = audioDiagnostics,
                    usedModelStorageBytes = viewModel.repository.modelManager.getUsedModelStorageBytes(),
                    onToggleOfflineOnly = viewModel::setOfflineOnlyMode,
                    onSpeechSpeedChange = viewModel::setSpeechSpeed,
                    onToggleAutoPlay = viewModel::setAutoPlayTranslatedSpeech,
                    onToggleWifiOnly = viewModel::setDownloadOverWifiOnly,
                    onToggleCorrectionConsent = viewModel::setConsentToCorrectionSync,
                    onClearHistory = viewModel::clearAllHistory,
                    onOpenAboutWhisly = { navController.navigate(WhislyRoutes.ABOUT) },
                    onBack = { navController.popBackStack() }
                )
            }

            composable(WhislyRoutes.ABOUT) {
                AboutWhislyScreen(
                    onBack = {
                        if (!navController.popBackStack()) {
                            navController.navigate(WhislyRoutes.PROFILE)
                        }
                    }
                )
            }
        }
    }
}
