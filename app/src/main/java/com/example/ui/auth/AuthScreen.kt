package com.example.ui.auth

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.R
import com.example.core.CountryDialCode
import com.example.core.findActivity
import com.example.data.model.OtpChannel
import com.example.data.repository.AuthRepository
import com.example.ui.components.DlLanguageSelector
import com.example.ui.theme.DlSpacing
import com.example.ui.theme.LocalDlTokens

enum class AuthRoute {
    LOGIN,
    REGISTER
}

@Composable
fun AuthScreen(
    authRepository: AuthRepository,
    onAuthSuccess: () -> Unit,
    initialRoute: AuthRoute = AuthRoute.LOGIN,
    externalNotice: String? = null,
    appLanguage: String = "en",
    onSelectLanguage: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val activity = context.findActivity()

    val loginViewModel: LoginViewModel = viewModel(
        factory = viewModelFactory {
            initializer { LoginViewModel(authRepository) }
        }
    )
    val registerViewModel: RegisterViewModel = viewModel(
        factory = viewModelFactory {
            initializer { RegisterViewModel(authRepository) }
        }
    )

    val loginState by loginViewModel.uiState.collectAsStateWithLifecycle()
    val registerState by registerViewModel.uiState.collectAsStateWithLifecycle()

    var currentRoute by remember { mutableStateOf(initialRoute) }

    BackHandler(enabled = currentRoute == AuthRoute.REGISTER) {
        registerViewModel.clearErrors()
        currentRoute = AuthRoute.LOGIN
    }

    LaunchedEffect(Unit) {
        loginViewModel.checkAutoSignIn(context, onAuthSuccess)
    }

    AuthPortalContainer(
        currentRoute = currentRoute,
        onRouteChange = { next ->
            loginViewModel.clearError()
            registerViewModel.clearErrors()
            currentRoute = next
        },
        loginState = loginState,
        registerState = registerState,
        onGoogleLoginClick = { loginViewModel.signInWithGoogle(activity, onAuthSuccess, context) },
        onDisplayNameChanged = registerViewModel::onDisplayNameChanged,
        onUsernameChanged = registerViewModel::onUsernameChanged,
        onBioChanged = registerViewModel::onBioChanged,
        onAvatarColorSelected = registerViewModel::onAvatarColorSelected,
        onDiscoverableChanged = registerViewModel::onDiscoverableChanged,
        onSubmitRegister = { registerViewModel.registerWithGoogle(activity, onAuthSuccess, context) },
        externalNotice = externalNotice,
        appLanguage = appLanguage,
        onSelectLanguage = onSelectLanguage,
        onSelectCountry = loginViewModel::selectCountry,
        onPhoneInputChanged = loginViewModel::onPhoneInputChanged,
        onOtpCodeChanged = loginViewModel::onOtpCodeChanged,
        onSelectOtpChannel = loginViewModel::onSelectOtpChannel,
        onWhatsAppOptInChanged = loginViewModel::onWhatsAppOptInChanged,
        onSendOtpClick = { loginViewModel.sendPhoneOtp(activity, onAuthSuccess, isResend = false) },
        onResendOtpClick = { loginViewModel.sendPhoneOtp(activity, onAuthSuccess, isResend = true) },
        onVerifyOtpClick = { loginViewModel.verifyOtpCode(context, onAuthSuccess) },
        onEditPhoneClick = loginViewModel::editPhoneNumber
    )
}

@Composable
internal fun AuthPortalContainer(
    currentRoute: AuthRoute,
    onRouteChange: (AuthRoute) -> Unit,
    loginState: LoginUiState,
    registerState: RegisterUiState,
    onGoogleLoginClick: () -> Unit,
    onDisplayNameChanged: (String) -> Unit,
    onUsernameChanged: (String) -> Unit,
    onBioChanged: (String) -> Unit,
    onAvatarColorSelected: (String) -> Unit,
    onDiscoverableChanged: (Boolean) -> Unit,
    onSubmitRegister: () -> Unit,
    externalNotice: String? = null,
    appLanguage: String = "en",
    onSelectLanguage: (String) -> Unit = {},
    onSelectCountry: (CountryDialCode) -> Unit = {},
    onPhoneInputChanged: (String) -> Unit = {},
    onOtpCodeChanged: (String) -> Unit = {},
    onSelectOtpChannel: (OtpChannel) -> Unit = {},
    onWhatsAppOptInChanged: (Boolean) -> Unit = {},
    onSendOtpClick: () -> Unit = {},
    onResendOtpClick: () -> Unit = {},
    onVerifyOtpClick: () -> Unit = {},
    onEditPhoneClick: () -> Unit = {}
) {
    var statusNotice by remember { mutableStateOf<String?>(null) }
    val tokens = LocalDlTokens.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 520.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            DlLanguageSelector(
                selectedLanguage = appLanguage,
                onSelectLanguage = onSelectLanguage
            )
            Spacer(modifier = Modifier.height(DlSpacing.md))

            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("auth_mode_switcher")
            ) {
                SegmentedButton(
                    selected = currentRoute == AuthRoute.LOGIN,
                    onClick = { onRouteChange(AuthRoute.LOGIN) },
                    shape = SegmentedButtonDefaults.itemShape(
                        index = 0,
                        count = 2,
                        baseShape = RoundedCornerShape(12.dp)
                    ),
                    colors = SegmentedButtonDefaults.colors(
                        activeContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        activeContentColor = MaterialTheme.colorScheme.primary,
                        inactiveContainerColor = tokens.surfaceSubtle,
                        inactiveContentColor = tokens.textSecondary
                    ),
                    modifier = Modifier.testTag("auth_tab_login")
                ) {
                    Text(stringResource(R.string.auth_tab_login))
                }
                SegmentedButton(
                    selected = currentRoute == AuthRoute.REGISTER,
                    onClick = { onRouteChange(AuthRoute.REGISTER) },
                    shape = SegmentedButtonDefaults.itemShape(
                        index = 1,
                        count = 2,
                        baseShape = RoundedCornerShape(12.dp)
                    ),
                    colors = SegmentedButtonDefaults.colors(
                        activeContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        activeContentColor = MaterialTheme.colorScheme.primary,
                        inactiveContainerColor = tokens.surfaceSubtle,
                        inactiveContentColor = tokens.textSecondary
                    ),
                    modifier = Modifier.testTag("auth_tab_register")
                ) {
                    Text(stringResource(R.string.auth_tab_register))
                }
            }

            Spacer(modifier = Modifier.height(DlSpacing.base))

            val combinedNotice = statusNotice ?: externalNotice
            if (combinedNotice != null) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = DlSpacing.md)
                ) {
                    Text(
                        text = combinedNotice,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(DlSpacing.md)
                    )
                }
            }

            AnimatedContent(
                targetState = currentRoute,
                transitionSpec = {
                    if (tokens.reducedMotion) EnterTransition.None togetherWith ExitTransition.None
                    else fadeIn() togetherWith fadeOut()
                },
                label = "AuthRouteTransition"
            ) { route ->
                when (route) {
                    AuthRoute.LOGIN -> LoginScreen(
                        uiState = loginState,
                        onGoogleLoginClick = onGoogleLoginClick,
                        onNavigateToRegister = { onRouteChange(AuthRoute.REGISTER) },
                        onDeathLegionStatus = { statusNotice = it },
                        onSelectCountry = onSelectCountry,
                        onPhoneInputChanged = onPhoneInputChanged,
                        onOtpCodeChanged = onOtpCodeChanged,
                        onSelectOtpChannel = onSelectOtpChannel,
                        onWhatsAppOptInChanged = onWhatsAppOptInChanged,
                        onSendOtpClick = onSendOtpClick,
                        onResendOtpClick = onResendOtpClick,
                        onVerifyOtpClick = onVerifyOtpClick,
                        onEditPhoneClick = onEditPhoneClick
                    )
                    AuthRoute.REGISTER -> RegisterScreen(
                        uiState = registerState,
                        onDisplayNameChanged = onDisplayNameChanged,
                        onUsernameChanged = onUsernameChanged,
                        onBioChanged = onBioChanged,
                        onAvatarColorSelected = onAvatarColorSelected,
                        onDiscoverableChanged = onDiscoverableChanged,
                        onSubmitRegister = onSubmitRegister,
                        onNavigateToLogin = { onRouteChange(AuthRoute.LOGIN) }
                    )
                }
            }
        }
    }
}
