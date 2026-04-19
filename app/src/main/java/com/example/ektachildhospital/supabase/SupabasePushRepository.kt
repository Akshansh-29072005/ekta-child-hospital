package com.example.ektachildhospital.supabase

import android.util.Log
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DeviceTokenRow(
    @SerialName("user_id")
    val userId: String,
    val token: String,
    val platform: String = "android"
)

class SupabasePushRepository {
    private val client = SupabaseClientProvider.client

    suspend fun saveDeviceToken(token: String) = withContext(Dispatchers.IO) {
        try {
            // Use RPC to bypass RLS conflicts during token ownership transfer
            client.postgrest.rpc(
                function = "sync_device_token",
                parameters = mapOf("fcm_token" to token)
            )
        } catch (e: Exception) {
            Log.e("PushRepository", "Failed to sync device token: ${e.message}", e)
        }
    }

    suspend fun deleteDeviceToken(token: String) = withContext(Dispatchers.IO) {
        try {
            client.postgrest["device_tokens"].delete {
                filter {
                    eq("token", token)
                }
            }
        } catch (e: Exception) {
            Log.e("PushRepository", "Failed to delete device token", e)
        }
    }
}
