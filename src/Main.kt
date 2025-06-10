fun main() {
    val downloader = AccountingDownloader(2025, 5)
    downloader.download()
    println("Done.")
}