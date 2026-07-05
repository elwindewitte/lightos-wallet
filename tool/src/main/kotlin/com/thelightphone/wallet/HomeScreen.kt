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
import androidx.lifecycle.viewModelScope
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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Whether the home screen is browsing the full list or searching within it. */
enum class HomeMode { Browse, Search }

class HomeScreenViewModel(
    private val stats: CardStatsStore,
) : LightViewModel<Unit>() {

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

    // Persistent per-card open counts. Not consumed for sorting yet — we're
    // collecting the data now so a "most used" view can use it later.
    val openCounts: StateFlow<Map<String, Int>> =
        stats.openCounts.stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    fun enterSearch() {
        _mode.value = HomeMode.Search
    }

    fun exitSearch() {
        _mode.value = HomeMode.Browse
    }

    /** Record that a card was opened: bump recents and the persistent open count. */
    fun recordOpened(card: LoyaltyCard) {
        _recentNames.value = listOf(card.name) + _recentNames.value.filterNot { it == card.name }
        viewModelScope.launch { stats.recordOpen(card.name) }
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

    override fun createViewModel() = HomeScreenViewModel(CardStatsStore(lightContext.dataStore))

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
            navigateTo(screenFactory = { CardDetailScreen(it, card) })
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
