package dev.saged.qayd.data

import androidx.compose.runtime.Immutable

@Immutable
data class Supplier(val id: String, val name: String, val phone: String)

enum class STxKind(val key: String, val label: String) {
    Purchase("purchase", "فاتورة شراء"), Payment("payment", "دفعة للمورد");

    companion object { fun of(k: String?) = entries.firstOrNull { it.key == k } }
}

@Immutable
data class STx(val id: String, val supplierId: String, val kind: STxKind, val amount: Double, val at: Long, val note: String, val byName: String)

/** ما على المحل للمورد = المشتريات − الدفعات. */
@Immutable
data class SupplierBalance(val supplier: Supplier, val owed: Double, val last: Long)

@Immutable
data class SupplierState(val items: List<SupplierBalance>, val txs: List<STx>) {
    val totalOwed get() = round2(items.sumOf { it.owed.coerceAtLeast(0.0) })
    fun of(id: String) = items.firstOrNull { it.supplier.id == id }
    fun history(id: String) = txs.filter { it.supplierId == id }.sortedByDescending { it.at }

    companion object {
        val Empty = SupplierState(emptyList(), emptyList())
        fun of(suppliers: List<Supplier>, txs: List<STx>): SupplierState {
            val by = txs.groupBy { it.supplierId }
            return SupplierState(
                suppliers.map { s ->
                    val t = by[s.id].orEmpty()
                    SupplierBalance(s, round2(t.sumOf { if (it.kind == STxKind.Purchase) it.amount else -it.amount }), t.maxOfOrNull { it.at } ?: 0L)
                }.sortedByDescending { it.owed },
                txs,
            )
        }
    }
}
