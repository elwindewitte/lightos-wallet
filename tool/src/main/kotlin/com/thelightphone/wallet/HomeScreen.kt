package com.thelightphone.wallet

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.viewModelScope
import com.thelightphone.sdk.InitialScreen
import com.thelightphone.sdk.LightScreen
import com.thelightphone.sdk.LightViewModel
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.buildDatabase
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
import com.thelightphone.wallet.code.CodeType
import com.thelightphone.wallet.data.CardRepository
import com.thelightphone.wallet.data.WalletDatabase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * What the home screen is doing: browsing the list, searching within it, or
 * selecting cards to bulk-delete.
 */
enum class HomeMode { Browse, Search, Select }

class HomeScreenViewModel(private val repository: CardRepository) : LightViewModel<Unit>() {

    // The persisted card list, ordered by name in the query. The Room Flow re-emits
    // on every add / rename / delete, so mutations below just write and let the list
    // refresh itself — there is no in-memory copy to keep in sync.
    val cards: StateFlow<List<LoyaltyCard>> =
        repository.observeCards()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _mode = MutableStateFlow(HomeMode.Browse)
    val mode: StateFlow<HomeMode> = _mode.asStateFlow()

    // Card ids in most-recently-opened order. Drives the default ordering of search
    // mode. In-memory (per session) — recency isn't worth persisting for v1.
    private val _recentIds = MutableStateFlow<List<Long>>(emptyList())
    val recentIds: StateFlow<List<Long>> = _recentIds.asStateFlow()

    // Ids of cards ticked in Select mode.
    private val _selectedIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedIds: StateFlow<Set<Long>> = _selectedIds.asStateFlow()

    fun enterSearch() {
        _mode.value = HomeMode.Search
    }

    fun exitSearch() {
        _mode.value = HomeMode.Browse
    }

    fun enterSelect() {
        _selectedIds.value = emptySet()
        _mode.value = HomeMode.Select
    }

    fun exitSelect() {
        _selectedIds.value = emptySet()
        _mode.value = HomeMode.Browse
    }

    /** Tick or untick [card] in Select mode. */
    fun toggleSelected(card: LoyaltyCard) {
        val id = card.id
        _selectedIds.value =
            if (id in _selectedIds.value) _selectedIds.value - id
            else _selectedIds.value + id
    }

    /** Delete every ticked card, then leave Select mode. No-op if none ticked. */
    fun deleteSelected() {
        val ids = _selectedIds.value
        if (ids.isEmpty()) return
        viewModelScope.launch { repository.deleteCards(ids.toList()) }
        _recentIds.value = _recentIds.value.filterNot { it in ids }
        exitSelect()
    }

    /** Record that a card was opened so it bubbles to the top of recents. */
    fun recordOpened(card: LoyaltyCard) {
        _recentIds.value = listOf(card.id) + _recentIds.value.filterNot { it == card.id }
    }

    /** Add a scanned or manually entered card to storage. Ignores a blank name. */
    fun addCard(name: String, code: String, codeType: CodeType) {
        if (name.isBlank()) return
        viewModelScope.launch { repository.addCard(name, code, codeType) }
    }

    /**
     * Rename the card with [id] to [newName]. The Room Flow re-emits the re-sorted
     * list; recents are keyed by id so they need no update. A blank name is ignored
     * by the repository.
     */
    fun renameCard(id: Long, newName: String) {
        viewModelScope.launch { repository.renameCard(id, newName) }
    }

    /** Remove the card with [id] from storage and from recents. */
    fun deleteCard(id: Long) {
        viewModelScope.launch { repository.deleteCards(listOf(id)) }
        _recentIds.value = _recentIds.value.filterNot { it == id }
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

    // Built once and shared process-wide; every screen in the add / edit flow that
    // touches storage goes through this same repository.
    private val repository = CardRepository.getInstance {
        lightContext.buildDatabase(WalletDatabase::class.java, CardRepository.DATABASE_NAME)
    }

    override val viewModelClass: Class<HomeScreenViewModel>
        get() = HomeScreenViewModel::class.java

    override fun createViewModel() = HomeScreenViewModel(repository)

    @Composable
    override fun Content() {
        val mode by viewModel.mode.collectAsState()
        val cards by viewModel.cards.collectAsState()
        val recentIds by viewModel.recentIds.collectAsState()
        val selected by viewModel.selectedIds.collectAsState()
        val themeColors by LightThemeController.colors.collectAsState()

        // Confirm before bulk-deleting the ticked cards; delete on confirmation.
        val confirmDeleteSelected: () -> Unit = {
            val count = viewModel.selectedIds.value.size
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

        // Add a card by scanning it (auto-detecting the symbology), then naming it;
        // the scanner is popped before the name screen is pushed, per the SDK's
        // camera-scanner guidance. The scanner offers a manual-entry fallback that
        // resolves to the same ScannedCode, so both routes converge here.
        val startAdd: () -> Unit = {
            navigateTo(
                screenFactory = { ScanCardScreen(it) },
                resultCallback = { scanned ->
                    navigateTo(
                        screenFactory = { NameCardScreen(it) },
                        resultCallback = { name ->
                            viewModel.addCard(name, scanned.code, scanned.codeType)
                        },
                    )
                },
            )
        }

        LightTheme(colors = themeColors) {
            when (mode) {
                HomeMode.Browse -> BrowseContent(
                    cards = cards,
                    onSearch = viewModel::enterSearch,
                    onEdit = viewModel::enterSelect,
                    onAdd = startAdd,
                    onOpen = openCard,
                )

                HomeMode.Search -> SearchContent(
                    cards = cards,
                    recentIds = recentIds,
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
    onAdd: () -> Unit,
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

        if (cards.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 1f.gridUnitsAsDp()),
                contentAlignment = Alignment.Center,
            ) {
                LightText(
                    text = "No cards yet.\nTap + to add one.",
                    variant = LightTextVariant.Copy,
                    align = TextAlign.Center,
                    lighten = true,
                )
            }
        } else {
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
        }

        // Search, EDIT opens bulk-select-to-delete, ADD starts the scan/manual flow.
        LightBottomBar(
            items = listOf(
                LightBarButton.LightIcon(icon = LightIcons.SEARCH, onClick = onSearch),
                LightBarButton.Text(text = "EDIT", onClick = onEdit),
                LightBarButton.LightIcon(icon = LightIcons.ADD, onClick = onAdd),
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
    selected: Set<Long>,
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
                    checked = card.id in selected,
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
