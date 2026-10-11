package com.sandolpin.gallery.data.analysis

import java.text.Normalizer
import java.util.Locale

/**
 * 写真のカテゴリ(ML Kitのたくさんのラベルを、「食べ物」「楽器」のような、大きなまとまりにしたもの)。
 *
 * ホームの「いろいろな写真」と、検索ボックスの例(「楽器」で検索 など)に使う。
 * 検索で、カテゴリの名前(「楽器」など)を入力したときは、そのカテゴリのラベル全部が、検索の対象になる。
 *
 * @param names 日本語の名前。先頭が表示用。2つめ以降は、検索で使う別の言い方。
 * @param labels ML Kitの英語のラベル(大文字小文字は区別しない)。表にないラベルがあっても、害はない。
 */
enum class PhotoCategory(val names: List<String>, val labels: Set<String>) {
    Food(
        listOf("食べ物", "料理", "ごはん"),
        setOf(
            "Food", "Dish", "Cuisine", "Meal", "Breakfast", "Lunch", "Dinner", "Fruit", "Apple", "Banana", "Orange",
            "Lemon", "Grape", "Strawberry", "Watermelon", "Peach", "Pineapple", "Cherry", "Vegetable", "Tomato",
            "Potato", "Carrot", "Onion", "Cabbage", "Cucumber", "Pumpkin", "Mushroom", "Salad", "Meat", "Beef",
            "Pork", "Chicken", "Steak", "Sausage", "Hamburger", "Hot dog", "French fries", "Sandwich", "Pizza",
            "Pasta", "Noodle", "Ramen", "Soup", "Rice", "Sushi", "Sashimi", "Seafood", "Egg", "Cheese", "Bread",
            "Bun", "Toast", "Croissant", "Bagel", "Fast food", "Junk food", "Cooking", "Restaurant",
        ),
    ),
    Sweets(
        listOf("スイーツ", "お菓子", "デザート"),
        setOf(
            "Cake", "Cupcake", "Cookie", "Chocolate", "Candy", "Dessert", "Ice cream", "Donut", "Pancake",
            "Waffle", "Pie", "Snack", "Baking",
        ),
    ),
    Drink(
        listOf("飲み物", "ドリンク"),
        setOf(
            "Drink", "Beverage", "Coffee", "Tea", "Juice", "Beer", "Wine", "Cocktail", "Alcohol", "Milk",
            "Smoothie", "Cafe", "Bar", "Mug", "Cup", "Glass", "Bottle",
        ),
    ),
    Scenery(
        listOf("風景", "景色"),
        setOf(
            "Landscape", "Nature", "Mountain", "Hill", "Volcano", "Cliff", "Rock", "Forest", "Jungle", "Desert",
            "Field", "Meadow", "Horizon", "Sunlight", "Snow", "Winter", "Fog", "Rain", "Waterfall", "Park",
            "Garden", "Autumn", "Spring", "Summer",
        ),
    ),
    Sea(
        listOf("海", "水辺", "ビーチ"),
        setOf(
            "Sea", "Ocean", "Beach", "Coast", "Shore", "Wave", "Water", "Lake", "River", "Pond", "Sand",
            "Swimming pool", "Harbor", "Port", "Pier", "Boat", "Surfing", "Swimming",
        ),
    ),
    Sky(
        listOf("空", "夕焼け", "夜空"),
        setOf("Sky", "Cloud", "Sunset", "Sunrise", "Sun", "Moon", "Star", "Night", "Fireworks"),
    ),
    Flower(
        listOf("花", "植物"),
        setOf(
            "Plant", "Tree", "Flower", "Petal", "Rose", "Tulip", "Sunflower", "Lily", "Orchid", "Daisy", "Grass",
            "Leaf", "Bamboo", "Moss", "Cactus", "Houseplant", "Bouquet", "Branch",
        ),
    ),
    Animal(
        listOf("動物", "生き物"),
        setOf(
            "Animal", "Bird", "Duck", "Eagle", "Owl", "Penguin", "Parrot", "Swan", "Fish", "Shark", "Dolphin",
            "Whale", "Turtle", "Frog", "Snake", "Lizard", "Horse", "Cow", "Cattle", "Sheep", "Pig", "Goat",
            "Mouse", "Squirrel", "Deer", "Bear", "Elephant", "Giraffe", "Zebra", "Lion", "Tiger", "Monkey",
            "Fox", "Wolf", "Insect", "Butterfly", "Bee", "Ant", "Spider", "Beetle", "Dragonfly", "Crab",
            "Lobster", "Shrimp", "Jellyfish", "Coral", "Aquarium", "Zoo",
        ),
    ),
    Pet(
        listOf("ペット", "犬", "猫"),
        setOf("Pet", "Dog", "Puppy", "Cat", "Kitten", "Rabbit", "Hamster"),
    ),
    Vehicle(
        listOf("乗り物", "車"),
        setOf(
            "Vehicle", "Car", "Sports car", "Taxi", "Truck", "Van", "Bus", "Motorcycle", "Bicycle", "Scooter",
            "Train", "Railway", "Tram", "Airplane", "Aircraft", "Helicopter", "Airport", "Ship", "Yacht",
            "Sailboat", "Wheel", "Tire", "Cycling", "Race",
        ),
    ),
    Building(
        listOf("建物", "街", "建築"),
        setOf(
            "Building", "Architecture", "House", "Home", "Apartment", "Skyscraper", "Tower", "Bridge", "City",
            "Town", "Urban area", "Cityscape", "Skyline", "Village", "Street", "Road", "Highway", "Gate",
            "Market", "Shop", "Store", "Supermarket", "Shopping",
        ),
    ),
    Travel(
        listOf("旅行", "観光", "名所"),
        setOf(
            "Travel", "Tourism", "Vacation", "Adventure", "Temple", "Shrine", "Castle", "Palace", "Church",
            "Mosque", "Monument", "Statue", "Sculpture", "Lighthouse", "Museum", "Theme park", "Amusement park",
            "Ferris wheel", "Roller coaster", "Tent", "Camping", "Hiking", "Climbing",
        ),
    ),
    Person(
        listOf("人物", "人", "ポートレート"),
        setOf(
            "Person", "People", "Man", "Woman", "Boy", "Girl", "Child", "Toddler", "Baby", "Face", "Smile",
            "Selfie", "Crowd", "Family", "Friendship", "Couple",
        ),
    ),
    Fashion(
        listOf("ファッション", "服"),
        setOf(
            "Fashion", "Clothing", "Dress", "Suit", "Shirt", "T-shirt", "Jeans", "Jacket", "Coat", "Sweater",
            "Kimono", "Shoe", "Footwear", "Boot", "Sneakers", "Hat", "Cap", "Sunglasses", "Glasses", "Bag",
            "Handbag", "Backpack", "Jewellery", "Necklace", "Ring", "Makeup", "Costume", "Cosplay",
        ),
    ),
    Instrument(
        listOf("楽器", "音楽"),
        setOf(
            "Musical instrument", "Guitar", "Piano", "Drum", "Violin", "Microphone", "Music", "Concert", "Stage",
            "Performance",
        ),
    ),
    Sports(
        listOf("スポーツ", "運動"),
        setOf(
            "Sport", "Sports", "Ball", "Football", "Soccer", "Baseball", "Basketball", "Tennis", "Golf",
            "Volleyball", "Rugby", "Badminton", "Table tennis", "Skiing", "Snowboarding", "Skateboard",
            "Running", "Marathon", "Fishing", "Yoga", "Gym", "Fitness", "Competition", "Team", "Stadium",
        ),
    ),
    Event(
        listOf("イベント", "お祝い", "パーティー"),
        setOf(
            "Festival", "Party", "Birthday", "Christmas", "Christmas tree", "Halloween", "Wedding", "Bride",
            "Groom", "Ceremony", "Graduation", "Event", "Balloon", "Gift", "Dance",
        ),
    ),
    Interior(
        listOf("インテリア", "部屋", "家具"),
        setOf(
            "Interior design", "Furniture", "Chair", "Table", "Desk", "Couch", "Sofa", "Bed", "Shelf", "Lamp",
            "Lighting", "Curtain", "Mirror", "Clock", "Candle", "Fireplace", "Room", "Living room", "Bedroom",
            "Bathroom", "Kitchen", "Window", "Door", "Stairs", "Ceiling", "Floor",
        ),
    ),
    Art(
        listOf("アート", "イラスト", "絵"),
        setOf(
            "Art", "Illustration", "Drawing", "Painting", "Cartoon", "Comics", "Anime", "Graphic", "Poster",
            "Pattern", "Font", "Text", "Screenshot",
        ),
    ),
    Gadget(
        listOf("ガジェット", "電子機器", "パソコン"),
        setOf(
            "Computer", "Laptop", "Keyboard", "Monitor", "Television", "Mobile phone", "Smartphone", "Camera",
            "Headphones", "Speaker", "Gadget", "Electronics", "Technology", "Game", "Video game",
        ),
    ),
    Toy(
        listOf("おもちゃ", "フィギュア", "ぬいぐるみ"),
        setOf("Toy", "Doll", "Stuffed toy", "Figurine", "Lego", "Playground", "Swing"),
    ),
    ;

    /** 画面に出す名前。 */
    val displayName: String get() = names.first()
}

/**
 * [PhotoCategory] についての、決まった値と、調べ方。
 */
object PhotoCategories {

    /** ホームの「いろいろな写真」にするには、そのカテゴリの写真が、これ以上の枚数、必要。 */
    const val MinPhotosForHome = 6

    /** ホームの「いろいろな写真」に並べる、最大の枚数。 */
    const val HomePickupCount = 10

    /**
     * カテゴリの一覧を、フォルダ内画面で開くための、特別なフォルダID。
     * 「お気に入り」が Long.MIN_VALUE を使っているので、その次から。実際のフォルダのIDには、まず現れない値。
     */
    private const val FolderIdBase = Long.MIN_VALUE + 1

    fun folderIdOf(category: PhotoCategory): Long = FolderIdBase + category.ordinal

    /** フォルダ内画面のID → カテゴリ。カテゴリのIDでなければ null。 */
    fun fromFolderId(folderId: Long): PhotoCategory? {
        val index = folderId - FolderIdBase
        return if (index in 0 until PhotoCategory.entries.size) PhotoCategory.entries[index.toInt()] else null
    }

    /**
     * 検索の言葉([query])に、名前があてはまるカテゴリ。
     * 名前が同じなら、あてはまる。2文字以上の言葉は、名前の一部に含まれていても、あてはまる。
     * (1文字の言葉は、同じ名前だけ。「花」で「花火」が混ざらないようにするため)
     * 全角・半角、英字の大文字小文字は区別しない。ひらがなとカタカナは区別する。
     */
    fun matching(query: String): List<PhotoCategory> {
        val needle = normalize(query)
        if (needle.isEmpty()) return emptyList()
        return PhotoCategory.entries.filter { category ->
            category.names.any { name ->
                val normalized = normalize(name)
                normalized == needle || (needle.length >= 2 && normalized.contains(needle))
            }
        }
    }

    /** 検索ボックスの例(「○○」で検索)に使う、言葉。 */
    val searchExamples: List<String> = PhotoCategory.entries.map { it.displayName }

    private fun normalize(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFKC).lowercase(Locale.ROOT).trim()
}
