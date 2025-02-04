import com.google.gson.GsonBuilder
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import java.math.BigDecimal
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.YearMonth
import java.util.*
import kotlin.collections.ArrayList

private const val BASE_ID: String = "appODavvV60uXOqnC"
private const val RECORD_TABLE_ID: String = "tblKKtw8kgNVfK4pa"
private const val PARTNER_TABLE_ID: String = "tblDAC6SuH7nBI1OP"
private const val TYPE_TABLE_ID: String = "tblxpOuxH7oWtlda8"
private const val AIRTABLE_URI: String = "https://api.airtable.com/v0/%s/%s"
private const val AIRTABLE_TOKEN: String = "patmAXskVHOs01sPn.094ed9e354ba953b0473cc059e15d143fe4b164ee7ee45f921813f88223fdc61"

data class AirtableRecord<T>(val id: String, val createdTime: Date, val fields: T)
data class AirtableResponse<T>(val records: List<AirtableRecord<T>>, val offset: String)
data class DocumentType(val name: String)
data class Partner(val name: String, val notes: String)
data class RegistryItem(val seq: Int) {
    val direction: String? = null
    val createdOn: Date? = null
    @Transient
    var partner: Partner? = null
    @SerializedName("partner")
    val assignedPartners: List<String>? = null
    @Transient
    var type: DocumentType? = null
    @SerializedName("type")
    val assignedTypes: List<String>? = null
    val keywords: List<String>? = null
    val notes: String? = null
    val googleDriveId: String? = null
    val googleDriveURL: String? = null
    val id: String? = null
    val amount: BigDecimal? = null
    val currency: String? = null
    val refDate: Date? = null
    val dueDate: Date? = null
    val modifiedOn: Date? = null
    val name: String? = null
    val mimeType: String? = null

    override fun toString(): String {
        return "RegistryItem(name=$name, notes=$notes, keywords=$keywords, direction=$direction, seq=$seq)"
    }
}

class AirtableClient(private val year: Short, private val month: Short) {

    lateinit var registryItems: List<RegistryItem>

    fun buildFilterFormula():String {
        val types = listOf("Átutalásos számla", "Díjbekérő", "Kártyás számla", "Készpénzes számla",
            "Proforma számla", "Útelszámolás", "Sztornó számla", "Érvénytelenítő számla",
            "Számlával egy tekintet alá eső okirat", "Teljesítési igazolás")
        return buildString {
            val from = "%02d/%02d/%02d".format(month, 1, year)
            val until = "%02d/%02d/%02d".format(month, YearMonth.of(year.toInt(), month.toInt()).atEndOfMonth().dayOfMonth, year)
            append("AND(")
            append("OR(IS_SAME(refDate, \"$from\"), IS_AFTER(refDate, \"$from\")),")
            append("OR(IS_SAME(refDate, \"$until\"), IS_BEFORE(refDate, \"$until\")),")
            append("OR(").append(types.joinToString(", ") { s -> "type=\"$s\"" }).append(")")
            append(")")
        }
    }

    private fun buildAirtableRequest(tableId: String, offset: String?, filterFormula: String?): HttpRequest {
        val queryParams = buildMap {
            if (!offset.isNullOrBlank()) {
                put("offset", URLEncoder.encode(offset, "UTF-8"))
            }
            if (!filterFormula.isNullOrBlank()) {
                put("filterBy" +
                        "Formula", URLEncoder.encode(filterFormula, "UTF-8"))
            }
        }
        val uri = URI.create(buildString {
            append(AIRTABLE_URI.format(BASE_ID, tableId));
            if (queryParams.isNotEmpty()) {
                append(queryParams.entries.joinToString(separator = "&", prefix = "?") { (param, value) -> "${param}=${value}" })
            }
        })
        return HttpRequest.newBuilder()
            .uri(uri)
            .header("Authorization", "Bearer $AIRTABLE_TOKEN")
            .GET()
            .build()
    }


    private fun <T> fetchAirtableData(tableId: String, filterFormula: String?, targetClass: Class<T>):List<AirtableRecord<T>> {
        val http = HttpClient.newHttpClient()
        val gson = GsonBuilder().create()
        val data = ArrayList<AirtableRecord<T>>()
        var offset:String? = null
        do {
            Logger.log("Fetching data from airtable (tableId='$tableId', offset='$offset')...")
            val httpResponse = http.send(buildAirtableRequest(tableId, offset, filterFormula), HttpResponse.BodyHandlers.ofString())
            if (httpResponse.statusCode() != 200) {
                throw IllegalStateException("Error: ${httpResponse.body()}")
            }
            val airtableResponse:AirtableResponse<T> = gson.fromJson(httpResponse.body(), TypeToken.getParameterized(AirtableResponse::class.java, targetClass).type)
            data.addAll(airtableResponse.records)
            offset = airtableResponse.offset
        }
        while (!offset.isNullOrBlank())
        Logger.log("...retrieved ${data.size} records.")
        return data
    }

    private fun <T> fetchInstanceMap(airtableTableId: String, targetClass: Class<T>):Map<String, T> {
        val instanceList:List<AirtableRecord<T>> = fetchAirtableData(airtableTableId, null, targetClass)
        return instanceList.associate { it.id to it.fields }
    }

    fun fetch() {
        val documentTypes = fetchInstanceMap(TYPE_TABLE_ID, DocumentType::class.java)
        val partners = fetchInstanceMap(PARTNER_TABLE_ID, Partner::class.java)
        val airtableRecords = fetchAirtableData(RECORD_TABLE_ID, buildFilterFormula(), RegistryItem::class.java)
        registryItems = airtableRecords.map { arri:AirtableRecord<RegistryItem> -> arri.fields }.onEach { ri ->
            ri.assignedTypes?.firstOrNull()?.let { ri.type = documentTypes[it] }
            ri.assignedPartners?.firstOrNull()?.let { ri.partner = partners[it] }
        }
    }
}
