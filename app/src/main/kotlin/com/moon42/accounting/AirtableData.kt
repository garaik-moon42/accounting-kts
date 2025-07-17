package com.moon42.accounting

import com.google.gson.annotations.SerializedName
import java.math.BigDecimal
import java.util.*

data class DocumentType(val name: String)
data class Partner(val name: String, val notes: String)
data class RegistryItem(val seq: Int) {
    val direction: String? = null
    val createdOn: Date? = null
    val partner: Partner?
        get() = if (!assignedPartnerIds.isNullOrEmpty()) AirtableCache.partners[assignedPartnerIds.first()] else null
    @SerializedName("partner")
    val assignedPartnerIds: List<String>? = null
    val type: DocumentType?
        get() = if (!assignedTypeIds.isNullOrEmpty()) AirtableCache.documentTypes[assignedTypeIds.first()] else null
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

private object AirtableCache {
    val documentTypes: Map<String, DocumentType> = AirtableClient.fetchInstanceMap(Config.data.airtable.typeTableId, DocumentType::class.java)
    val partners: Map<String, Partner> = AirtableClient.fetchInstanceMap(Config.data.airtable.partnerTableId, Partner::class.java)
}

fun fetchInvoicesOfMonth(year: Short, month: Short): List<RegistryItem> = AirtableClient
            .fetchAirtableData(Config.data.airtable.recordTableId, FilterFormulaBuilder.forInvoicesOfMonth(year, month), RegistryItem::class.java)
            .map { it.fields }
