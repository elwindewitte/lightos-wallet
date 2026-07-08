package com.thelightphone.wallet

import androidx.compose.foundation.background
import androidx.compose.foundation.text.input.rememberTextFieldState
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

class EnterCodeViewModel : LightViewModel<String>()

/**
 * Manual-entry step: type the card's code value. Reached from [ScanCardScreen]'s
 * "Enter manually" fallback; followed by [PickCodeTypeScreen] to choose the symbology
 * and then the shared name step.
 *
 * Like [EditCardNameScreen]/[NameCardScreen] this is a [LightScreen] with a distinct
 * editor title so the SDK's title-keyed keyboard VM doesn't collide with the other
 * editors. Returns the trimmed value via [goBack]; a blank value (or backing out)
 * returns null, cancelling manual entry.
 */
class EnterCodeScreen(
    sealedActivity: SealedLightActivity,
) : LightScreen<String, EnterCodeViewModel>(sealedActivity) {

    override val viewModelClass: Class<EnterCodeViewModel>
        get() = EnterCodeViewModel::class.java

    override fun createViewModel() = EnterCodeViewModel()

    @Composable
    override fun Content() {
        val textState = rememberTextFieldState()
        val themeColors by LightThemeController.colors.collectAsState()
        val keyboardOptionsFlow = rememberKeyboardOptions()

        LightTheme(colors = themeColors) {
            LightTextInputEditor(
                title = "Card number",
                state = textState,
                keyboardOptionsFlow = keyboardOptionsFlow,
                submitLabel = "NEXT",
                onSubmit = { result ->
                    val code = result.toString().trim()
                    goBack(if (code.isEmpty()) null else code)
                },
                onBack = { goBack(null) },
                modifier = Modifier.background(LightThemeTokens.colors.background),
            )
        }
    }
}
