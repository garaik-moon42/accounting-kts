package com.moon42.accounting

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
import com.google.api.services.drive.model.File as GoogleFile

object Logger {
    fun log(msg: String?) {
        println(msg)
    }
}

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
        val drive = Drive.Builder(httpTransport, GsonFactory.getDefaultInstance(), credentials)
            .setApplicationName(Config.data.applicationName)
            .build()
            .files()

        File(Config.data.download.targetDir).also(File::deleteRecursively).mkdirs()

        val invoicesOfMonth = Airtable.fetchInvoicesOfMonth(year, month)

        runBlocking {
            for (invoiceData in invoicesOfMonth) {
                launch {
                    downloadFile(invoiceData, drive)
                }
            }
        }
    }

    private suspend fun downloadFile(invoiceData: RegistryItem, drive: Drive.Files) {
        Logger.log("Downloading file of registry item #${invoiceData.seq} with name '${invoiceData.name}'...")
        withContext(Dispatchers.IO) {
            val googleDriveFile = drive.get(invoiceData.googleDriveId).execute()
            val targetDirPath:String = if (Config.data.download.separatePartnerDirs) {
                File(Config.data.download.targetDir + File.separator + composeDirNameFor(invoiceData)).also(File::mkdirs).absolutePath
            }
            else {
                Config.data.download.targetDir
            }
            val targetFilePath = targetDirPath + File.separator + composeFileNameFor(googleDriveFile, invoiceData)
            drive.get(invoiceData.googleDriveId).executeMediaAndDownloadTo(FileOutputStream(targetFilePath))
            Logger.log("File downloaded for #${invoiceData.seq}: $targetDirPath")
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
                if (Config.data.download.separatePartnerDirs) {
                    append("%05d".format(seq))
                    append("_")
                    partner?.let { append(sanitizeFileName(it.name)) }
                }
                else {
                    partner?.let { append(sanitizeFileName(it.name)) }
                    append("_")
                    append("%05d".format(seq))
                }
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
        val instr = AccountingDownloader::class.java.getResourceAsStream(Config.data.google.clientSecret)
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
