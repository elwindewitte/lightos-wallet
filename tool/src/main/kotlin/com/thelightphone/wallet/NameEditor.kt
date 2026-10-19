package com.thelightphone.wallet

import androidx.compose.foundation.background
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewModelScope
import com.thelightphone.sdk.LightViewModel
import com.thelightphone.sdk.rememberKeyboardOptions
import com.thelightphone.sdk.ui.LightFullscreenModal
import com.thelightphone.sdk.ui.LightTextInputEditor
import com.thelightphone.sdk.ui.LightTheme
import com.thelightphone.sdk.ui.LightThemeController
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.wallet.data.CardRepository
import kotlinx.coroutines.launch

/**
 * Shared state for the two card-name editors ([NameCardScreen], [EditCardNameScreen]):
 * the text (kept here so it survives the duplicate-name message replacing the
 * editor) and that message. Names must be unique, ignoring case and surrounding
 * spaces, so two cards never look the same in the list.
 */
abstract class NameEditorViewModel(
    private val repository: CardRepository,
    initialName: String,
    // The card being renamed, so keeping its own name isn't a clash.
    private val excludingId: Long,
) : LightViewModel<String>() {

    val textState = TextFieldState(initialName)

    var duplicateMessage by mutableStateOf<String?>(null)
        private set

    /** Checks [raw] and calls [onAccepted] with the trimmed name if it's free. */
    fun submit(raw: CharSequence, onAccepted: (String) -> Unit) {
        val name = raw.toString().trim()
        if (name.isEmpty()) return
        viewModelScope.launch {
            if (repository.isNameTaken(name, excludingId)) {
                duplicateMessage = "A card called “$name” already exists. Choose another name."
            } else {
                onAccepted(name)
            }
        }
    }

    fun dismissMessage() {
        duplicateMessage = null
    }
}

/**
 * The name editor itself: the SDK's [LightTextInputEditor], swapped for LightOS's
 * full-screen message while a duplicate name is being reported. ✕ returns to the
 * editor with the typed name kept.
 */
@Composable
fun NameEditorContent(
    title: String,
    viewModel: NameEditorViewModel,
    onSaved: (String) -> Unit,
    onBack: () -> Unit,
) {
    val themeColors by LightThemeController.colors.collectAsState()
    val keyboardOptionsFlow = rememberKeyboardOptions()

    LightTheme(colors = themeColors) {
        val message = viewModel.duplicateMessage
        if (message != null) {
            LightFullscreenModal(message = message, onClose = viewModel::dismissMessage)
            return@LightTheme
        }
        LightTextInputEditor(
            title = title,
            state = viewModel.textState,
            keyboardOptionsFlow = keyboardOptionsFlow,
            submitLabel = "SAVE",
            onSubmit = { viewModel.submit(it, onSaved) },
            onBack = onBack,
            modifier = Modifier.background(LightThemeTokens.colors.background),
        )
    }
}
