package xyz.jxmen.mockx.stock

import org.springframework.data.jpa.repository.JpaRepository

interface StockRepository : JpaRepository<Stock, Long> {
    fun findBySymbol(symbol: String): Stock?
}
