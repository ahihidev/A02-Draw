package com.a02.draw.feature.home.screen.emojimix

enum class EmojiMixMode(val slotCount: Int) {
    MIX_2(2),
    MIX_3(3),
}

data class EmojiOption(val emoji: String, val label: String)

data class EmojiKitchenPair(val left: String, val right: String, val resultUrl: String) {
    val normalizedKey: String = normalize(left, right)

    companion object {
        fun normalize(first: String, second: String): String =
            listOf(first, second).sorted().joinToString("|")
    }
}

data class EmojiMixResult(
    val mode: EmojiMixMode,
    val inputs: List<String>,
    val uri: String,
    val displayName: String,
)

object EmojiCompositeSpec {
    const val SIZE = 1024
    val centers: List<Pair<Int, Int>> = listOf(350 to 410, 674 to 410, 512 to 720)
}

object EmojiKitchenCatalog {
    val options = listOf(
        EmojiOption("😀", "Grinning face"),
        EmojiOption("😍", "Heart eyes"),
        EmojiOption("😂", "Tears of joy"),
        EmojiOption("🥰", "Smiling face with hearts"),
        EmojiOption("🤔", "Thinking face"),
        EmojiOption("😎", "Cool face"),
        EmojiOption("🥳", "Party face"),
        EmojiOption("😭", "Crying face"),
        EmojiOption("🔥", "Fire"),
        EmojiOption("🤖", "Robot"),
        EmojiOption("🐱", "Cat"),
        EmojiOption("🐶", "Dog"),
        EmojiOption("🐼", "Panda"),
        EmojiOption("🐸", "Frog"),
        EmojiOption("👻", "Ghost"),
        EmojiOption("🚀", "Rocket"),
        EmojiOption("🦄", "Unicorn"),
        EmojiOption("🌈", "Rainbow"),
        EmojiOption("🍕", "Pizza"),
        EmojiOption("⭐", "Star"),
        EmojiOption("❤️", "Heart"),
    ) + EmojiKitchenExpansion.options

    // Stable, app-owned metadata manifest. The generated artwork remains hosted by Google Emoji Kitchen.
    val pairs = listOf(
        EmojiKitchenPair(
            "😀",
            "😍",
            "https://www.gstatic.com/android/keyboard/emojikitchen/20201001/u1f600/u1f600_u1f60d.png"
        ),
        EmojiKitchenPair(
            "😀",
            "😂",
            "https://www.gstatic.com/android/keyboard/emojikitchen/20201001/u1f600/u1f600_u1f602.png"
        ),
        EmojiKitchenPair(
            "😀",
            "🔥",
            "https://www.gstatic.com/android/keyboard/emojikitchen/20201001/u1f600/u1f600_u1f525.png"
        ),
        EmojiKitchenPair(
            "😀",
            "🐱",
            "https://www.gstatic.com/android/keyboard/emojikitchen/20230301/u1f600/u1f600_u1f431.png"
        ),
        EmojiKitchenPair(
            "😀",
            "🐶",
            "https://www.gstatic.com/android/keyboard/emojikitchen/20211115/u1f436/u1f436_u1f600.png"
        ),
        EmojiKitchenPair(
            "😀",
            "👻",
            "https://www.gstatic.com/android/keyboard/emojikitchen/20201001/u1f600/u1f600_u1f47b.png"
        ),
        EmojiKitchenPair(
            "😀",
            "🚀",
            "https://www.gstatic.com/android/keyboard/emojikitchen/20240206/u1f680/u1f680_u1f600.png"
        ),
        EmojiKitchenPair(
            "😀",
            "🦄",
            "https://www.gstatic.com/android/keyboard/emojikitchen/20210831/u1f984/u1f984_u1f600.png"
        ),
        EmojiKitchenPair(
            "😍",
            "😂",
            "https://www.gstatic.com/android/keyboard/emojikitchen/20201001/u1f602/u1f602_u1f60d.png"
        ),
        EmojiKitchenPair(
            "😍",
            "🔥",
            "https://www.gstatic.com/android/keyboard/emojikitchen/20201001/u1f60d/u1f60d_u1f525.png"
        ),
        EmojiKitchenPair(
            "😍",
            "🐱",
            "https://www.gstatic.com/android/keyboard/emojikitchen/20201001/u1f60d/u1f60d_u1f431.png"
        ),
        EmojiKitchenPair(
            "😍",
            "🐶",
            "https://www.gstatic.com/android/keyboard/emojikitchen/20211115/u1f436/u1f436_u1f60d.png"
        ),
        EmojiKitchenPair(
            "😍",
            "👻",
            "https://www.gstatic.com/android/keyboard/emojikitchen/20201001/u1f60d/u1f60d_u1f47b.png"
        ),
        EmojiKitchenPair(
            "😍",
            "🚀",
            "https://www.gstatic.com/android/keyboard/emojikitchen/20230301/u1f60d/u1f60d_u1f680.png"
        ),
        EmojiKitchenPair(
            "😍",
            "🦄",
            "https://www.gstatic.com/android/keyboard/emojikitchen/20210831/u1f984/u1f984_u1f60d.png"
        ),
        EmojiKitchenPair(
            "😂",
            "🔥",
            "https://www.gstatic.com/android/keyboard/emojikitchen/20201001/u1f602/u1f602_u1f525.png"
        ),
        EmojiKitchenPair(
            "😂",
            "🐱",
            "https://www.gstatic.com/android/keyboard/emojikitchen/20230301/u1f602/u1f602_u1f431.png"
        ),
        EmojiKitchenPair(
            "😂",
            "🐶",
            "https://www.gstatic.com/android/keyboard/emojikitchen/20211115/u1f436/u1f436_u1f602.png"
        ),
        EmojiKitchenPair(
            "😂",
            "👻",
            "https://www.gstatic.com/android/keyboard/emojikitchen/20230301/u1f602/u1f602_u1f47b.png"
        ),
        EmojiKitchenPair(
            "😂",
            "🚀",
            "https://www.gstatic.com/android/keyboard/emojikitchen/20220815/u1f602/u1f602_u1f680.png"
        ),
        EmojiKitchenPair(
            "😂",
            "🦄",
            "https://www.gstatic.com/android/keyboard/emojikitchen/20210831/u1f984/u1f984_u1f602.png"
        ),
        EmojiKitchenPair(
            "🔥",
            "🐱",
            "https://www.gstatic.com/android/keyboard/emojikitchen/20201001/u1f431/u1f431_u1f525.png"
        ),
        EmojiKitchenPair(
            "🔥",
            "🐶",
            "https://www.gstatic.com/android/keyboard/emojikitchen/20211115/u1f436/u1f436_u1f525.png"
        ),
        EmojiKitchenPair(
            "🔥",
            "👻",
            "https://www.gstatic.com/android/keyboard/emojikitchen/20201001/u1f47b/u1f47b_u1f525.png"
        ),
        EmojiKitchenPair(
            "🔥",
            "🚀",
            "https://www.gstatic.com/android/keyboard/emojikitchen/20240530/u1f525/u1f525_u1f680.png"
        ),
        EmojiKitchenPair(
            "🔥",
            "🦄",
            "https://www.gstatic.com/android/keyboard/emojikitchen/20210831/u1f984/u1f984_u1f525.png"
        ),
        EmojiKitchenPair(
            "🥰",
            "😀",
            "https://www.gstatic.com/android/keyboard/emojikitchen/20201001/u1f600/u1f600_u1f970.png"
        ),
        EmojiKitchenPair(
            "🥰",
            "😍",
            "https://www.gstatic.com/android/keyboard/emojikitchen/20201001/u1f970/u1f970_u1f60d.png"
        ),
        EmojiKitchenPair(
            "🥰",
            "😂",
            "https://www.gstatic.com/android/keyboard/emojikitchen/20230301/u1f602/u1f602_u1f970.png"
        ),
        EmojiKitchenPair(
            "🥰",
            "🔥",
            "https://www.gstatic.com/android/keyboard/emojikitchen/20201001/u1f970/u1f970_u1f525.png"
        ),
        EmojiKitchenPair(
            "🤔",
            "😀",
            "https://www.gstatic.com/android/keyboard/emojikitchen/20201001/u1f600/u1f600_u1f914.png"
        ),
        EmojiKitchenPair(
            "🤔",
            "😍",
            "https://www.gstatic.com/android/keyboard/emojikitchen/20201001/u1f60d/u1f60d_u1f914.png"
        ),
        EmojiKitchenPair(
            "🤔",
            "😂",
            "https://www.gstatic.com/android/keyboard/emojikitchen/20201001/u1f602/u1f602_u1f914.png"
        ),
        EmojiKitchenPair(
            "🤔",
            "🔥",
            "https://www.gstatic.com/android/keyboard/emojikitchen/20201001/u1f914/u1f914_u1f525.png"
        ),
        EmojiKitchenPair(
            "😎",
            "😀",
            "https://www.gstatic.com/android/keyboard/emojikitchen/20230301/u1f600/u1f600_u1f60e.png"
        ),
        EmojiKitchenPair(
            "😎",
            "😍",
            "https://www.gstatic.com/android/keyboard/emojikitchen/20230301/u1f60d/u1f60d_u1f60e.png"
        ),
        EmojiKitchenPair(
            "😎",
            "😂",
            "https://www.gstatic.com/android/keyboard/emojikitchen/20230301/u1f602/u1f602_u1f60e.png"
        ),
        EmojiKitchenPair(
            "😎",
            "🔥",
            "https://www.gstatic.com/android/keyboard/emojikitchen/20230301/u1f60e/u1f60e_u1f525.png"
        ),
        EmojiKitchenPair(
            "🥳",
            "😀",
            "https://www.gstatic.com/android/keyboard/emojikitchen/20230301/u1f600/u1f600_u1f973.png"
        ),
        EmojiKitchenPair(
            "🥳",
            "😍",
            "https://www.gstatic.com/android/keyboard/emojikitchen/20201001/u1f60d/u1f60d_u1f973.png"
        ),
        EmojiKitchenPair(
            "🥳",
            "😂",
            "https://www.gstatic.com/android/keyboard/emojikitchen/20230301/u1f602/u1f602_u1f973.png"
        ),
        EmojiKitchenPair(
            "🥳",
            "🔥",
            "https://www.gstatic.com/android/keyboard/emojikitchen/20201001/u1f973/u1f973_u1f525.png"
        ),
        EmojiKitchenPair(
            "😭",
            "😀",
            "https://www.gstatic.com/android/keyboard/emojikitchen/20201001/u1f600/u1f600_u1f62d.png"
        ),
        EmojiKitchenPair(
            "😭",
            "😍",
            "https://www.gstatic.com/android/keyboard/emojikitchen/20241023/u1f62d/u1f62d_u1f60d.png"
        ),
        EmojiKitchenPair(
            "😭",
            "😂",
            "https://www.gstatic.com/android/keyboard/emojikitchen/20201001/u1f602/u1f602_u1f62d.png"
        ),
        EmojiKitchenPair(
            "😭",
            "🔥",
            "https://www.gstatic.com/android/keyboard/emojikitchen/20201001/u1f62d/u1f62d_u1f525.png"
        ),
        EmojiKitchenPair(
            "🤖",
            "😀",
            "https://www.gstatic.com/android/keyboard/emojikitchen/20201001/u1f600/u1f600_u1f916.png"
        ),
        EmojiKitchenPair(
            "🤖",
            "😍",
            "https://www.gstatic.com/android/keyboard/emojikitchen/20201001/u1f60d/u1f60d_u1f916.png"
        ),
        EmojiKitchenPair(
            "🤖",
            "😂",
            "https://www.gstatic.com/android/keyboard/emojikitchen/20201001/u1f602/u1f602_u1f916.png"
        ),
        EmojiKitchenPair(
            "🤖",
            "🔥",
            "https://www.gstatic.com/android/keyboard/emojikitchen/20201001/u1f916/u1f916_u1f525.png"
        ),
        EmojiKitchenPair(
            "🐼",
            "😀",
            "https://www.gstatic.com/android/keyboard/emojikitchen/20230301/u1f600/u1f600_u1f43c.png"
        ),
        EmojiKitchenPair(
            "🐼",
            "😍",
            "https://www.gstatic.com/android/keyboard/emojikitchen/20230301/u1f60d/u1f60d_u1f43c.png"
        ),
        EmojiKitchenPair(
            "🐼",
            "😂",
            "https://www.gstatic.com/android/keyboard/emojikitchen/20230301/u1f602/u1f602_u1f43c.png"
        ),
        EmojiKitchenPair(
            "🐼",
            "🔥",
            "https://www.gstatic.com/android/keyboard/emojikitchen/20230216/u1f43c/u1f43c_u1f525.png"
        ),
        EmojiKitchenPair(
            "🐸",
            "😀",
            "https://www.gstatic.com/android/keyboard/emojikitchen/20230803/u1f438/u1f438_u1f600.png"
        ),
        EmojiKitchenPair(
            "🐸",
            "😍",
            "https://www.gstatic.com/android/keyboard/emojikitchen/20230803/u1f438/u1f438_u1f60d.png"
        ),
        EmojiKitchenPair(
            "🐸",
            "😂",
            "https://www.gstatic.com/android/keyboard/emojikitchen/20230803/u1f438/u1f438_u1f602.png"
        ),
        EmojiKitchenPair(
            "🐸",
            "🔥",
            "https://www.gstatic.com/android/keyboard/emojikitchen/20230803/u1f438/u1f438_u1f525.png"
        ),
        EmojiKitchenPair(
            "🌈",
            "😀",
            "https://www.gstatic.com/android/keyboard/emojikitchen/20230301/u1f600/u1f600_u1f308.png"
        ),
        EmojiKitchenPair(
            "🌈",
            "😍",
            "https://www.gstatic.com/android/keyboard/emojikitchen/20201001/u1f60d/u1f60d_u1f308.png"
        ),
        EmojiKitchenPair(
            "🌈",
            "😂",
            "https://www.gstatic.com/android/keyboard/emojikitchen/20230301/u1f602/u1f602_u1f308.png"
        ),
        EmojiKitchenPair(
            "🌈",
            "🔥",
            "https://www.gstatic.com/android/keyboard/emojikitchen/20201001/u1f525/u1f525_u1f308.png"
        ),
        EmojiKitchenPair(
            "🍕",
            "😀",
            "https://www.gstatic.com/android/keyboard/emojikitchen/20241021/u1f355/u1f355_u1f600.png"
        ),
        EmojiKitchenPair(
            "🍕",
            "😍",
            "https://www.gstatic.com/android/keyboard/emojikitchen/20241021/u1f355/u1f355_u1f60d.png"
        ),
        EmojiKitchenPair(
            "🍕",
            "😂",
            "https://www.gstatic.com/android/keyboard/emojikitchen/20241021/u1f355/u1f355_u1f602.png"
        ),
        EmojiKitchenPair(
            "🍕",
            "🔥",
            "https://www.gstatic.com/android/keyboard/emojikitchen/20241021/u1f355/u1f355_u1f525.png"
        ),
        EmojiKitchenPair(
            "⭐",
            "😀",
            "https://www.gstatic.com/android/keyboard/emojikitchen/20230301/u1f600/u1f600_u2b50.png"
        ),
        EmojiKitchenPair(
            "⭐",
            "😍",
            "https://www.gstatic.com/android/keyboard/emojikitchen/20230301/u1f60d/u1f60d_u2b50.png"
        ),
        EmojiKitchenPair(
            "⭐",
            "😂",
            "https://www.gstatic.com/android/keyboard/emojikitchen/20230301/u1f602/u1f602_u2b50.png"
        ),
        EmojiKitchenPair(
            "⭐",
            "🔥",
            "https://www.gstatic.com/android/keyboard/emojikitchen/20240610/u2b50/u2b50_u1f525.png"
        ),
        EmojiKitchenPair(
            "❤️",
            "😀",
            "https://www.gstatic.com/android/keyboard/emojikitchen/20250130/u2764-ufe0f/u2764-ufe0f_u1f600.png"
        ),
        EmojiKitchenPair(
            "❤️",
            "😍",
            "https://www.gstatic.com/android/keyboard/emojikitchen/20201001/u1f60d/u1f60d_u2764-ufe0f.png"
        ),
        EmojiKitchenPair(
            "❤️",
            "😂",
            "https://www.gstatic.com/android/keyboard/emojikitchen/20250130/u2764-ufe0f/u2764-ufe0f_u1f602.png"
        ),
        EmojiKitchenPair(
            "❤️",
            "🔥",
            "https://www.gstatic.com/android/keyboard/emojikitchen/20230821/u2764-ufe0f/u2764-ufe0f_u1f525.png"
        ),
    ) + EmojiKitchenExpansion.pairs

    fun compatibleWith(first: String): List<EmojiOption> {
        val supported = pairs.asSequence()
            .filter { it.left == first || it.right == first }
            .map { if (it.left == first) it.right else it.left }
            .toSet()
        return options.filter { it.emoji in supported }
    }

    fun find(first: String, second: String): EmojiKitchenPair? {
        val key = EmojiKitchenPair.normalize(first, second)
        return pairs.firstOrNull { it.normalizedKey == key }
    }
}
