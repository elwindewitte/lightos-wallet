# CLAUDE.md — Light Wallet

## What this tool is (and is not)
Purpose: A Light Phone III tool that stores loyalty and membership cards and displays their barcodes / QR codes for scanning in-store.
In scope (v1): manually add a card (name + code value + code type); store it locally
on the device; list saved cards; open one to display its code full-screen, high
contrast, for scanning.
Out of scope (v1): scanning a physical card to import it (needs camera access —
UNVERIFIED whether the SDK allows it); cloud sync or accounts; payment/NFC cards; sharing.
If unsure whether something is in scope, ask me — do not add features.

## Design constraints (non-negotiable)
- The display renders black & white. Design for high contrast; never rely on color.
- One screen, one job. No feeds, no dashboards, no cross-linking.
- No notifications, badges, or any mechanic designed to pull me back to the tool.
- The scannable code is the whole point: when a card is open, render the barcode as
  large, bright, and unobstructed as the screen allows.

## Technical rules — TO BE FILLED FROM SOURCE. DO NOT GUESS.
(Navigation API · allowed/declared dependencies · permission model · module structure ·
build & sideload commands · local-storage mechanism the SDK provides)
