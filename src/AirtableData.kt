import com.google.gson.annotations.SerializedName
import java.math.BigDecimal
import java.util.*

data class DocumentType(val name: String)
data class Partner(val name: String, val notes: String)
data class RegistryItem(val seq: Int) {
    val direction: String? = null
    val createdOn: Date? = null
    @Transient
    var partner: Partner? = null
    @SerializedName("partner")
    val assignedPartnerIds: List<String>? = null
    @Transient
    var type: DocumentType? = null
    @SerializedName("type")
    val assignedTypeIds: List<String>? = null
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

class AirtableData(year: Short, month: Short) {
    val documentTypes: Map<String, DocumentType> = AirtableClient.fetchInstanceMap(Config.Airtable.typeTableId, DocumentType::class.java)
    val partners: Map<String, Partner> = AirtableClient.fetchInstanceMap(Config.Airtable.partnerTableId, Partner::class.java)
    val registryItems: List<RegistryItem> =
        AirtableClient
            .fetchAirtableData(Config.Airtable.recordTableId, FilterFormulaBuilder.forRegistryItems(year, month), RegistryItem::class.java)
            .map { it.fields }
}
