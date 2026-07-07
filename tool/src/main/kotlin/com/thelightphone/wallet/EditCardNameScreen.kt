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

class EditCardNameViewModel : LightViewModel<String>()

/**
 * Full-screen editor for a card's name, pre-filled with the current name. Backed
 * by the SDK's [LightTextInputEditor] (top bar + underlined input + LP3 keyboard),
 * the same component UI Demo > Text input uses.
 *
 * Returns the trimmed new name via [goBack]; returns null (delivering no result)
 * when the field is left blank or the user backs out, so an empty rename is
 * treated as a cancel and the card keeps its old name.
 *
 * This is a [LightScreen] (not a SimpleLightScreen) on purpose: LightTextInputEditor
 * caches its keyboard view model keyed by the editor title. A SimpleLightScreen has
 * no ViewModelStore of its own, so that VM would land in the long-lived Activity
 * store and be reused — still bound to the first edit's text field — on every later
 * edit, leaving the field unresponsive. Owning a ViewModelStore scopes the keyboard
 * VM to this screen and clears it on close, so each edit starts fresh.
 */
class EditCardNameScreen(
    sealedActivity: SealedLightActivity,
    private val initialName: String,
) : LightScreen<String, EditCardNameViewModel>(sealedActivity) {

    override val viewModelClass: Class<EditCardNameViewModel>
        get() = EditCardNameViewModel::class.java

    override fun createViewModel() = EditCardNameViewModel()

    @Composable
    override fun Content() {
        val textState = rememberTextFieldState(initialName)
        val themeColors by LightThemeController.colors.collectAsState()
        val keyboardOptionsFlow = rememberKeyboardOptions()

        LightTheme(colors = themeColors) {
            LightTextInputEditor(
                title = "Edit name",
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
