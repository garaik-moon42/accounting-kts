fun main() {
    val downloader = AccountingDownloader(2025, 2)
    downloader.download()
    println("Done.")
}