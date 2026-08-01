package com.nexiplay.app.data

import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.serializer.KotlinXSerializer
import kotlinx.serialization.json.Json
import com.nexiplay.app.BuildConfig

object SupabaseClient {

    // Main database (movies, users, coins, etc.)
    val main = createSupabaseClient(
        supabaseUrl = BuildConfig.SUPABASE_URL,
        supabaseKey = BuildConfig.SUPABASE_ANON_KEY
    ) {
        install(Auth)
        install(Postgrest)
        install(Realtime)
        defaultSerializer = KotlinXSerializer(Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        })
    }

    // Novels database (novels, chapters)
    val novels = createSupabaseClient(
        supabaseUrl = BuildConfig.SUPABASE_NOVELS_URL,
        supabaseKey = BuildConfig.SUPABASE_NOVELS_ANON_KEY
    ) {
        install(Postgrest)
        defaultSerializer = KotlinXSerializer(Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        })
    }
}
