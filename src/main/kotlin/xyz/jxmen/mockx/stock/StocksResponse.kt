package xyz.jxmen.mockx.stock

data class StocksResponse(
    val stocks: List<StockSummary>,
) {
    data class StockSummary(
        val name: String,
        val symbol: String,
    )

    companion object {
        fun of(stocks: List<Stock>): StocksResponse = StocksResponse(stocks.map { StockSummary(name = it.name, symbol = it.symbol) })
    }
}
