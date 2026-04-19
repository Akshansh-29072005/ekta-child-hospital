package com.example.ektachildhospital.supabase

import com.example.ektachildhospital.api.Doctor
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

class SupabaseDoctorRepository {
    private val client = SupabaseClientProvider.client
    private val json = Json
    private val availabilitySerializer = MapSerializer(String.serializer(), ListSerializer(String.serializer()))

    suspend fun getDoctors(): List<Doctor> = withContext(Dispatchers.IO) {
        client.postgrest["doctors"]
            .select()
            .decodeList<Doctor>()
    }

    suspend fun addDoctor(doctor: Doctor): Doctor = withContext(Dispatchers.IO) {
        client.postgrest["doctors"].insert(doctor)
        doctor
    }

    suspend fun deleteDoctor(id: String) = withContext(Dispatchers.IO) {
        client.postgrest["doctors"].delete {
            filter {
                eq("id", id)
            }
        }
    }

    suspend fun updateDoctor(doctor: Doctor): Doctor = withContext(Dispatchers.IO) {
        client.postgrest["doctors"].update({
            set("name", doctor.name)
            set("email", doctor.email)
            set("specialty", doctor.specialty)
            set("consultation_fee", doctor.consultationFee)
            set("rating", doctor.rating)
            set("image_url", doctor.imageUrl)
            set("availability", json.encodeToJsonElement(availabilitySerializer, doctor.availability))
        }) {
            filter {
                eq("id", doctor.id)
            }
        }
        doctor
    }
}
