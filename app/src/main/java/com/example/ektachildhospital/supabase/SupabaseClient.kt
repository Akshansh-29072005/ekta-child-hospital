package com.example.ektachildhospital.supabase

import com.example.ektachildhospital.BuildConfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.gotrue.Auth
import io.github.jan.supabase.compose.auth.ComposeAuth
import io.github.jan.supabase.compose.auth.composeAuth
import io.github.jan.supabase.compose.auth.googleNativeLogin
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime

object SupabaseClientProvider {
    private const val SUPABASE_URL = BuildConfig.SUPABASE_URL
    private const val SUPABASE_ANON_KEY = BuildConfig.SUPABASE_ANON_KEY

    val client: SupabaseClient = createSupabaseClient(
        supabaseUrl = SUPABASE_URL,
        supabaseKey = SUPABASE_ANON_KEY
    ) {
        install(Auth)
        install(Postgrest)
        install(Realtime)
        install(ComposeAuth) {
            googleNativeLogin(serverClientId = BuildConfig.GOOGLE_CLIENT_ID)
        }
    }
}
