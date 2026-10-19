package com.thelightphone.wallet

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.SimpleLightScreen
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightBottomBar
import com.thelightphone.sdk.ui.LightScrollView
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.LightTheme
import com.thelightphone.sdk.ui.LightThemeController
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.LightTopBar
import com.thelightphone.sdk.ui.LightTopBarCenter
import com.thelightphone.sdk.ui.gridUnitsAsDp
import com.thelightphone.sdk.ui.lightClickable

/**
 * Bulk delete: tick cards with the leading checkboxes, then CANCEL out or DELETE the
 * selection (behind [DeleteCardConfirmScreen]). The title nudges you to pick
 * something, then reflects the running count.
 *
 * A screen of its own (not a mode of [HomeScreen]) so the SDK's back stack handles
 * the hardware back key. Returns the ids to delete via [goBack]; CANCEL or back
 * returns nothing.
 */
class SelectCardsScreen(
    sealedActivity: SealedLightActivity,
    private val cards: List<LoyaltyCard>,
) : SimpleLightScreen<Set<Long>>(sealedActivity) {

    // Held on the screen (not in composition) so it survives the confirm screen
    // being pushed on top.
    private var selected by mutableStateOf<Set<Long>>(emptySet())

    private fun toggle(card: LoyaltyCard) {
        selected = if (card.id in selected) selected - card.id else selected + card.id
    }

    private fun confirmDelete() {
        val ids = selected
        if (ids.isEmpty()) return
        navigateTo(
            screenFactory = { DeleteCardConfirmScreen(it, deleteCardsPrompt(ids.size)) },
            resultCallback = { confirmed -> if (confirmed) goBack(ids) },
        )
    }

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
                    center = LightTopBarCenter.Text(
                        if (selected.isEmpty()) "Select to delete…" else "${selected.size} selected",
                    ),
                    modifier = Modifier.padding(bottom = 1f.gridUnitsAsDp()),
                )

                LightScrollView(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(start = 1f.gridUnitsAsDp()),
                ) {
                    cards.forEach { card ->
                        SelectableCardRow(
                            name = card.name,
                            checked = card.id in selected,
                            onToggle = { toggle(card) },
                        )
                    }
                }

                LightBottomBar(
                    items = listOf(
                        LightBarButton.Text(text = "CANCEL", onClick = { goBack() }),
                        LightBarButton.Text(text = "DELETE", onClick = ::confirmDelete),
                    ),
                )
            }
        }
    }
}

/** Diameter of the checkbox, ≈40px in design units. */
private const val CHECKBOX_UNITS = 1f

/** Ring thickness as a fraction of the checkbox diameter (matches the SDK asset). */
private const val CHECKBOX_STROKE_FRACTION = 0.1f

/**
 * A card row with a leading checkbox, then the name. Tapping toggles.
 *
 * The checkbox is ≈40px and stays under the Copy line height, so the row matches
 * the plain list rows' spacing.
 */
@Composable
private fun SelectableCardRow(name: String, checked: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .lightClickable(onClick = onToggle)
            .padding(vertical = 0.75f.gridUnitsAsDp()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CardCheckbox(checked = checked)
        Spacer(modifier = Modifier.width(1f.gridUnitsAsDp()))
        LightText(text = name, variant = LightTextVariant.Copy)
    }
}

/**
 * The checkbox: a filled disc when checked, a ring when not. Drawn with Canvas
 * rather than the SDK's SELECT_ON/OFF icons, whose circle sits flush to its
 * viewport so the stroke clips on the top-left edge at this size.
 */
@Composable
private fun CardCheckbox(checked: Boolean) {
    val color = LightThemeTokens.colors.content
    val description = if (checked) "Selected" else "Not selected"
    Canvas(
        modifier = Modifier
            .size(CHECKBOX_UNITS.gridUnitsAsDp())
            .semantics { contentDescription = description },
    ) {
        val diameter = size.minDimension
        val stroke = diameter * CHECKBOX_STROKE_FRACTION
        if (checked) {
            drawCircle(color = color, radius = diameter / 2f, center = center)
        } else {
            // Inset by half the stroke so the ring's outer edge stays in bounds.
            drawCircle(
                color = color,
                radius = (diameter - stroke) / 2f,
                center = center,
                style = Stroke(width = stroke),
            )
        }
    }
}
