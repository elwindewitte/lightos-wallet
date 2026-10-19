package com.thelightphone.wallet

import com.thelightphone.sdk.EntryPoint
import com.thelightphone.sdk.LightEntryPoint

/**
 * The SDK's required tool entry point. The Wallet needs no startup work and no push
 * notifications, so it keeps every [LightEntryPoint] default.
 */
@EntryPoint
object ToolEntryPoint : LightEntryPoint
