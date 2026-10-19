package com.thelightphone.wallet

import androidx.compose.runtime.Composable
import com.thelightphone.sdk.LightScreen
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.buildDatabase
import com.thelightphone.wallet.data.CardRepository
import com.thelightphone.wallet.data.WalletDatabase

class NameCardViewModel(repository: CardRepository) :
    NameEditorViewModel(repository, initialName = "", excludingId = CardRepository.NO_CARD)

/**
 * Final step of adding a card: give it a name. Backed by the SDK's
 * [LightTextInputEditor] via [NameEditorContent], same as [EditCardNameScreen] — see
 * that screen for why this owns a ViewModelStore (the keyboard VM is cached by editor
 * title, so each editor needs a distinct title and its own store to stay responsive).
 *
 * Returns the trimmed name via [goBack]. SAVE on a blank field does nothing, a name
 * another card already uses shows a message, and backing out returns no result.
 */
class NameCardScreen(
    sealedActivity: SealedLightActivity,
) : LightScreen<String, NameCardViewModel>(sealedActivity) {

    private val repository = CardRepository.getInstance {
        lightContext.buildDatabase(WalletDatabase::class.java, CardRepository.DATABASE_NAME)
    }

    override val viewModelClass: Class<NameCardViewModel>
        get() = NameCardViewModel::class.java

    override fun createViewModel() = NameCardViewModel(repository)

    @Composable
    override fun Content() {
        NameEditorContent(
            title = "Card name",
            viewModel = viewModel,
            onSaved = { goBack(it) },
            onBack = { goBack(null) },
        )
    }
}
