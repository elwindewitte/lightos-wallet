package com.thelightphone.wallet

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.thelightphone.lp3Keyboard.ui.LayoutOptions
import com.thelightphone.lp3Keyboard.ui.viewmodel.EnQwertyLp3KeyboardViewModel
import com.thelightphone.lp3Keyboard.ui.viewmodel.Lp3KeyboardViewModel
import com.thelightphone.sdk.rememberKeyboardOptions
import com.thelightphone.sdk.ui.LightIcon
import com.thelightphone.sdk.ui.LightIcons
import com.thelightphone.sdk.ui.LightScrollView
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.designVerticalPxToSp
import com.thelightphone.sdk.ui.gridUnitsAsDp
import com.thelightphone.sdk.ui.keyboard.LightEmbeddedLp3Keyboard
import com.thelightphone.sdk.ui.lightClickable

private const val SEARCH_BAR_HEIGHT_UNITS = 3f
private const val SEARCH_ICON_SIZE_UNITS = 2f
private const val CURSOR_WIDTH_DP = 2

/**
 * Search mode: a left-aligned query field with a close button in the top bar, a
 * live-filtered card list, and the embedded LP3 keyboard pinned to the bottom.
 *
 * With no query, cards are ordered most-recently-opened first (then the rest,
 * alphabetically). As soon as the user types, the list filters to matching names.
 */
@Composable
fun SearchContent(
    cards: List<LoyaltyCard>,
    recentIds: List<Long>,
    onClose: () -> Unit,
    onOpen: (LoyaltyCard) -> Unit,
) {
    val textState = rememberTextFieldState()
    val query = textState.text.toString().trim()

    val keyboardOptions = rememberKeyboardOptions()
    val keyboard: Lp3KeyboardViewModel<*> = remember(textState, keyboardOptions) {
        EnQwertyLp3KeyboardViewModel<Unit>(
            SearchKeyboardCallback(textState),
            keyboardOptionsFlow = keyboardOptions,
            // Close button only on overlay layouts (emoji, extended chars), as in the
            // SDK's own text editor.
            optionsForLayout = { LayoutOptions(displayCloseButton = !it.isRootLayout) },
        )
    }

    // Recents (opened cards, most-recent first), then the rest alphabetically.
    val recentCards = remember(cards, recentIds) {
        val byId = cards.associateBy { it.id }
        recentIds.mapNotNull { byId[it] }
    }
    val allOrdered = remember(cards, recentCards) {
        val recentSet = recentCards.map { it.id }.toSet()
        recentCards + cards.filter { it.id !in recentSet }
    }
    // Empty query shows only recents; typing searches across every card.
    val visible = if (query.isEmpty()) {
        recentCards
    } else {
        allOrdered.filter { it.name.contains(query, ignoreCase = true) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(LightThemeTokens.colors.background),
    ) {
        SearchTopBar(state = textState, onClose = onClose)

        Spacer(modifier = Modifier.height(1f.gridUnitsAsDp()))

        LightScrollView(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(start = 1f.gridUnitsAsDp()),
        ) {
            if (visible.isEmpty()) {
                LightText(
                    text = if (query.isEmpty()) "Type to search your cards" else "No cards found",
                    variant = LightTextVariant.Copy,
                    lighten = true,
                    modifier = Modifier.padding(vertical = 0.75f.gridUnitsAsDp()),
                )
            } else {
                visible.forEach { card ->
                    CardRow(name = card.name, onClick = { onOpen(card) })
                }
            }
        }

        LightEmbeddedLp3Keyboard(viewModel = keyboard)
    }
}

@Composable
private fun SearchTopBar(state: TextFieldState, onClose: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = SEARCH_BAR_HEIGHT_UNITS.gridUnitsAsDp())
            .padding(horizontal = 1f.gridUnitsAsDp()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SearchInputField(state = state, modifier = Modifier.weight(1f))

        Spacer(modifier = Modifier.width(1f.gridUnitsAsDp()))

        LightIcon(
            icon = LightIcons.CLOSE,
            size = SEARCH_ICON_SIZE_UNITS,
            contentDescription = "Close search",
            modifier = Modifier.lightClickable(onClick = onClose),
        )
    }
}

/**
 * The query with a caret and (once you type) an underline, mirroring the SDK's
 * full-screen text editor used in UI Demo > Text input. There's no placeholder:
 * an empty field just shows the caret.
 */
@Composable
private fun SearchInputField(state: TextFieldState, modifier: Modifier = Modifier) {
    val colors = LightThemeTokens.colors
    val style = searchInputStyle()
    var textLayout by remember { mutableStateOf<TextLayoutResult?>(null) }

    Box(modifier = modifier) {
        BasicText(
            text = state.text.toString(),
            style = style,
            maxLines = 1,
            softWrap = false,
            onTextLayout = { textLayout = it },
            modifier = Modifier.fillMaxWidth(),
        )
        textLayout?.let { layout ->
            val cursorPos = state.selection.min.coerceIn(0, layout.layoutInput.text.length)
            val rect = layout.getCursorRect(cursorPos)
            Box(
                modifier = Modifier
                    .offset { IntOffset(rect.left.toInt(), rect.top.toInt()) }
                    .width(CURSOR_WIDTH_DP.dp)
                    .height(with(LocalDensity.current) { rect.height.toDp() })
                    .background(colors.content),
            )
        }
    }
}

/**
 * Underlined input style sized to match the top bar (the `Fine` text variant).
 * Rebuilt here because the SDK's own style helper is internal;
 * [designVerticalPxToSp] is the public equivalent of its screen scaling.
 */
@Composable
private fun searchInputStyle(): TextStyle {
    val base = LightThemeTokens.typography.fine
    return base.copy(
        fontSize = base.fontSize.value.designVerticalPxToSp(),
        lineHeight = base.lineHeight.value.designVerticalPxToSp(),
        letterSpacing = base.letterSpacing.value.designVerticalPxToSp(),
        color = LightThemeTokens.colors.content,
        textDecoration = TextDecoration.Underline,
    )
}
