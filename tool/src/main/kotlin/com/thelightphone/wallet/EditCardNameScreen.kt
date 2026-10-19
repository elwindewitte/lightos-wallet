package com.thelightphone.wallet

import androidx.compose.runtime.Composable
import com.thelightphone.sdk.LightScreen
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.buildDatabase
import com.thelightphone.wallet.data.CardRepository
import com.thelightphone.wallet.data.WalletDatabase

class EditCardNameViewModel(repository: CardRepository, initialName: String, cardId: Long) :
    NameEditorViewModel(repository, initialName, excludingId = cardId)

/**
 * Full-screen editor for a card's name, pre-filled with the current name. Backed
 * by the SDK's [LightTextInputEditor] via [NameEditorContent], which also blocks a
 * name another card already uses (keeping this card's own name, or changing only its
 * capitals, is fine).
 *
 * Returns the trimmed new name via [goBack]; SAVE on a blank field does nothing, and
 * backing out returns no result, so the card keeps its old name.
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
    private val cardId: Long,
    private val initialName: String,
) : LightScreen<String, EditCardNameViewModel>(sealedActivity) {

    private val repository = CardRepository.getInstance {
        lightContext.buildDatabase(WalletDatabase::class.java, CardRepository.DATABASE_NAME)
    }

    override val viewModelClass: Class<EditCardNameViewModel>
        get() = EditCardNameViewModel::class.java

    override fun createViewModel() = EditCardNameViewModel(repository, initialName, cardId)

    @Composable
    override fun Content() {
        NameEditorContent(
            title = "Rename",
            viewModel = viewModel,
            onSaved = { goBack(it) },
            onBack = { goBack(null) },
        )
    }
}
