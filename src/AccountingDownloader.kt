import com.google.api.client.auth.oauth2.Credential
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.client.util.store.FileDataStoreFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.text.DecimalFormat
import java.util.*
import com.google.api.services.drive.model.File as GoogleFile

object Logger {
    fun log(msg: String?) {
        println(msg)
    }
}

object Config {
    private val properties: Properties

    object Airtable {
        val baseId: String = properties.getProperty("airtable.baseId")
        val recordTableId: String = properties.getProperty("airtable.recordTableId")
        val partnerTableId: String = properties.getProperty("airtable.partnerTableId")
        val typeTableId: String = properties.getProperty("airtable.typeTableId")
        val token: String = properties.getProperty("airtable.token")
    }

    object Google {
        val apiClientKey = properties.getProperty("google.api.clientKey")
    }

    init {
        val propertiesFileName = "accounting.properties"
        val propertiesFileUrl = this::class.java.classLoader.getResource(propertiesFileName) ?: error("Properties file '$propertiesFileName' not found.")
        properties = Properties().apply { load(propertiesFileUrl.openStream()) }
    }
}

const val TARGET_DIR = "accounting-files"
val MIME_MAP = mapOf(
    "application/pdf" to "pdf",
    "text/csv" to "csv",
    "application/msword" to "doc",
    "application/vnd.openxmlformats-officedocument.wordprocessingml.document" to "docx",
    "image/jpeg" to "jpg",
    "image/png" to "png",
    "application/vnd.ms-powerpoint" to "ppt",
    "application/vnd.openxmlformats-officedocument.presentationml.presentation" to "pptx",
    "text/plain" to "txt",
    "application/vnd.ms-excel" to "xls",
    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" to "xlsx",
    "application/zip" to "zip"
)

class AccountingDownloader(private val year: Short, private val month: Short) {

    fun download() {
        val monthFormat = DecimalFormat("00")
        println("Downloading files for ${year}.${monthFormat.format(month)}...")

        val httpTransport = GoogleNetHttpTransport.newTrustedTransport()
        val credentials = getCredentials(httpTransport)
        val drive = Drive.Builder(httpTransport, GsonFactory.getDefaultInstance(), credentials).build().files()

        File(TARGET_DIR).also(File::deleteRecursively).mkdirs()

        val airtableData = AirtableData(year, month)

        runBlocking {
            for (ri in airtableData.registryItems) {
                launch {
                    downloadFile(ri, drive)
                }
            }
        }
    }

    private suspend fun downloadFile(ri: RegistryItem, drive: Drive.Files) {
        Logger.log("Downloading file of registry item #${ri.seq} with name '${ri.name}'...")
        withContext(Dispatchers.IO) {
            val googleDriveFile = drive.get(ri.googleDriveId).execute()
            val targetPartnerDir = File(TARGET_DIR + File.separator + composeDirNameFor(ri)).also(File::mkdirs)
            val targetFilePath = targetPartnerDir.absolutePath + File.separator + composeFileNameFor(googleDriveFile, ri)
            drive.get(ri.googleDriveId).executeMediaAndDownloadTo(FileOutputStream(targetFilePath))
            Logger.log("File downloaded for #${ri.seq}: $targetFilePath")
        }
    }

    private fun sanitizeFileName(fileName: String): String {
        return fileName.replace("""[<>:"/\\|?*.]""".toRegex(), "_")
    }

    private fun composeDirNameFor(registryItem: RegistryItem): String {
        return sanitizeFileName(registryItem.partner?.name ?: error("No partner found for registry item #${registryItem.seq}.")).trim()
    }

    private fun  composeFileNameFor(googleDriveFile: GoogleFile, registryItem: RegistryItem): String {
        return buildString {
            with (registryItem) {
                append("%05d".format(seq))
                append("_")
                partner?.let { append(sanitizeFileName(it.name)) }
                append("_")
                type?.let { append(sanitizeFileName(it.name)) }
                append("_")
                append("%04d%02d".format(year, month))
                append(".")
                append(MIME_MAP[googleDriveFile.mimeType] ?: error("Mime type not found '${googleDriveFile.mimeType}'."))
            }
        }
    }

    private fun getCredentials(httpTransport: NetHttpTransport?): Credential {
        val instr = AccountingDownloader::class.java.getResourceAsStream(Config.Google.apiClientKey)
            ?: error("Missing credentials.json file")
        val clientSecrets = GoogleClientSecrets.load(GsonFactory.getDefaultInstance(), InputStreamReader(instr))
        val flow = GoogleAuthorizationCodeFlow.Builder(
            httpTransport, GsonFactory.getDefaultInstance(), clientSecrets, listOf(DriveScopes.DRIVE_READONLY))
            .setDataStoreFactory(FileDataStoreFactory(File("tokens")))
            .setAccessType("offline")
            .build()
        val receiver = LocalServerReceiver.Builder().setPort(8888).build()
        return AuthorizationCodeInstalledApp(flow, receiver).authorize("user")
    }
}
