package com.moon42.accounting

import com.google.gson.annotations.SerializedName
import com.moon42.airtable.AirtableClient
import java.math.BigDecimal
import java.time.YearMonth
import java.util.*

data class DocumentType(val name: String)
data class Partner(val name: String, val notes: String)
data class RegistryItem(val seq: Int) {
    val direction: String? = null
    val createdOn: Date? = null
    val partner: Partner?
        get() = if (!assignedPartnerIds.isNullOrEmpty()) Airtable.partners[assignedPartnerIds.first()] else null
    @SerializedName("partner")
    val assignedPartnerIds: List<String>? = null
    val type: DocumentType?
        get() = if (!assignedTypeIds.isNullOrEmpty()) Airtable.documentTypes[assignedTypeIds.first()] else null
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

object Airtable {
    val airtableClient = AirtableClient(Config.data.airtable.baseId, Config.data.airtable.token)
    val documentTypes: Map<String, DocumentType> = airtableClient.fetchInstanceMap(Config.data.airtable.typeTableId, DocumentType::class.java)
    val partners: Map<String, Partner> = airtableClient.fetchInstanceMap(Config.data.airtable.partnerTableId, Partner::class.java)

    fun filterForInvoicesOfMonth(year: Short, month: Short):String {
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

    fun fetchInvoicesOfMonth(year: Short, month: Short): List<RegistryItem> = airtableClient
        .fetchAirtableData(Config.data.airtable.recordTableId, filterForInvoicesOfMonth(year, month), RegistryItem::class.java)
        .map { it.fields }
}

