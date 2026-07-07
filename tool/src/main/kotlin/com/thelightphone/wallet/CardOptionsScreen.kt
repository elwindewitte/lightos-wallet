package com.thelightphone.wallet

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.SimpleLightScreen
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightIcons
import com.thelightphone.sdk.ui.LightScrollView
import com.thelightphone.sdk.ui.LightTheme
import com.thelightphone.sdk.ui.LightThemeController
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.LightTopBar
import com.thelightphone.sdk.ui.LightTopBarCenter
import com.thelightphone.sdk.ui.gridUnitsAsDp

/** The outcome of the card options menu, bubbled up to the detail screen. */
sealed interface CardOption {
    data class Rename(val newName: String) : CardOption
    object Delete : CardOption
}

/**
 * The "more options" step reached from the detail screen's ⋯ button. Offers the
 * two things you can do to a card — rename or delete — each on its own row.
 *
 * Pure UI: it drives the edit / delete-confirmation sub-screens but doesn't touch
 * storage. The chosen outcome is returned to the detail screen via [goBack], which
 * owns applying it. Backing out (hardware or ⟵ button) returns nothing.
 */
class CardOptionsScreen(
    sealedActivity: SealedLightActivity,
    private val cardName: String,
) : SimpleLightScreen<CardOption>(sealedActivity) {

    @Composable
    override fun Content() {
        val themeColors by LightThemeController.colors.collectAsState()

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
                    center = LightTopBarCenter.Text(cardName),
                    modifier = Modifier.padding(bottom = 1f.gridUnitsAsDp()),
                )

                LightScrollView(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(start = 1f.gridUnitsAsDp()),
                ) {
                    CardRow(
                        name = "Edit",
                        onClick = {
                            navigateTo(
                                screenFactory = { EditCardNameScreen(it, cardName) },
                                resultCallback = { newName -> goBack(CardOption.Rename(newName)) },
                            )
                        },
                    )
                    CardRow(
                        name = "Delete",
                        onClick = {
                            navigateTo(
                                screenFactory = { DeleteCardConfirmScreen(it, cardName) },
                                resultCallback = { confirmed -> if (confirmed) goBack(CardOption.Delete) },
                            )
                        },
                    )
                }
            }
        }
    }
}
