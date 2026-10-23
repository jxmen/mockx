package xyz.jxmen.mockx.stock

import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class GetStockService(
    private val stockRepository: StockRepository,
) {
    fun getStocks(sort: Sort): StocksResponse {
        // NOTE: 페이지네이션은 아직 지원하지 않는다.
        val stocks = stockRepository.findAll(sort)

        return StocksResponse.of(stocks)
    }

    fun getStock(symbol: String): StockResponse {
        val stock = stockRepository.findBySymbol(symbol.uppercase()) ?: throw StockNotFoundException(symbol)

        return StockResponse.of(stock)
    }
}
