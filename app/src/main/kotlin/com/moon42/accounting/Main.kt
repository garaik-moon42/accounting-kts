package com.moon42.accounting

fun main() {
    println("Hello!")
    val downloader = AccountingDownloader(2025, 6)
    downloader.download()
    println("Done.")
}