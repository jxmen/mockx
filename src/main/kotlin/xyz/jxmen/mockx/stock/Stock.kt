package xyz.jxmen.mockx.stock

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import xyz.jxmen.mockx.common.jpa.BaseTimeEntity

/**
 * 종목
 */
@Entity
@Table(
    name = "stock",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_stock_symbol", columnNames = ["symbol"]),
    ],
)
class Stock(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L,
    @Column(nullable = false, length = 100)
    val name: String, // Micron Technology
    @Column(nullable = false, length = 10)
    val symbol: String, // MU
) : BaseTimeEntity()
