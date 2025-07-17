package com.moon42.accounting

import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.YearMonth
import java.util.*

private const val AIRTABLE_URI: String = "https://api.airtable.com/v0/%s/%s"

data class AirtableRecord<T>(val id: String, val createdTime: Date, val fields: T)
data class AirtableResponse<T>(val records: List<AirtableRecord<T>>, val offset: String)

object FilterFormulaBuilder {

    fun forInvoicesOfMonth(year: Short, month: Short):String {
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
}

object AirtableClient {

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
            append(AIRTABLE_URI.format(Config.data.airtable.baseId, tableId))
            if (queryParams.isNotEmpty()) {
                append(queryParams.entries.joinToString(separator = "&", prefix = "?") { (param, value) -> "${param}=${value}" })
            }
        })
        return HttpRequest.newBuilder()
            .uri(uri)
            .header("Authorization", "Bearer ${Config.data.airtable.token}")
            .GET()
            .build()
    }


    fun <T> fetchAirtableData(tableId: String, filterFormula: String?, targetClass: Class<T>):List<AirtableRecord<T>> {
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

    fun <T> fetchInstanceMap(airtableTableId: String, targetClass: Class<T>):Map<String, T> {
        val instanceList:List<AirtableRecord<T>> = fetchAirtableData(airtableTableId, null, targetClass)
        return instanceList.associate { it.id to it.fields }
    }
}
