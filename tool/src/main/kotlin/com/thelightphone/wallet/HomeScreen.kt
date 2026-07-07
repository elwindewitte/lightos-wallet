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

/** Whether the home screen is browsing the full list or searching within it. */
enum class HomeMode { Browse, Search }

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

    fun enterSearch() {
        _mode.value = HomeMode.Search
    }

    fun exitSearch() {
        _mode.value = HomeMode.Browse
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

    // Consume the hardware back button to leave search mode instead of exiting.
    override fun onBackPressed(): Boolean =
        if (_mode.value == HomeMode.Search) {
            exitSearch()
            true
        } else {
            false
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
        val themeColors by LightThemeController.colors.collectAsState()

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
                    onOpen = openCard,
                )

                HomeMode.Search -> SearchContent(
                    cards = cards,
                    recentNames = recentNames,
                    onClose = viewModel::exitSearch,
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

        // ADD and PENCIL (edit) are placeholders for now; only search is wired.
        LightBottomBar(
            items = listOf(
                LightBarButton.LightIcon(icon = LightIcons.SEARCH, onClick = onSearch),
                LightBarButton.Text(text = "ADD", onClick = {}),
                LightBarButton.LightIcon(icon = LightIcons.PENCIL, onClick = {}),
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
