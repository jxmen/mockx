package xyz.jxmen.mockx.stock

data class StockResponse(
    val name: String,
    val symbol: String,
) {
    companion object {
        fun of(stock: Stock): StockResponse = StockResponse(stock.name, stock.symbol)
    }
}
