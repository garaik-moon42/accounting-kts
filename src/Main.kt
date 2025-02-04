fun main() {
    val downloader = AccountingDownloader(2025, 1)
    downloader.download()
    println("Done.")
}