package com.github.pambrose

import com.github.pambrose.Config.SS_ID
import com.github.pambrose.GoogleApiUtils.getLocalAppCredentials

fun main() {
  TradingSheet(SS_ID, getLocalAppCredentials()).apply {
    println(users)
    println(items)
    println(calcUserSummary())
  }
}
