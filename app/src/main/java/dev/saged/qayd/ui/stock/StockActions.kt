package dev.saged.qayd.ui.stock

import dev.saged.qayd.data.NewProduct
import dev.saged.qayd.data.Product
import dev.saged.qayd.data.STxKind

/** عمليات الأصناف والموردين. */
class StockActions(
    val addProduct: (NewProduct) -> Unit = {},
    val editProduct: (Product) -> Unit = {},
    val deleteProduct: (Product) -> Unit = {},
    val sell: (Product, Double, String) -> Unit = { _, _, _ -> },
    val receive: (Product, Double, Double, String?, String) -> Unit = { _, _, _, _, _ -> },
    val adjust: (Product, Double, Double, String) -> Unit = { _, _, _, _ -> },
    val returnItem: (Product, Double, String) -> Unit = { _, _, _ -> },
    val addSupplier: (String, String) -> String = { _, _ -> "" },
    val supplierTx: (String, STxKind, Double, String) -> Unit = { _, _, _, _ -> },
) {
    companion object { val None = StockActions() }
}
