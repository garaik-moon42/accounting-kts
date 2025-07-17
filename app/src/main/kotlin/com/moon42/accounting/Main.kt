package com.moon42.accounting

fun main() {
    println("Hello!")
    val downloader = AccountingDownloader(2025, 5)
    downloader.download()
    println("Done.")
}