package com.thelightphone.wallet

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.thelightphone.sdk.LightScreen
import com.thelightphone.sdk.LightViewModel
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightBottomBar
import com.thelightphone.sdk.ui.LightIcons
import com.thelightphone.sdk.ui.LightTheme
import com.thelightphone.sdk.ui.LightThemeController
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.LightTopBar
import com.thelightphone.sdk.ui.LightTopBarCenter
import com.thelightphone.sdk.ui.gridUnitsAsDp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Holds the card's current display name so the header updates live when the name
 * is edited on this screen (before the change is reflected back in the list).
 */
class CardDetailViewModel(initialName: String) : LightViewModel<Unit>() {
    private val _name = MutableStateFlow(initialName)
    val name: StateFlow<String> = _name.asStateFlow()

    fun setName(newName: String) {
        _name.value = newName
    }
}

/**
 * Detail view for a single card. The header carries the card's name, the body
 * holds the full-screen scannable code, and a bottom bar offers the two things
 * you can do to a card — RENAME and DELETE — each navigating straight to
 * its own screen. One screen serves every card — the card is passed in via the
 * constructor.
 *
 * List mutations are pushed straight back via [onRename] / [onDelete] (keyed by
 * the card's stable id) rather than returned as results, so they survive the
 * hardware back button — which pops the screen without carrying a result.
 */
class CardDetailScreen(
    sealedActivity: SealedLightActivity,
    private val card: LoyaltyCard,
    private val onRename: (id: Long, newName: String) -> Unit,
    private val onDelete: (id: Long) -> Unit,
) : LightScreen<Unit, CardDetailViewModel>(sealedActivity) {

    override val viewModelClass: Class<CardDetailViewModel>
        get() = CardDetailViewModel::class.java

    override fun createViewModel() = CardDetailViewModel(card.name)

    @Composable
    override fun Content() {
        val themeColors by LightThemeController.colors.collectAsState()
        val name by viewModel.name.collectAsState()

        LightTheme(colors = themeColors) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(LightThemeTokens.colors.background),
            ) {
                LightTopBar(
                    leftButton = LightBarButton.LightIcon(
                        icon = LightIcons.BACK,
                        onClick = { goBack() },
                    ),
                    center = LightTopBarCenter.Text(name),
                    modifier = Modifier.padding(bottom = 1f.gridUnitsAsDp()),
                )

                // Full-bleed white code surface, top-aligned just below the header; the
                // theme background fills the space beneath it (see the mockups).
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    CardCodeSurface(code = card.code, codeType = card.codeType)
                }

                // RENAME renames in place (header updates live via the VM); DELETE
                // confirms, then removes the card and pops back to the list.
                LightBottomBar(
                    items = listOf(
                        LightBarButton.Text(
                            text = "RENAME",
                            onClick = {
                                navigateTo(
                                    screenFactory = { EditCardNameScreen(it, viewModel.name.value) },
                                    resultCallback = { newName ->
                                        onRename(card.id, newName)
                                        viewModel.setName(newName)
                                    },
                                )
                            },
                        ),
                        LightBarButton.Text(
                            text = "DELETE",
                            onClick = {
                                navigateTo(
                                    screenFactory = {
                                        DeleteCardConfirmScreen(it, deleteCardPrompt(viewModel.name.value))
                                    },
                                    resultCallback = { confirmed ->
                                        if (confirmed) {
                                            onDelete(card.id)
                                            goBack()
                                        }
                                    },
                                )
                            },
                        ),
                    ),
                )
            }
        }
    }
}
