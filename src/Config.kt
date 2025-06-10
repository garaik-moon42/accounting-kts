import com.google.gson.Gson
import java.io.InputStreamReader

object Config {
    val data: ConfigData

    init {
        val configFileName = "accounting-config.json"
        val configFileUrl = this::class.java.classLoader.getResource(configFileName)
            ?: error("Config file not found: $configFileName")
        data = InputStreamReader(configFileUrl.openStream()).use {
            Gson().fromJson(it, ConfigData::class.java)
        }
    }
}

data class ConfigData (
    val applicationName: String,
    val download: DownloadData,
    val airtable: AirtableConfig,
    val google: GoogleConfig
)

data class DownloadData (
    val targetDir: String,
    val separatePartnerDirs: Boolean
)

data class AirtableConfig (
    val baseId: String,
    val recordTableId: String,
    val partnerTableId: String,
    val typeTableId: String,
    val token: String
)

data class GoogleConfig (
    val clientSecret: String,
    val tokenDir: String
)