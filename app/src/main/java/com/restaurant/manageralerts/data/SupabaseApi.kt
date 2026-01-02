package com.restaurant.manageralerts.data

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

object SupabaseApi {
    // TODO: set your values
    private const val EDGE_BASE = "https://xdyjwrzqixwzieizrgkc.supabase.co/functions/v1/manager-api"
    private const val SUPABASE_ANON = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InhkeWp3cnpxaXh3emllaXpyZ2tjIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NjcxMTkyMDEsImV4cCI6MjA4MjY5NTIwMX0.RypEWdT4oxcfQb54Rx3LlCmrnIImVRXo_KK9RlOhyqc"

    private val client = OkHttpClient()
    private val json = "application/json; charset=utf-8".toMediaType()

    fun registerDevice(deviceId: String, name: String, fcmToken: String, platform: String): Boolean {
        val body = """
          {
            "device_id":"$deviceId",
            "name":${name.jsonEscape()},
            "token":"$fcmToken",
            "platform":"$platform"
          }
        """.trimIndent()

        val req = Request.Builder()
            .url("$EDGE_BASE/register-device")
            .post(body.toRequestBody(json))
            .addHeader("Authorization", "Bearer $SUPABASE_ANON")
            .addHeader("Content-Type", "application/json")
            .build()

        client.newCall(req).execute().use { resp ->
            return resp.isSuccessful
        }
    }

    fun claimCall(requestId: String, deviceId: String, name: String): Boolean {
        val body = """
          {
            "request_id":"$requestId",
            "device_id":"$deviceId",
            "name":${name.jsonEscape()}
          }
        """.trimIndent()

        val req = Request.Builder()
            .url("$EDGE_BASE/claim-call")
            .post(body.toRequestBody(json))
            .addHeader("Authorization", "Bearer $SUPABASE_ANON")
            .addHeader("Content-Type", "application/json")
            .build()

        client.newCall(req).execute().use { resp ->
            // If already taken, backend can return 409; still OK for us (we just want it dismissed).
            return resp.isSuccessful || resp.code == 409
        }
    }
}

private fun String.jsonEscape(): String {
    val s = this.replace("\\", "\\\\").replace("\"", "\\\"")
    return "\"$s\""
}
