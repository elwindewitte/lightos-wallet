package com.thelightphone.wallet

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.SimpleLightScreen
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightFullscreenModal
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
import com.thelightphone.wallet.code.CodeInput
import com.thelightphone.wallet.code.CodeType
import com.thelightphone.wallet.code.displayLabel

/**
 * Manual-entry step: pick the code's symbology for [code]. Reached from
 * [EnterCodeScreen] when the camera couldn't be used, so the type can't be
 * auto-detected.
 *
 * Only lists the types that can actually encode [code] (see [CodeInput.typesFor]), so
 * nothing unrenderable can be saved. Picking an EAN/UPC whose check digit doesn't
 * match shows LightOS's full-screen message; closing it returns to the number.
 * Otherwise it pushes the name step and returns the finished card via [goBack].
 * Backing out returns to [EnterCodeScreen] with the number kept.
 */
class PickCodeTypeScreen(
    sealedActivity: SealedLightActivity,
    private val code: String,
) : SimpleLightScreen<ScannedCode>(sealedActivity) {

    private val types = CodeInput.typesFor(code)

    private var error by mutableStateOf<String?>(null)

    private fun pick(type: CodeType) {
        when (val check = CodeInput.check(code, type)) {
            is CodeInput.Check.WrongCheckDigit -> error =
                "This number doesn’t look right. The last digit should be " +
                    "${check.expected}, check the number on your card."

            else -> navigateTo(
                screenFactory = { NameCardScreen(it) },
                resultCallback = { name ->
                    goBack(ScannedCode(CodeInput.normalize(code, type), type, name))
                },
            )
        }
    }

    @Composable
    override fun Content() {
        val themeColors by LightThemeController.colors.collectAsState()

        LightTheme(colors = themeColors) {
            val message = error
            if (message != null) {
                LightFullscreenModal(message = message, onClose = { goBack() })
                return@LightTheme
            }

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
                    center = LightTopBarCenter.Text("Code type"),
                    modifier = Modifier.padding(bottom = 1f.gridUnitsAsDp()),
                )

                LightScrollView(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(start = 1f.gridUnitsAsDp()),
                ) {
                    if (types.isEmpty()) {
                        LightText(
                            text = "No code type fits this number.",
                            variant = LightTextVariant.Copy,
                            lighten = true,
                            modifier = Modifier.padding(vertical = 0.75f.gridUnitsAsDp()),
                        )
                    }
                    types.forEach { type ->
                        CardRow(name = type.displayLabel, onClick = { pick(type) })
                    }
                }
            }
        }
    }
}
