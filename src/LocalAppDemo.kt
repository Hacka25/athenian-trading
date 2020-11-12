import TradingSheet.Companion.getLocalAppCredentials
import TradingSheet.Companion.ssId

fun main() {
    val tradingSheet = TradingSheet(ssId, getLocalAppCredentials())
    println(tradingSheet.users)
    println(tradingSheet.items)
    println(tradingSheet.calcUserSummary())
}
