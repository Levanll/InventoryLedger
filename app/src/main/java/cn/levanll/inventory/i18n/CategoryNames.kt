package cn.levanll.inventory.i18n

/**
 * 分类名称的中英对照。
 *
 * 为什么单独做映射而不是把分类名存两份：
 *   分类名是数据库主键的一部分（UNIQUE(name, parent_id)），
 *   且用户可自建分类。存两份会让「用户新建的分类」只有当前语言一种名字，
 *   切换后另一半是空的。
 *
 *   这里只给「系统预置分类」提供英文名。用户自建的分类照原样显示 ——
 *   这是诚实的：系统不知道用户自己起的名字该怎么翻译。
 */
object CategoryNames {

    /** 中文名 -> 英文名。未收录的返回原值 */
    private val MAP: Map<String, String> = mapOf(
        // 一级
        "服装" to "Clothing",
        "食品" to "Food",
        "美妆个护" to "Beauty & Care",
        "日用家居" to "Household",
        "数码配件" to "Digital Accessories",
        "药品保健" to "Medicine & Health",
        "未分类" to "Uncategorized",

        // 服装
        "上装" to "Tops",
        "下装" to "Bottoms",
        "鞋靴" to "Shoes",
        "配饰" to "Accessories",
        "T恤" to "T-Shirts",
        "衬衫" to "Shirts",
        "卫衣" to "Hoodies",
        "外套" to "Outerwear",
        "裤装" to "Pants",
        "裙装" to "Skirts",
        "运动鞋" to "Sneakers",
        "皮鞋" to "Leather Shoes",
        "拖鞋" to "Slippers",
        "帽子" to "Hats",
        "袜类" to "Socks",

        // 食品
        "生鲜" to "Fresh",
        "零食" to "Snacks",
        "饮料" to "Beverages",
        "调味" to "Condiments",
        "主食" to "Staples",
        "蔬菜" to "Vegetables",
        "水果" to "Fruits",
        "肉禽蛋" to "Meat & Eggs",
        "乳制品" to "Dairy",
        "膨化" to "Chips",
        "饼干糕点" to "Biscuits",
        "坚果糖果" to "Nuts & Candy",
        "碳酸" to "Soda",
        "果汁" to "Juice",
        "茶咖" to "Tea & Coffee",
        "酱料" to "Sauces",
        "粮油" to "Oil & Grain",
        "米面" to "Rice & Flour",
        "速食" to "Instant Food",

        // 美妆个护
        "护肤" to "Skincare",
        "彩妆" to "Makeup",
        "洗护" to "Hair & Body",
        "香氛" to "Fragrance",
        "精华" to "Serum",
        "面霜" to "Cream",
        "面膜" to "Mask",
        "洁面" to "Cleanser",
        "水乳" to "Toner & Lotion",
        "口红" to "Lipstick",
        "底妆" to "Foundation",
        "眼妆" to "Eye Makeup",
        "洗发" to "Shampoo",
        "沐浴" to "Body Wash",
        "口腔" to "Oral Care",
        "香水" to "Perfume",
        "香膏" to "Solid Perfume",

        // 日用家居
        "清洁" to "Cleaning",
        "纸品" to "Paper",
        "收纳" to "Storage",
        "衣物清洁" to "Laundry",
        "厨卫清洁" to "Kitchen & Bath",
        "抽纸" to "Facial Tissue",
        "卷纸" to "Toilet Paper",
        "收纳箱" to "Storage Box",
        "收纳袋" to "Storage Bag",

        // 数码配件
        "线材" to "Cables",
        "充电" to "Charging",
        "数据线" to "Data Cable",
        "转接头" to "Adapter",
        "充电器" to "Charger",
        "移动电源" to "Power Bank",

        // 药品保健
        "常备药" to "First Aid",
        "保健品" to "Supplements",
        "感冒药" to "Cold Medicine",
        "肠胃药" to "Stomach Medicine",
        "外用药" to "Topical Medicine",
        "维生素" to "Vitamins",
        "营养品" to "Nutrition"
    )

    /** 取当前语言下的分类名；未收录的返回原名 */
    fun of(zhName: String): String {
        if (I18n.lang == Lang.ZH) return zhName
        return MAP[zhName] ?: zhName
    }

    /** 取路径（如「美妆个护 / 护肤 / 精华」）的本地化版本 */
    fun ofPath(path: String): String {
        if (I18n.lang == Lang.ZH) return path
        return path.split(" / ").joinToString(" / ") { of(it) }
    }
}
