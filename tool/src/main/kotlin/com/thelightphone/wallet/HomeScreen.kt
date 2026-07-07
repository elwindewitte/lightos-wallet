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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.thelightphone.sdk.InitialScreen
import com.thelightphone.sdk.LightScreen
import com.thelightphone.sdk.LightViewModel
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightBottomBar
import com.thelightphone.sdk.ui.LightIcons
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * What the home screen is doing: browsing the list, searching within it, or
 * selecting cards to bulk-delete.
 */
enum class HomeMode { Browse, Search, Select }

class HomeScreenViewModel : LightViewModel<Unit>() {

    // Seed cards for now; manual add / local storage come later. Kept sorted
    // alphabetically (case-insensitive) so the browse list is easy to scan.
    private val _cards = MutableStateFlow(
        listOf("Gamma", "Shell", "Praxis", "Albert Heijn", "Jumbo", "Bouwmaat")
            .map { LoyaltyCard(it) }
            .sortedBy { it.name.lowercase() },
    )
    val cards: StateFlow<List<LoyaltyCard>> = _cards.asStateFlow()

    private val _mode = MutableStateFlow(HomeMode.Browse)
    val mode: StateFlow<HomeMode> = _mode.asStateFlow()

    // Card names in most-recently-opened order. Drives the default ordering of
    // search mode. In-memory for now; persistence lands with local storage.
    private val _recentNames = MutableStateFlow<List<String>>(emptyList())
    val recentNames: StateFlow<List<String>> = _recentNames.asStateFlow()

    // Names of cards ticked in Select mode. Keyed by name like the rest of the
    // tool (v1 seed names are unique); a stable id lands with local storage.
    private val _selectedNames = MutableStateFlow<Set<String>>(emptySet())
    val selectedNames: StateFlow<Set<String>> = _selectedNames.asStateFlow()

    fun enterSearch() {
        _mode.value = HomeMode.Search
    }

    fun exitSearch() {
        _mode.value = HomeMode.Browse
    }

    fun enterSelect() {
        _selectedNames.value = emptySet()
        _mode.value = HomeMode.Select
    }

    fun exitSelect() {
        _selectedNames.value = emptySet()
        _mode.value = HomeMode.Browse
    }

    /** Tick or untick [card] in Select mode. */
    fun toggleSelected(card: LoyaltyCard) {
        val name = card.name
        _selectedNames.value =
            if (name in _selectedNames.value) _selectedNames.value - name
            else _selectedNames.value + name
    }

    /** Delete every ticked card, then leave Select mode. No-op if none ticked. */
    fun deleteSelected() {
        val names = _selectedNames.value
        if (names.isEmpty()) return
        _cards.value = _cards.value.filterNot { it.name in names }
        _recentNames.value = _recentNames.value.filterNot { it in names }
        exitSelect()
    }

    /** Record that a card was opened so it bubbles to the top of recents. */
    fun recordOpened(card: LoyaltyCard) {
        _recentNames.value = listOf(card.name) + _recentNames.value.filterNot { it == card.name }
    }

    /**
     * Rename the card currently named [oldName] to [newName], keeping the browse
     * list sorted and recents pointing at the new name. No-op if the new name is
     * blank or unchanged. Cards are identified by name for now (v1 seed names are
     * unique); a stable id lands with local storage.
     */
    fun renameCard(oldName: String, newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isEmpty() || trimmed == oldName) return
        _cards.value = _cards.value
            .map { if (it.name == oldName) it.copy(name = trimmed) else it }
            .sortedBy { it.name.lowercase() }
        _recentNames.value = _recentNames.value.map { if (it == oldName) trimmed else it }
    }

    /** Remove the card named [name] from the list and from recents. */
    fun deleteCard(name: String) {
        _cards.value = _cards.value.filterNot { it.name == name }
        _recentNames.value = _recentNames.value.filterNot { it == name }
    }

    // Consume the hardware back button to leave search / select mode rather than
    // exiting the tool; from the plain browse list, let it close as usual.
    override fun onBackPressed(): Boolean =
        when (_mode.value) {
            HomeMode.Search -> {
                exitSearch()
                true
            }

            HomeMode.Select -> {
                exitSelect()
                true
            }

            HomeMode.Browse -> false
        }
}

@InitialScreen
class HomeScreen(sealedActivity: SealedLightActivity) :
    LightScreen<Unit, HomeScreenViewModel>(sealedActivity) {

    override val viewModelClass: Class<HomeScreenViewModel>
        get() = HomeScreenViewModel::class.java

    override fun createViewModel() = HomeScreenViewModel()

    @Composable
    override fun Content() {
        val mode by viewModel.mode.collectAsState()
        val cards by viewModel.cards.collectAsState()
        val recentNames by viewModel.recentNames.collectAsState()
        val selected by viewModel.selectedNames.collectAsState()
        val themeColors by LightThemeController.colors.collectAsState()

        // Confirm before bulk-deleting the ticked cards; delete on confirmation.
        val confirmDeleteSelected: () -> Unit = {
            val count = viewModel.selectedNames.value.size
            if (count > 0) {
                navigateTo(
                    screenFactory = { DeleteCardConfirmScreen(it, deleteCardsPrompt(count)) },
                    resultCallback = { confirmed -> if (confirmed) viewModel.deleteSelected() },
                )
            }
        }

        // Opening a card records recency and returns to browse, so coming back
        // from the detail view lands on the plain list.
        val openCard: (LoyaltyCard) -> Unit = { card ->
            viewModel.recordOpened(card)
            viewModel.exitSearch()
            navigateTo(
                screenFactory = {
                    CardDetailScreen(it, card, viewModel::renameCard, viewModel::deleteCard)
                },
            )
        }

        LightTheme(colors = themeColors) {
            when (mode) {
                HomeMode.Browse -> BrowseContent(
                    cards = cards,
                    onSearch = viewModel::enterSearch,
                    onEdit = viewModel::enterSelect,
                    onOpen = openCard,
                )

                HomeMode.Search -> SearchContent(
                    cards = cards,
                    recentNames = recentNames,
                    onClose = viewModel::exitSearch,
                    onOpen = openCard,
                )

                HomeMode.Select -> SelectContent(
                    cards = cards,
                    selected = selected,
                    onToggle = viewModel::toggleSelected,
                    onCancel = viewModel::exitSelect,
                    onDelete = confirmDeleteSelected,
                )
            }
        }
    }
}

@Composable
private fun BrowseContent(
    cards: List<LoyaltyCard>,
    onSearch: () -> Unit,
    onEdit: () -> Unit,
    onOpen: (LoyaltyCard) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(LightThemeTokens.colors.background),
    ) {
        LightTopBar(
            center = LightTopBarCenter.Text("Wallet"),
            modifier = Modifier.padding(bottom = 1f.gridUnitsAsDp()),
        )

        LightScrollView(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(start = 1f.gridUnitsAsDp()),
        ) {
            cards.forEach { card ->
                CardRow(name = card.name, onClick = { onOpen(card) })
            }
        }

        // Edit opens bulk-select-to-delete; ADD (the icon) is still a placeholder.
        LightBottomBar(
            items = listOf(
                LightBarButton.LightIcon(icon = LightIcons.SEARCH, onClick = onSearch),
                LightBarButton.Text(text = "EDIT", onClick = onEdit),
                LightBarButton.LightIcon(icon = LightIcons.ADD, onClick = {}),
            ),
        )
    }
}

/**
 * Select mode: tick cards with the leading checkboxes, then CANCEL out or DELETE
 * the selection (behind a confirmation). The title nudges you to pick something,
 * then reflects the running count.
 */
@Composable
private fun SelectContent(
    cards: List<LoyaltyCard>,
    selected: Set<String>,
    onToggle: (LoyaltyCard) -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
) {
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
                    checked = card.name in selected,
                    onToggle = { onToggle(card) },
                )
            }
        }

        LightBottomBar(
            items = listOf(
                LightBarButton.Text(text = "CANCEL", onClick = onCancel),
                LightBarButton.Text(text = "DELETE", onClick = onDelete),
            ),
        )
    }
}

/** A single tappable card name in a list. Shared by browse and search. */
@Composable
internal fun CardRow(name: String, onClick: () -> Unit) {
    LightText(
        text = name,
        variant = LightTextVariant.Copy,
        modifier = Modifier
            .fillMaxWidth()
            .lightClickable(onClick = onClick)
            .padding(vertical = 0.75f.gridUnitsAsDp()),
    )
}

/** Diameter of the Select-mode checkbox, ≈40px in design units. */
private const val CHECKBOX_UNITS = 1f

/** Ring thickness as a fraction of the checkbox diameter (matches the SDK asset). */
private const val CHECKBOX_STROKE_FRACTION = 0.1f

/**
 * A card row in Select mode: a leading checkbox, then the name. Tapping toggles.
 *
 * The checkbox is ≈40px and stays under the Copy line height, so the row matches
 * the plain browse rows' spacing.
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
 * The Select-mode checkbox: a filled disc when checked, a ring when not. Drawn
 * with Canvas rather than the SDK's SELECT_ON/OFF icons, whose circle sits flush
 * to its viewport so the stroke clips on the top-left edge at this size.
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
