# Wallet

A Light Phone III tool that stores loyalty and membership cards and displays their barcodes / QR codes for scanning in-store.

Built on the Light SDK (https://github.com/lightphone/light-sdk).
Build and sideload instructions live in CLAUDE.md, not here.

## Features

### Adding cards
- **Scan with the camera.** Point the camera at a card's barcode or QR code; the type is detected automatically. The camera permission is only asked for the first time you add a card.
- **Seven code types:** QR, Code 128, EAN-13, EAN-8, UPC-A, Code 39 and ITF (Interleaved 2 of 5). Codes the Wallet can't display are ignored by the scanner instead of being saved broken.
- **Type it in by hand** for cards the camera can't read, or when the card isn't with you:
  - only the code types that can hold what you typed are offered;
  - a typo check for EAN-13, EAN-8 and UPC-A catches a wrong last (check) digit;
  - a missing check digit is filled in, so the number under the barcode matches your card;
  - Back goes one step at a time and keeps what you typed.
- **Name your card**, emoji included.
- **No duplicate names:** a name another card already uses (ignoring capitals) is refused with a message.

### Showing a card
- **Full-width, high-contrast code:** black on a white panel, drawn on whole pixels so tills can read it.
- **The card number** printed under the code, matching the physical card.
- Long names are shortened in the title so they never push the code down.

### Finding cards
- **Alphabetical list** that scrolls for any number of cards.
- **Search** filters as you type and ignores capitals.
- **Recently opened first:** an empty search shows the cards you opened most recently, remembered across restarts.
- **Return hides the keyboard** so the results get the whole screen; tap the search field to bring it back.

### Managing cards
- **Rename** or **delete** a card from its screen (deleting asks for confirmation).
- **Delete several at once** with SELECT.

## Privacy
- Cards are stored only on your phone. There is no account and no sync, and the Wallet works fully offline.
- The Wallet itself sends no data anywhere: no analytics, tracking or ads.
- **Disclosure:** the Light SDK's camera scanner uses Google ML Kit barcode scanning, which sends anonymous usage statistics to Google when the phone is online (app ID and version, ML Kit version, language and a per-install ID). No card numbers or images are included.

## Status
Version 1.0.0, being submitted to the Light Phone Tool Library.
