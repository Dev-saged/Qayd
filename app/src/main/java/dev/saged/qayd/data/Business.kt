package dev.saged.qayd.data

/** نوع نشاط المحل: يجهّز الوحدات وأزرار البيع السريع المناسبة له عند الإنشاء. */
enum class BizType(val key: String, val label: String, val modules: Set<Module>, val quickAdds: List<Int>) {
    General("general", "عام", setOf(Module.Debts, Module.Sms), listOf(5, 10, 20, 50)),
    Grocery("grocery", "بقالة وسوبرماركت", setOf(Module.Debts, Module.Inventory, Module.Suppliers, Module.Sms), listOf(1, 5, 10, 20)),
    Clothing("clothing", "ملابس وأحذية", setOf(Module.Debts, Module.Inventory, Module.Suppliers, Module.Layaway, Module.Sms), listOf(20, 50, 100, 200)),
    Food("food", "مطعم، مشاوي، فلافل", setOf(Module.Debts, Module.Menu, Module.Suppliers, Module.Sms), listOf(5, 10, 15, 20)),
    Household("household", "أدوات منزلية", setOf(Module.Debts, Module.Inventory, Module.Suppliers, Module.Layaway, Module.Sms), listOf(10, 20, 50, 100)),
    Batteries("batteries", "بطاريات سيارات", setOf(Module.Debts, Module.Inventory, Module.Suppliers, Module.Batteries, Module.Sms), listOf(50, 100, 200, 300)),
    Games("games", "صالة ألعاب وكهرباء", setOf(Module.Debts, Module.Ps, Module.Electricity, Module.Sms), listOf(5, 10, 20, 50));

    companion object {
        fun of(key: String?) = entries.firstOrNull { it.key == key } ?: General
    }
}
