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
import com.thelightphone.wallet.code.CodeInput
import com.thelightphone.wallet.code.CodeType
import com.thelightphone.wallet.data.CardRepository
import com.thelightphone.wallet.data.RecentCardsStore
import com.thelightphone.wallet.data.WalletDatabase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HomeScreenViewModel(
    private val repository: CardRepository,
    private val recents: RecentCardsStore,
) : LightViewModel<Unit>() {

    // The persisted card list, ordered by name in the query. The Room Flow re-emits
    // on every add / rename / delete, so mutations below just write and let the list
    // refresh itself — there is no in-memory copy to keep in sync.
    val cards: StateFlow<List<LoyaltyCard>> =
        repository.observeCards()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // Card ids in most-recently-opened order. Drives the default ordering of
    // search. Persisted in DataStore so the recents survive a restart.
    private val _recentIds = MutableStateFlow<List<Long>>(emptyList())
    val recentIds: StateFlow<List<Long>> = _recentIds.asStateFlow()

    init {
        viewModelScope.launch {
            // Merge rather than overwrite, in case a card was opened before this loaded.
            _recentIds.value = (_recentIds.value + recents.load()).distinct()
        }
    }

    private fun setRecents(ids: List<Long>) {
        _recentIds.value = ids
        viewModelScope.launch { recents.save(ids) }
    }

    /** Delete the cards with [ids] (from bulk select) and drop them from recents. */
    fun deleteCards(ids: Set<Long>) {
        if (ids.isEmpty()) return
        viewModelScope.launch { repository.deleteCards(ids.toList()) }
        setRecents(_recentIds.value.filterNot { it in ids })
    }

    /** Record that a card was opened so it bubbles to the top of recents. */
    fun recordOpened(card: LoyaltyCard) {
        setRecents(listOf(card.id) + _recentIds.value.filterNot { it == card.id })
    }

    /**
     * Add a scanned or manually entered card to storage, storing the code exactly as
     * the barcode carries it (see [CodeInput.normalize]). Ignores a blank name.
     */
    fun addCard(name: String, code: String, codeType: CodeType) {
        if (name.isBlank()) return
        val stored = CodeInput.normalize(code, codeType)
        viewModelScope.launch { repository.addCard(name, stored, codeType) }
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
    fun deleteCard(id: Long) = deleteCards(setOf(id))
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

    override fun createViewModel() =
        HomeScreenViewModel(repository, RecentCardsStore(lightContext.dataStore))

    @Composable
    override fun Content() {
        val cards by viewModel.cards.collectAsState()
        val themeColors by LightThemeController.colors.collectAsState()

        // Opening a card records recency. Search pops itself before returning the
        // card, so coming back from the detail view lands on the plain list.
        val openCard: (LoyaltyCard) -> Unit = { card ->
            viewModel.recordOpened(card)
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
                    val name = scanned.name
                    if (name != null) {
                        viewModel.addCard(name, scanned.code, scanned.codeType)
                    } else {
                        navigateTo(
                            screenFactory = { NameCardScreen(it) },
                            resultCallback = { newName ->
                                viewModel.addCard(newName, scanned.code, scanned.codeType)
                            },
                        )
                    }
                },
            )
        }

        // Search and bulk-select are their own screens, so the SDK's back stack
        // (and the hardware back key) closes them like any other screen.
        val startSearch: () -> Unit = {
            navigateTo(
                screenFactory = { SearchScreen(it, cards, viewModel.recentIds.value) },
                resultCallback = openCard,
            )
        }
        val startSelect: () -> Unit = {
            navigateTo(
                screenFactory = { SelectCardsScreen(it, cards) },
                resultCallback = viewModel::deleteCards,
            )
        }

        LightTheme(colors = themeColors) {
            if (cards.isEmpty()) {
                EmptyContent(onAdd = startAdd)
            } else {
                BrowseContent(
                    cards = cards,
                    onSearch = startSearch,
                    onSelect = startSelect,
                    onAdd = startAdd,
                    onOpen = openCard,
                )
            }
        }
    }
}

@Composable
private fun BrowseContent(
    cards: List<LoyaltyCard>,
    onSearch: () -> Unit,
    onSelect: () -> Unit,
    onAdd: () -> Unit,
    onOpen: (LoyaltyCard) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(LightThemeTokens.colors.background),
    ) {
        // No top bar: the list starts at the top so more cards fit on screen.
        LightScrollView(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(start = 1f.gridUnitsAsDp(), top = 1f.gridUnitsAsDp()),
        ) {
            cards.forEach { card ->
                CardRow(name = card.name, onClick = { onOpen(card) })
            }
        }

        // Search, SELECT opens bulk-select-to-delete, ADD starts the scan/manual flow.
        LightBottomBar(
            items = listOf(
                LightBarButton.LightIcon(icon = LightIcons.SEARCH, onClick = onSearch),
                LightBarButton.Text(text = "SELECT", onClick = onSelect),
                LightBarButton.LightIcon(icon = LightIcons.ADD, onClick = onAdd),
            ),
        )
    }
}

/**
 * First run: a short explanation and a single ADD in the bottom bar (the same
 * pattern as LightOS's Authenticator). Search and SELECT only appear once there are
 * cards to act on.
 */
@Composable
private fun EmptyContent(onAdd: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(LightThemeTokens.colors.background),
    ) {
        LightTopBar(center = LightTopBarCenter.Text("Wallet"))

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 1f.gridUnitsAsDp()),
            contentAlignment = Alignment.Center,
        ) {
            LightText(
                text = "No cards yet.\nAdd your first one.",
                variant = LightTextVariant.Copy,
                align = TextAlign.Center,
                lighten = true,
            )
        }

        LightBottomBar(items = listOf(LightBarButton.Text(text = "ADD", onClick = onAdd)))
    }
}

/** A single tappable card name in a list. Shared by browse, search and the type picker. */
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
