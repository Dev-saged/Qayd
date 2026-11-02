package dev.saged.qayd.data

import androidx.compose.runtime.Immutable

@Immutable
data class Product(
    val id: String,
    val name: String,
    val barcode: String,
    val unit: String,
    val cost: Double,
    val price: Double,
    val minStock: Double,
)

enum class MoveKind(val key: String, val label: String) {
    In("in", "إدخال بضاعة"), Sale("sale", "بيع"), Adjust("adjust", "جرد"), Return("return", "مرتجع");

    companion object { fun of(k: String?) = entries.firstOrNull { it.key == k } }
}

/** حركة مخزون: الكمية موجبة للداخل وسالبة للخارج. الرصيد لا يُخزَّن بل يُحسب منها. */
@Immutable
data class Move(val id: String, val productId: String, val kind: MoveKind, val qty: Double, val cost: Double, val at: Long, val byName: String)

@Immutable
data class ProductStock(val product: Product, val qty: Double) {
    val low get() = product.minStock > 0 && qty <= product.minStock
    val value get() = round2(qty.coerceAtLeast(0.0) * product.cost)
}

@Immutable
data class StockState(val items: List<ProductStock>, val moves: List<Move>) {
    val lowCount get() = items.count { it.low }
    val value get() = round2(items.sumOf { it.value })
    fun of(id: String) = items.firstOrNull { it.product.id == id }
    fun byBarcode(code: String) = items.firstOrNull { it.product.barcode.isNotBlank() && it.product.barcode == code }
    fun history(id: String) = moves.filter { it.productId == id }.sortedByDescending { it.at }

    companion object {
        val Empty = StockState(emptyList(), emptyList())
        fun of(products: List<Product>, moves: List<Move>): StockState {
            val sums = moves.groupBy { it.productId }.mapValues { (_, m) -> round2(m.sumOf { it.qty }) }
            return StockState(products.map { ProductStock(it, sums[it.id] ?: 0.0) }.sortedBy { it.product.name }, moves)
        }
    }
}

@Immutable
data class NewProduct(val name: String, val barcode: String, val unit: String, val cost: Double, val price: Double, val minStock: Double, val qty: Double)
