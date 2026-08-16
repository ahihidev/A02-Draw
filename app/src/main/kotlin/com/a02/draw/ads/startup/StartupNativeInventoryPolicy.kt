package com.a02.draw.ads.startup

internal enum class NativeInventoryOrigin {
    SPLASH_PRELOAD,
    LANGUAGE_SPARE,
    PLACEMENT,
}

internal object StartupNativeInventoryPolicy {
    private val languageTwoFloor = AdInventoryKey(
        StartupAdPlacement.LANGUAGE,
        AdFloor.TWO_FLOOR,
    )
    private val fallbackComparator = compareBy<AdInventoryKey>(
        { it.floor.ordinal },
        { startupNativePreloadOrder.indexOf(it.placement) },
    )

    fun startupCandidates(
        placement: StartupAdPlacement,
        inventory: Map<AdInventoryKey, NativeInventoryOrigin>,
    ): List<AdInventoryKey> = buildList {
        if (languageTwoFloor in inventory) add(languageTwoFloor)
        addAll(splashPreloadedCandidates(inventory))
        AdFloor.entries.forEach { floor ->
            val key = AdInventoryKey(placement, floor)
            if (key in inventory) add(key)
        }
    }.distinct()

    fun sharedCandidates(
        inventory: Map<AdInventoryKey, NativeInventoryOrigin>,
    ): List<AdInventoryKey> = buildList {
        if (languageTwoFloor in inventory) add(languageTwoFloor)
        addAll(splashPreloadedCandidates(inventory))
    }.distinct()

    private fun splashPreloadedCandidates(
        inventory: Map<AdInventoryKey, NativeInventoryOrigin>,
    ): List<AdInventoryKey> = inventory
        .filterValues { it == NativeInventoryOrigin.SPLASH_PRELOAD }
        .keys
        .filterNot { it == languageTwoFloor }
        .sortedWith(fallbackComparator)
}
