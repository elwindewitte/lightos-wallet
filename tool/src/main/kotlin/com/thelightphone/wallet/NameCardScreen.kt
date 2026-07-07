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

class NameCardViewModel : LightViewModel<String>()

/**
 * Final step of adding a card: give it a name. Backed by the SDK's
 * [LightTextInputEditor] (top bar + underlined input + LP3 keyboard), same as
 * [EditCardNameScreen] — see that screen for why this owns a ViewModelStore (the
 * keyboard VM is cached by editor title, so each editor needs a distinct title and
 * its own store to stay responsive).
 *
 * Returns the trimmed name via [goBack]; returns null (no result) when left blank or
 * backed out, so an empty name cancels the add and nothing is saved.
 */
class NameCardScreen(
    sealedActivity: SealedLightActivity,
) : LightScreen<String, NameCardViewModel>(sealedActivity) {

    override val viewModelClass: Class<NameCardViewModel>
        get() = NameCardViewModel::class.java

    override fun createViewModel() = NameCardViewModel()

    @Composable
    override fun Content() {
        val textState = rememberTextFieldState()
        val themeColors by LightThemeController.colors.collectAsState()
        val keyboardOptionsFlow = rememberKeyboardOptions()

        LightTheme(colors = themeColors) {
            LightTextInputEditor(
                title = "Card name",
                state = textState,
                keyboardOptionsFlow = keyboardOptionsFlow,
                submitLabel = "SAVE",
                onSubmit = { result ->
                    val name = result.toString().trim()
                    goBack(if (name.isEmpty()) null else name)
                },
                onBack = { goBack(null) },
                modifier = Modifier.background(LightThemeTokens.colors.background),
            )
        }
    }
}
