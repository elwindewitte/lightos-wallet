package com.thelightphone.wallet

import androidx.compose.foundation.background
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.thelightphone.sdk.LightScreen
import com.thelightphone.sdk.LightViewModel
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.rememberKeyboardOptions
import com.thelightphone.sdk.ui.LightTextInputEditor
import com.thelightphone.sdk.ui.LightTheme
import com.thelightphone.sdk.ui.LightThemeController
import com.thelightphone.sdk.ui.LightThemeTokens

class EnterCodeViewModel : LightViewModel<ScannedCode>() {
    // Held here rather than in composition so the typed number is still there when
    // the user comes back from the type picker.
    val textState = TextFieldState()
}

/**
 * Manual-entry step: type the card's code value. Reached from [ScanCardScreen]'s
 * "Enter manually" fallback. NEXT pushes [PickCodeTypeScreen] on top (this screen
 * stays underneath, so Back from the type picker returns here with the number kept),
 * which in turn pushes the name step. The finished card bubbles back up via [goBack].
 *
 * Like [EditCardNameScreen]/[NameCardScreen] this is a [LightScreen] with a distinct
 * editor title so the SDK's title-keyed keyboard VM doesn't collide with the other
 * editors. NEXT with nothing typed does nothing; backing out cancels manual entry.
 */
class EnterCodeScreen(
    sealedActivity: SealedLightActivity,
) : LightScreen<ScannedCode, EnterCodeViewModel>(sealedActivity) {

    override val viewModelClass: Class<EnterCodeViewModel>
        get() = EnterCodeViewModel::class.java

    override fun createViewModel() = EnterCodeViewModel()

    @Composable
    override fun Content() {
        val themeColors by LightThemeController.colors.collectAsState()
        val keyboardOptionsFlow = rememberKeyboardOptions()

        LightTheme(colors = themeColors) {
            LightTextInputEditor(
                title = "Card number",
                state = viewModel.textState,
                keyboardOptionsFlow = keyboardOptionsFlow,
                submitLabel = "NEXT",
                onSubmit = { result ->
                    val code = result.toString().trim()
                    if (code.isNotEmpty()) {
                        navigateTo(
                            screenFactory = { PickCodeTypeScreen(it, code) },
                            resultCallback = { card -> goBack(card) },
                        )
                    }
                },
                onBack = { goBack(null) },
                modifier = Modifier.background(LightThemeTokens.colors.background),
            )
        }
    }
}
