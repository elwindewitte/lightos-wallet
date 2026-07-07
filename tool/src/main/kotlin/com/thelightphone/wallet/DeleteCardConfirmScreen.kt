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
import androidx.compose.ui.text.style.TextAlign
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.SimpleLightScreen
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightBottomBar
import com.thelightphone.sdk.ui.LightIcons
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.LightTheme
import com.thelightphone.sdk.ui.LightThemeController
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.LightTopBar
import com.thelightphone.sdk.ui.LightTopBarCenter
import com.thelightphone.sdk.ui.gridUnitsAsDp

/** Confirmation question for deleting a single named card. */
fun deleteCardPrompt(name: String): String = "Delete “$name”?"

/** Confirmation question for bulk-deleting [count] cards. */
fun deleteCardsPrompt(count: Int): String =
    if (count == 1) "Delete 1 card?" else "Delete $count cards?"

/**
 * Confirmation step for deleting one or more cards. The caller supplies the
 * [prompt] question (see [deleteCardPrompt] / [deleteCardsPrompt]); this screen
 * appends the irreversibility warning and the CANCEL / DELETE choice.
 *
 * Returns true only when the user taps DELETE; backing out (CANCEL, the ⟵ button,
 * or hardware back) returns false so nothing is deleted.
 */
class DeleteCardConfirmScreen(
    sealedActivity: SealedLightActivity,
    private val prompt: String,
) : SimpleLightScreen<Boolean>(sealedActivity) {

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
                        onClick = { goBack(false) },
                    ),
                    center = LightTopBarCenter.Text("Delete"),
                    modifier = Modifier.padding(bottom = 1f.gridUnitsAsDp()),
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 1f.gridUnitsAsDp()),
                    contentAlignment = Alignment.Center,
                ) {
                    LightText(
                        text = "$prompt\nThis can’t be undone.",
                        variant = LightTextVariant.Copy,
                        align = TextAlign.Center,
                    )
                }

                LightBottomBar(
                    items = listOf(
                        LightBarButton.Text(text = "CANCEL", onClick = { goBack(false) }),
                        LightBarButton.Text(text = "DELETE", onClick = { goBack(true) }),
                    ),
                )
            }
        }
    }
}
