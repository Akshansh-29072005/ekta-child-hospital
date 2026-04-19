package com.example.ektachildhospital.api

import android.content.Context
import android.content.Intent
import com.example.ektachildhospital.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object GlobalAuthHandler {
    fun handleUnauthorized(context: Context) {
        val tokenManager = TokenManager(context)
        CoroutineScope(Dispatchers.IO).launch {
            tokenManager.clearAuthData()
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            context.startActivity(intent)
        }
    }
}