package com.a02.draw.feature.home.screen.emojimix

internal object EmojiKitchenExpansion {
    private data class CatalogEntry(
        val emoji: String,
        val label: String,
        val resultUrl: String,
    )

    private val entries = listOf(
        CatalogEntry(
            "😃",
            "Smiley",
            "https://www.gstatic.com/android/keyboard/emojikitchen/20260202/u1f600/u1f600_u1f603.png"
        ),
        CatalogEntry(
            "😄",
            "Smile",
            "https://www.gstatic.com/android/keyboard/emojikitchen/20201001/u1f600/u1f600_u1f604.png"
        ),
        CatalogEntry(
            "😁",
            "Grin",
            "https://www.gstatic.com/android/keyboard/emojikitchen/20201001/u1f600/u1f600_u1f601.png"
        ),
        CatalogEntry(
            "😆",
            "Laughing",
            "https://www.gstatic.com/android/keyboard/emojikitchen/20201001/u1f600/u1f600_u1f606.png"
        ),
        CatalogEntry(
            "😅",
            "Sweat smile",
            "https://www.gstatic.com/android/keyboard/emojikitchen/20201001/u1f600/u1f600_u1f605.png"
        ),
        CatalogEntry(
            "🤣",
            "Rolling on the floor laughing",
            "https://www.gstatic.com/android/keyboard/emojikitchen/20201001/u1f600/u1f600_u1f923.png"
        ),
        CatalogEntry(
            "😉",
            "Wink",
            "https://www.gstatic.com/android/keyboard/emojikitchen/20201001/u1f600/u1f600_u1f609.png"
        ),
        CatalogEntry(
            "😗",
            "Kissing",
            "https://www.gstatic.com/android/keyboard/emojikitchen/20201001/u1f600/u1f600_u1f617.png"
        ),
        CatalogEntry(
            "😙",
            "Kissing smiling eyes",
            "https://www.gstatic.com/android/keyboard/emojikitchen/20201001/u1f600/u1f600_u1f619.png"
        ),
        CatalogEntry(
            "😚",
            "Kissing closed eyes",
            "https://www.gstatic.com/android/keyboard/emojikitchen/20230301/u1f600/u1f600_u1f61a.png"
        ),
        CatalogEntry(
            "😘",
            "Kissing heart",
            "https://www.gstatic.com/android/keyboard/emojikitchen/20201001/u1f600/u1f600_u1f618.png"
        ),
        CatalogEntry(
            "🤩",
            "Star struck",
            "https://www.gstatic.com/android/keyboard/emojikitchen/20241023/u1f600/u1f600_u1f929.png"
        ),
        CatalogEntry(
            "🙃",
            "Upside down face",
            "https://www.gstatic.com/android/keyboard/emojikitchen/20201001/u1f600/u1f600_u1f643.png"
        ),
        CatalogEntry(
            "🙂",
            "Slightly smiling face",
            "https://www.gstatic.com/android/keyboard/emojikitchen/20201001/u1f600/u1f600_u1f642.png"
        ),
        CatalogEntry(
            "🥲",
            "Smiling face with tear",
            "https://www.gstatic.com/android/keyboard/emojikitchen/20201001/u1f600/u1f600_u1f972.png"
        ),
        CatalogEntry(
            "🥹",
            "Face holding back tears",
            "https://www.gstatic.com/android/keyboard/emojikitchen/20211115/u1f979/u1f979_u1f600.png"
        ),
        CatalogEntry(
            "😋",
            "Yum",
            "https://www.gstatic.com/android/keyboard/emojikitchen/20201001/u1f600/u1f600_u1f60b.png"
        ),
        CatalogEntry(
            "😛",
            "Stuck out tongue",
            "https://www.gstatic.com/android/keyboard/emojikitchen/20201001/u1f600/u1f600_u1f61b.png"
        ),
        CatalogEntry(
            "😝",
            "Stuck out tongue closed eyes",
            "https://www.gstatic.com/android/keyboard/emojikitchen/20201001/u1f600/u1f600_u1f61d.png"
        ),
        CatalogEntry(
            "😜",
            "Stuck out tongue winking eye",
            "https://www.gstatic.com/android/keyboard/emojikitchen/20201001/u1f600/u1f600_u1f61c.png"
        ),
        CatalogEntry(
            "🤪",
            "Zany face",
            "https://www.gstatic.com/android/keyboard/emojikitchen/20201001/u1f600/u1f600_u1f92a.png"
        ),
        CatalogEntry(
            "😇",
            "Innocent",
            "https://www.gstatic.com/android/keyboard/emojikitchen/20201001/u1f600/u1f600_u1f607.png"
        ),
        CatalogEntry(
            "😊",
            "Blush",
            "https://www.gstatic.com/android/keyboard/emojikitchen/20201001/u1f600/u1f600_u1f60a.png"
        ),
        CatalogEntry(
            "☺️",
            "Relaxed",
            "https://www.gstatic.com/android/keyboard/emojikitchen/20201001/u1f600/u1f600_u263a-ufe0f.png"
        ),
        CatalogEntry(
            "😏",
            "Smirk",
            "https://www.gstatic.com/android/keyboard/emojikitchen/20201001/u1f600/u1f600_u1f60f.png"
        ),
        CatalogEntry(
            "😌",
            "Relieved",
            "https://www.gstatic.com/android/keyboard/emojikitchen/20201001/u1f600/u1f600_u1f60c.png"
        ),
        CatalogEntry(
            "😔",
            "Pensive",
            "https://www.gstatic.com/android/keyboard/emojikitchen/20201001/u1f600/u1f600_u1f614.png"
        ),
        CatalogEntry(
            "😑",
            "Expressionless",
            "https://www.gstatic.com/android/keyboard/emojikitchen/20201001/u1f600/u1f600_u1f611.png"
        ),
        CatalogEntry(
            "😐",
            "Neutral face",
            "https://www.gstatic.com/android/keyboard/emojikitchen/20201001/u1f600/u1f600_u1f610.png"
        ),
        CatalogEntry(
            "😶",
            "No mouth",
            "https://www.gstatic.com/android/keyboard/emojikitchen/20201001/u1f600/u1f600_u1f636.png"
        ),
        CatalogEntry(
            "🫡",
            "Saluting face",
            "https://www.gstatic.com/android/keyboard/emojikitchen/20211115/u1fae1/u1fae1_u1f600.png"
        ),
        CatalogEntry(
            "🤫",
            "Shushing face",
            "https://www.gstatic.com/android/keyboard/emojikitchen/20201001/u1f600/u1f600_u1f92b.png"
        ),
        CatalogEntry(
            "🫢",
            "Face with open eyes and hand over mouth",
            "https://www.gstatic.com/android/keyboard/emojikitchen/20211115/u1fae2/u1fae2_u1f600.png"
        ),
        CatalogEntry(
            "🤭",
            "Face with hand over mouth",
            "https://www.gstatic.com/android/keyboard/emojikitchen/20201001/u1f600/u1f600_u1f92d.png"
        ),
        CatalogEntry(
            "🥱",
            "Yawning face",
            "https://www.gstatic.com/android/keyboard/emojikitchen/20201001/u1f600/u1f600_u1f971.png"
        ),
        CatalogEntry(
            "🐵",
            "Monkey face",
            "https://www.gstatic.com/android/keyboard/emojikitchen/20201001/u1f600/u1f600_u1f435.png"
        ),
        CatalogEntry(
            "🦁",
            "Lion face",
            "https://www.gstatic.com/android/keyboard/emojikitchen/20201001/u1f600/u1f600_u1f981.png"
        ),
        CatalogEntry(
            "🐯",
            "Tiger",
            "https://www.gstatic.com/android/keyboard/emojikitchen/20220110/u1f42f/u1f42f_u1f600.png"
        ),
        CatalogEntry(
            "🐺",
            "Wolf",
            "https://www.gstatic.com/android/keyboard/emojikitchen/20221101/u1f43a/u1f43a_u1f600.png"
        ),
        CatalogEntry(
            "🐻",
            "Bear",
            "https://www.gstatic.com/android/keyboard/emojikitchen/20210831/u1f43b/u1f43b_u1f600.png"
        ),
        CatalogEntry(
            "🐨",
            "Koala",
            "https://www.gstatic.com/android/keyboard/emojikitchen/20201001/u1f600/u1f600_u1f428.png"
        ),
        CatalogEntry(
            "🐭",
            "Mouse",
            "https://www.gstatic.com/android/keyboard/emojikitchen/20201001/u1f600/u1f600_u1f42d.png"
        ),
        CatalogEntry(
            "🐰",
            "Rabbit",
            "https://www.gstatic.com/android/keyboard/emojikitchen/20230301/u1f600/u1f600_u1f430.png"
        ),
        CatalogEntry(
            "🦊",
            "Fox face",
            "https://www.gstatic.com/android/keyboard/emojikitchen/20221101/u1f98a/u1f98a_u1f600.png"
        ),
        CatalogEntry(
            "🦝",
            "Raccoon",
            "https://www.gstatic.com/android/keyboard/emojikitchen/20211115/u1f99d/u1f99d_u1f600.png"
        ),
        CatalogEntry(
            "🐮",
            "Cow",
            "https://www.gstatic.com/android/keyboard/emojikitchen/20230803/u1f42e/u1f42e_u1f600.png"
        ),
        CatalogEntry(
            "🐷",
            "Pig",
            "https://www.gstatic.com/android/keyboard/emojikitchen/20230301/u1f600/u1f600_u1f437.png"
        ),
        CatalogEntry(
            "🐉",
            "Dragon",
            "https://www.gstatic.com/android/keyboard/emojikitchen/20241023/u1f409/u1f409_u1f600.png"
        ),
        CatalogEntry(
            "🐢",
            "Turtle",
            "https://www.gstatic.com/android/keyboard/emojikitchen/20201001/u1f600/u1f600_u1f422.png"
        ),
        CatalogEntry(
            "🐍",
            "Snake",
            "https://www.gstatic.com/android/keyboard/emojikitchen/20240530/u1f40d/u1f40d_u1f600.png"
        ),
        CatalogEntry(
            "🦔",
            "Hedgehog",
            "https://www.gstatic.com/android/keyboard/emojikitchen/20201001/u1f600/u1f600_u1f994.png"
        ),
        CatalogEntry(
            "🦉",
            "Owl",
            "https://www.gstatic.com/android/keyboard/emojikitchen/20210831/u1f989/u1f989_u1f600.png"
        ),
        CatalogEntry(
            "🐧",
            "Penguin",
            "https://www.gstatic.com/android/keyboard/emojikitchen/20211115/u1f427/u1f427_u1f600.png"
        ),
        CatalogEntry(
            "🦈",
            "Shark",
            "https://www.gstatic.com/android/keyboard/emojikitchen/20230418/u1f988/u1f988_u1f600.png"
        ),
        CatalogEntry(
            "🐳",
            "Whale",
            "https://www.gstatic.com/android/keyboard/emojikitchen/20230418/u1f433/u1f433_u1f600.png"
        ),
        CatalogEntry(
            "🐙",
            "Octopus",
            "https://www.gstatic.com/android/keyboard/emojikitchen/20201001/u1f600/u1f600_u1f419.png"
        ),
        CatalogEntry(
            "🦀",
            "Crab",
            "https://www.gstatic.com/android/keyboard/emojikitchen/20241023/u1f980/u1f980_u1f600.png"
        ),
        CatalogEntry(
            "🐝",
            "Bee",
            "https://www.gstatic.com/android/keyboard/emojikitchen/20201001/u1f600/u1f600_u1f41d.png"
        ),
        CatalogEntry(
            "🦋",
            "Butterfly",
            "https://www.gstatic.com/android/keyboard/emojikitchen/20240206/u1f98b/u1f98b_u1f600.png"
        ),
        CatalogEntry(
            "🐞",
            "Ladybug",
            "https://www.gstatic.com/android/keyboard/emojikitchen/20240530/u1f41e/u1f41e_u1f600.png"
        ),
        CatalogEntry(
            "🍓",
            "Strawberry",
            "https://www.gstatic.com/android/keyboard/emojikitchen/20230127/u1f353/u1f353_u1f600.png"
        ),
        CatalogEntry(
            "🍎",
            "Apple",
            "https://www.gstatic.com/android/keyboard/emojikitchen/20240206/u1f34e/u1f34e_u1f600.png"
        ),
        CatalogEntry(
            "🍉",
            "Watermelon",
            "https://www.gstatic.com/android/keyboard/emojikitchen/20220406/u1f349/u1f349_u1f600.png"
        ),
        CatalogEntry(
            "🍍",
            "Pineapple",
            "https://www.gstatic.com/android/keyboard/emojikitchen/20201001/u1f600/u1f600_u1f34d.png"
        ),
        CatalogEntry(
            "🍌",
            "Banana",
            "https://www.gstatic.com/android/keyboard/emojikitchen/20211115/u1f34c/u1f34c_u1f600.png"
        ),
        CatalogEntry(
            "🥑",
            "Avocado",
            "https://www.gstatic.com/android/keyboard/emojikitchen/20201001/u1f600/u1f600_u1f951.png"
        ),
        CatalogEntry(
            "🍔",
            "Hamburger",
            "https://www.gstatic.com/android/keyboard/emojikitchen/20260202/u1f354/u1f354_u1f600.png"
        ),
        CatalogEntry(
            "🍟",
            "Fries",
            "https://www.gstatic.com/android/keyboard/emojikitchen/20240206/u1f35f/u1f35f_u1f600.png"
        ),
        CatalogEntry(
            "🌮",
            "Taco",
            "https://www.gstatic.com/android/keyboard/emojikitchen/20240206/u1f32e/u1f32e_u1f600.png"
        ),
        CatalogEntry(
            "🍣",
            "Sushi",
            "https://www.gstatic.com/android/keyboard/emojikitchen/20240530/u1f363/u1f363_u1f600.png"
        ),
        CatalogEntry(
            "🍦",
            "Ice cream",
            "https://www.gstatic.com/android/keyboard/emojikitchen/20240206/u1f366/u1f366_u1f600.png"
        ),
        CatalogEntry(
            "🎂",
            "Birthday cake",
            "https://www.gstatic.com/android/keyboard/emojikitchen/20201001/u1f600/u1f600_u1f382.png"
        ),
        CatalogEntry(
            "🍩",
            "Doughnut",
            "https://www.gstatic.com/android/keyboard/emojikitchen/20240206/u1f369/u1f369_u1f600.png"
        ),
        CatalogEntry(
            "🍪",
            "Cookie",
            "https://www.gstatic.com/android/keyboard/emojikitchen/20240206/u1f36a/u1f36a_u1f600.png"
        ),
        CatalogEntry(
            "☕",
            "Coffee",
            "https://www.gstatic.com/android/keyboard/emojikitchen/20230301/u1f600/u1f600_u2615.png"
        ),
        CatalogEntry(
            "🪄",
            "Magic wand",
            "https://www.gstatic.com/android/keyboard/emojikitchen/20210521/u1fa84/u1fa84_u1f600.png"
        ),
        CatalogEntry(
            "🎃",
            "Jack o lantern",
            "https://www.gstatic.com/android/keyboard/emojikitchen/20201001/u1f600/u1f600_u1f383.png"
        ),
        CatalogEntry(
            "🎊",
            "Confetti ball",
            "https://www.gstatic.com/android/keyboard/emojikitchen/20201001/u1f600/u1f600_u1f38a.png"
        ),
        CatalogEntry(
            "🎈",
            "Balloon",
            "https://www.gstatic.com/android/keyboard/emojikitchen/20201001/u1f600/u1f600_u1f388.png"
        ),
        CatalogEntry(
            "🎀",
            "Ribbon",
            "https://www.gstatic.com/android/keyboard/emojikitchen/20240530/u1f380/u1f380_u1f600.png"
        ),
        CatalogEntry(
            "🎁",
            "Gift",
            "https://www.gstatic.com/android/keyboard/emojikitchen/20211115/u1f381/u1f381_u1f600.png"
        ),
        CatalogEntry(
            "🎆",
            "Fireworks",
            "https://www.gstatic.com/android/keyboard/emojikitchen/20231113/u1f386/u1f386_u1f600.png"
        ),
        CatalogEntry(
            "🪩",
            "Mirror ball",
            "https://www.gstatic.com/android/keyboard/emojikitchen/20240206/u1faa9/u1faa9_u1f600.png"
        ),
        CatalogEntry(
            "⚽",
            "Soccer ball",
            "https://www.gstatic.com/android/keyboard/emojikitchen/20220406/u26bd/u26bd_u1f600.png"
        ),
        CatalogEntry(
            "🎨",
            "Artist palette",
            "https://www.gstatic.com/android/keyboard/emojikitchen/20231113/u1f3a8/u1f3a8_u1f600.png"
        ),
        CatalogEntry(
            "🌞",
            "Sun with face",
            "https://www.gstatic.com/android/keyboard/emojikitchen/20201001/u1f600/u1f600_u1f31e.png"
        ),
        CatalogEntry(
            "🌝",
            "Full moon with face",
            "https://www.gstatic.com/android/keyboard/emojikitchen/20240530/u1f31d/u1f31d_u1f600.png"
        ),
        CatalogEntry(
            "🌚",
            "New moon with face",
            "https://www.gstatic.com/android/keyboard/emojikitchen/20240530/u1f31a/u1f31a_u1f600.png"
        ),
        CatalogEntry(
            "🌟",
            "Glowing star",
            "https://www.gstatic.com/android/keyboard/emojikitchen/20201001/u1f600/u1f600_u1f31f.png"
        ),
        CatalogEntry(
            "🪂",
            "Parachute",
            "https://www.gstatic.com/android/keyboard/emojikitchen/20231113/u1fa82/u1fa82_u1f600.png"
        ),
        CatalogEntry(
            "⛰️",
            "Mountain",
            "https://www.gstatic.com/android/keyboard/emojikitchen/20240530/u26f0-ufe0f/u26f0-ufe0f_u1f600.png"
        ),
        CatalogEntry(
            "🏔️",
            "Snow capped mountain",
            "https://www.gstatic.com/android/keyboard/emojikitchen/20240206/u1f3d4-ufe0f/u1f3d4-ufe0f_u1f600.png"
        ),
        CatalogEntry(
            "🪵",
            "Wood",
            "https://www.gstatic.com/android/keyboard/emojikitchen/20211115/u1fab5/u1fab5_u1f600.png"
        ),
        CatalogEntry(
            "🪨",
            "Rock",
            "https://www.gstatic.com/android/keyboard/emojikitchen/20220406/u1faa8/u1faa8_u1f600.png"
        ),
        CatalogEntry(
            "🌋",
            "Volcano",
            "https://www.gstatic.com/android/keyboard/emojikitchen/20240206/u1f30b/u1f30b_u1f600.png"
        ),
        CatalogEntry(
            "🫧",
            "Bubbles",
            "https://www.gstatic.com/android/keyboard/emojikitchen/20240530/u1fae7/u1fae7_u1f600.png"
        ),
        CatalogEntry(
            "🗿",
            "Moyai",
            "https://www.gstatic.com/android/keyboard/emojikitchen/20241023/u1f5ff/u1f5ff_u1f600.png"
        ),
        CatalogEntry(
            "💎",
            "Gem",
            "https://www.gstatic.com/android/keyboard/emojikitchen/20230301/u1f600/u1f600_u1f48e.png"
        ),
        CatalogEntry(
            "👑",
            "Crown",
            "https://www.gstatic.com/android/keyboard/emojikitchen/20201001/u1f600/u1f600_u1f451.png"
        ),
        CatalogEntry(
            "🎹",
            "Musical keyboard",
            "https://www.gstatic.com/android/keyboard/emojikitchen/20240206/u1f3b9/u1f3b9_u1f600.png"
        ),
    )

    val options: List<EmojiOption> = entries.map { entry ->
        EmojiOption(entry.emoji, entry.label)
    }

    val pairs: List<EmojiKitchenPair> = entries.map { entry ->
        EmojiKitchenPair(ANCHOR_EMOJI, entry.emoji, entry.resultUrl)
    }

    private const val ANCHOR_EMOJI = "😀"
}
