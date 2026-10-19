package com.thelightphone.wallet.code

import com.thelightphone.wallet.code.CodeInput.Check
import kotlin.test.Test
import kotlin.test.assertEquals

class CodeInputTest {

    @Test fun ean13CorrectCheckDigitIsOk() =
        assertEquals(Check.Ok, CodeInput.check("8710398527707", CodeType.EAN_13))

    @Test fun ean13WrongCheckDigitNamesTheExpectedOne() =
        assertEquals(Check.WrongCheckDigit('7'), CodeInput.check("8710398527701", CodeType.EAN_13))

    @Test fun ean13WithoutCheckDigitIsOk() =
        assertEquals(Check.Ok, CodeInput.check("871039852770", CodeType.EAN_13))

    @Test fun ean8AndUpcACheckDigits() {
        assertEquals(Check.WrongCheckDigit('4'), CodeInput.check("96385070", CodeType.EAN_8))
        assertEquals(Check.WrongCheckDigit('2'), CodeInput.check("036000291450", CodeType.UPC_A))
    }

    @Test fun lettersOnlyOfferTypesThatCarryLetters() =
        assertEquals(
            listOf(CodeType.QR, CodeType.CODE_128, CodeType.CODE_39),
            CodeInput.typesFor("ABC"),
        )

    @Test fun oddDigitCountExcludesItf() =
        assertEquals(false, CodeType.ITF in CodeInput.typesFor("12345"))

    @Test fun wrongCheckDigitStillOffersEan13() =
        assertEquals(true, CodeType.EAN_13 in CodeInput.typesFor("8710398527701"))

    @Test fun normalizeAppendsCheckDigitAndUppercasesCode39() {
        assertEquals("8710398527707", CodeInput.normalize("871039852770", CodeType.EAN_13))
        assertEquals("96385074", CodeInput.normalize("9638507", CodeType.EAN_8))
        assertEquals("036000291452", CodeInput.normalize("03600029145", CodeType.UPC_A))
        assertEquals("WALLET-39", CodeInput.normalize("wallet-39", CodeType.CODE_39))
        assertEquals("abc", CodeInput.normalize("abc", CodeType.CODE_128))
    }
}
