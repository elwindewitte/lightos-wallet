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

class HomeScreenViewModel : LightViewModel<Unit>() {

    // Seed cards for now; manual add / local storage come later. Kept sorted
    // alphabetically (case-insensitive) so the list is easy to scan in-store.
    private val _cards = MutableStateFlow(
        listOf("Gamma", "Shell", "Praxis", "Albert Heijn")
            .map { LoyaltyCard(it) }
            .sortedBy { it.name.lowercase() },
    )
    val cards: StateFlow<List<LoyaltyCard>> = _cards.asStateFlow()
}

@InitialScreen
class HomeScreen(sealedActivity: SealedLightActivity) :
    LightScreen<Unit, HomeScreenViewModel>(sealedActivity) {

    override val viewModelClass: Class<HomeScreenViewModel>
        get() = HomeScreenViewModel::class.java

    override fun createViewModel() = HomeScreenViewModel()

    @Composable
    override fun Content() {
        val cards by viewModel.cards.collectAsState()
        val themeColors by LightThemeController.colors.collectAsState()

        LightTheme(colors = themeColors) {
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
                        LightText(
                            text = card.name,
                            variant = LightTextVariant.Copy,
                            modifier = Modifier
                                .fillMaxWidth()
                                .lightClickable {
                                    navigateTo(screenFactory = { CardDetailScreen(it, card) })
                                }
                                .padding(vertical = 0.75f.gridUnitsAsDp()),
                        )
                    }
                }

                // Actions are placeholders for now — search, add a card, edit the
                // list — and aren't wired up yet.
                LightBottomBar(
                    items = listOf(
                        LightBarButton.LightIcon(icon = LightIcons.SEARCH, onClick = {}),
                        LightBarButton.Text(text = "ADD", onClick = {}),
                        LightBarButton.LightIcon(icon = LightIcons.PENCIL, onClick = {}),
                    ),
                )
            }
        }
    }
}
