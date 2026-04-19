package com.example.ektachildhospital.supabase

import io.github.jan.supabase.compose.auth.composable.NativeSignInResult
import io.github.jan.supabase.compose.auth.composeAuth
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.jsonPrimitive

@Serializable
data class Profile(
    val id: String,
    @SerialName("full_name")
    val full_name: String? = null,
    val phone: String? = null,
    val role: String = "patient",
    @SerialName("created_at")
    val createdAt: String? = null
)

class SupabaseAuthRepository {
    private val client = SupabaseClientProvider.client

    val composeAuth = client.composeAuth

    fun getCurrentUser() = client.auth.currentUserOrNull()

    suspend fun getProfile(): Profile? = withContext(Dispatchers.IO) {
        val userId = client.auth.currentUserOrNull()?.id ?: return@withContext null
        try {
            client.postgrest["profiles"]
                .select(columns = Columns.ALL) {
                    filter {
                        eq("id", userId)
                    }
                }
                .decodeSingleOrNull<Profile>()
        } catch (_: Exception) {
            null
        }
    }

    suspend fun updatePhone(phone: String) = withContext(Dispatchers.IO) {
        val userId = requireNotNull(client.auth.currentUserOrNull()?.id) { "User session not found" }
        client.postgrest["profiles"].update({
            set("phone", phone)
        }) {
            filter { eq("id", userId) }
        }
    }

    suspend fun handleSignInResult(result: NativeSignInResult): Profile? {
        if (result !is NativeSignInResult.Success) return null
        
        return withContext(Dispatchers.IO) {
            val user = try {
                client.auth.currentUserOrNull()
            } catch (_: Exception) {
                null
            } ?: return@withContext null
            
            // 1. Try to get profile from DB
            var profile = try {
                client.postgrest["profiles"]
                    .select(columns = Columns.ALL) {
                        filter {
                            eq("id", user.id)
                        }
                    }.decodeSingleOrNull<Profile>()
            } catch (_: Exception) {
                null
            }

            // 2. Fallback to Metadata if name is missing
            val metaName = user.userMetadata?.get("full_name")?.jsonPrimitive?.content 
                ?: user.userMetadata?.get("name")?.jsonPrimitive?.content

            if (profile == null) {
                // If no profile exists, create it in the database
                profile = Profile(
                    id = user.id,
                    full_name = metaName ?: "User",
                    role = "patient"
                )
                try {
                    client.postgrest["profiles"].insert(profile)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            } else if (profile.full_name.isNullOrBlank() && !metaName.isNullOrBlank()) {
                // If profile exists but name is blank, update it in the database
                profile = profile.copy(full_name = metaName)
                try {
                    client.postgrest["profiles"].update({
                        set("full_name", metaName)
                    }) {
                        filter { eq("id", user.id) }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            profile
        }
    }

    suspend fun logout() {
        withContext(Dispatchers.IO) {
            try {
                client.auth.signOut()
            } catch (_: Exception) {
                // Ignore
            }
        }
    }
}
