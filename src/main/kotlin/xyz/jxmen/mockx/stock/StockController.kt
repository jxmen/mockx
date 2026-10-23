package xyz.jxmen.mockx.stock

import org.springframework.data.domain.Sort
import org.springframework.data.web.SortDefault
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import xyz.jxmen.mockx.common.web.InvalidInputException

@RestController
@RequestMapping("/api/stocks")
class StockController(
    private val getStockService: GetStockService,
) {
    companion object {
        private val ALLOWED_SORT_FIELDS = setOf("id", "symbol")
        private val SYMBOL_REGEX = "^[a-zA-Z]{1,10}\$".toRegex()
    }

    /**
     * 종목 목록 조회
     */
    @GetMapping
    fun getStocks(
        @SortDefault(sort = ["id"], direction = Sort.Direction.ASC) sort: Sort,
    ): ResponseEntity<StocksResponse> {
        sort.validateAllowedFields()

        val response = getStockService.getStocks(sort)

        return ResponseEntity.ok(response)
    }

    /**
     * 종목 상세 조회
     */
    @GetMapping("/{symbol}")
    fun getStock(
        @PathVariable symbol: String,
    ): ResponseEntity<StockResponse> {
        if (!SYMBOL_REGEX.matches(symbol)) throw InvalidInputException("symbol", "길이 초과 혹은 영문이 아닌 숫자 포함")

        return ResponseEntity.ok(getStockService.getStock(symbol))
    }

    private fun Sort.validateAllowedFields() {
        if (this.count() > 1) throw InvalidInputException("sort", "정렬 조건은 한 가지만 지정할 수 있습니다.")
        this.firstOrNull { !ALLOWED_SORT_FIELDS.contains(it.property) }?.let {
            throw InvalidInputException("sort", "허용된 정렬 키값이 아닙니다 : ${it.property}")
        }
    }
}
