package com.example

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.core.DeathLegionOAuthClient
import com.example.core.LocaleHelper
import com.example.core.MultiAccountManager
import com.example.core.configureAppCheck
import com.example.core.findActivity
import com.example.data.model.UiState
import com.example.data.repository.AppearancePrefs
import com.example.data.repository.AuthRepository
import com.example.data.repository.ChatRepository
import com.example.data.repository.DlAiRepository
import com.example.data.repository.GroupForumRepository
import com.example.data.repository.LinkedDevicesRepository
import com.example.data.repository.PreferencesRepository
import com.example.data.repository.SupportRepository
import com.example.data.repository.TrustSafetyRepository
import com.example.data.repository.UserWorkspaceRepository
import com.example.data.repository.authStateFlow
import com.example.ui.auth.AuthScreen
import com.example.ui.screens.MainShellScreen
import com.example.ui.theme.DlChatTheme
import com.example.ui.viewmodel.DlChatViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch

class MainActivity : FragmentActivity() {
    private val pendingDeepLinkState = mutableStateOf<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Mandatory: Configure App Check with launch intent before setContent or ViewModels
        configureAppCheck(this, intent)
        handleIncomingIntent(intent)
        enableEdgeToEdge()

        setContent {
            val baseContext = LocalContext.current
            val prefsRepo = remember { PreferencesRepository(baseContext.applicationContext) }
            val appearance by prefsRepo.appearanceFlow.collectAsStateWithLifecycle(
                initialValue = AppearancePrefs()
            )
            val pendingDeepLink by pendingDeepLinkState
            val localizedContext = remember(baseContext, appearance.appLanguage) {
                LocaleHelper.localizedContext(baseContext, appearance.appLanguage)
            }

            CompositionLocalProvider(
                LocalContext provides localizedContext,
                LocalConfiguration provides localizedContext.resources.configuration
            ) {
                DlChatTheme(
                    themeMode = appearance.themeMode,
                    accentColorHex = appearance.accentColorHex,
                    fontScale = appearance.fontScale,
                    reducedMotion = appearance.reducedMotion,
                    compactDensity = appearance.compactDensity,
                    appLanguage = appearance.appLanguage
                ) {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        AppNavigation(
                            prefsRepo = prefsRepo,
                            appLanguage = appearance.appLanguage,
                            pendingDeepLink = pendingDeepLink,
                            onConsumeDeepLink = { pendingDeepLinkState.value = null }
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        val data = intent?.data ?: return
        if (data.scheme == "dlchat") {
            pendingDeepLinkState.value = data
        }
    }
}

@Composable
fun AppNavigation(
    prefsRepo: PreferencesRepository,
    appLanguage: String = "en",
    pendingDeepLink: Uri? = null,
    onConsumeDeepLink: () -> Unit = {}
) {
    val context = LocalContext.current
    val activity = context.findActivity()
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val auth = Firebase.auth
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)

    val db = remember {
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    }
    val authRepository = remember(db) { AuthRepository(db, auth) }

    val user = currentUser
    if (user == null) {
        LaunchedEffect(activity) {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
        val notice = if (pendingDeepLink != null && DeathLegionOAuthClient.parseCallbackUri(pendingDeepLink) != null) {
            stringResource(R.string.auth_legion_need_signin_first)
        } else null
        AuthScreen(
            authRepository = authRepository,
            onAuthSuccess = { /* AuthStateListener updates currentUser reactively */ },
            externalNotice = notice,
            appLanguage = appLanguage,
            onSelectLanguage = { code ->
                scope.launch {
                    prefsRepo.saveLocalUiPreferences(appLanguage = LocaleHelper.normalizeCode(code))
                }
            }
        )
    } else {
        val uid = user.uid
        key(uid) {
            val dlViewModel: DlChatViewModel = viewModel(
                key = uid,
                factory = viewModelFactory {
                    initializer {
                        val app = checkNotNull(this[APPLICATION_KEY])
                        val databaseId = app.getString(R.string.firestore_database_id)
                        val firestore = FirebaseFirestore.getInstance(databaseId)
                        DlChatViewModel(
                            authRepository = AuthRepository(firestore, auth),
                            chatRepository = ChatRepository(firestore, auth),
                            workspaceRepository = UserWorkspaceRepository(firestore, auth),
                            preferencesRepository = prefsRepo,
                            aiRepository = DlAiRepository(),
                            currentUserId = uid,
                            currentUserEmail = user.email,
                            isEmailVerified = user.isEmailVerified,
                            appContext = app.applicationContext,
                            linkedDevicesRepository = LinkedDevicesRepository(firestore, auth),
                            groupForumRepository = GroupForumRepository(firestore, auth),
                            trustSafetyRepository = TrustSafetyRepository(firestore, auth),
                            supportRepository = SupportRepository(firestore, auth)
                        )
                    }
                }
            )

            val settingsState by dlViewModel.settingsState.collectAsStateWithLifecycle()
            val myProfileState by dlViewModel.myProfileState.collectAsStateWithLifecycle()
            val currentSettings = (settingsState as? UiState.Success)?.data
            val latestSettings by rememberUpdatedState(currentSettings)

            // Record isolated multi-account slot on sign-in/profile update
            val profile = (myProfileState as? UiState.Success)?.data
            LaunchedEffect(uid, profile?.username, profile?.displayName) {
                if (profile != null) {
                    MultiAccountManager.recordAccount(
                        context = context.applicationContext,
                        uid = uid,
                        email = user.email.orEmpty(),
                        displayName = profile.displayName,
                        username = profile.username
                    )
                }
            }

            // Dynamic FLAG_SECURE screenshot & screen-recording protection
            val secureFlag = currentSettings?.screenshotProtectionEnabled == true
            LaunchedEffect(secureFlag, activity) {
                if (secureFlag) {
                    activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                } else {
                    activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
            }

            // Lifecycle observer for PBKDF2 / Biometric Auto-Lock
            DisposableEffect(lifecycleOwner, dlViewModel) {
                val observer = LifecycleEventObserver { _, event ->
                    when (event) {
                        Lifecycle.Event.ON_STOP -> dlViewModel.v3.recordBackgroundForAutoLock()
                        Lifecycle.Event.ON_START -> dlViewModel.v3.checkAutoLockOnForeground(latestSettings)
                        else -> Unit
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose {
                    lifecycleOwner.lifecycle.removeObserver(observer)
                }
            }

            // Handle Death Legion OAuth deep link callback; pass pair/join URIs to MainShellScreen
            val isOAuthUri = pendingDeepLink != null &&
                DeathLegionOAuthClient.parseCallbackUri(pendingDeepLink) != null
            LaunchedEffect(uid, pendingDeepLink) {
                if (pendingDeepLink != null && isOAuthUri) {
                    dlViewModel.v3.handleDeathLegionOAuthCallback(context, pendingDeepLink)
                    onConsumeDeepLink()
                }
            }

            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
            ) {
                MainShellScreen(
                    viewModel = dlViewModel,
                    prefsRepo = prefsRepo,
                    incomingQrOrInvite = if (!isOAuthUri) pendingDeepLink?.toString() else null,
                    onConsumeIncomingUri = onConsumeDeepLink
                )
            }
        }
    }
}
