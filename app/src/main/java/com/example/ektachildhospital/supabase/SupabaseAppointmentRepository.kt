package com.example.ektachildhospital.supabase

import com.example.ektachildhospital.api.AdminStats
import com.example.ektachildhospital.api.Appointment
import com.example.ektachildhospital.api.Doctor
import com.example.ektachildhospital.supabase.Profile
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

class SupabaseAppointmentRepository {
    private val client = SupabaseClientProvider.client

    fun getCurrentUserId(): String? = client.auth.currentUserOrNull()?.id

    @Serializable
    private data class AppointmentRow(
        @SerialName("id")
        val id: String,
        @SerialName("patient_id")
        val patientId: String,
        @SerialName("doctor_id")
        val doctorId: String,
        @SerialName("appointment_date")
        val date: String,
        @SerialName("appointment_time")
        val time: String,
        val status: String,
        @SerialName("created_at")
        val createdAt: String? = null
    )

    @Serializable
    private data class AppointmentInsertRow(
        @SerialName("id")
        val id: String,
        @SerialName("patient_id")
        val patientId: String,
        @SerialName("doctor_id")
        val doctorId: String,
        @SerialName("appointment_date")
        val date: String,
        @SerialName("appointment_time")
        val time: String,
        val status: String
    )

    private suspend fun loadDoctorNames(): Map<String, String> {
        val doctors = client.postgrest["doctors"]
            .select()
            .decodeList<Doctor>()

        return doctors.associate { doctor -> doctor.id to doctor.name }
    }

    suspend fun getMyAppointments(): List<Appointment> = withContext(Dispatchers.IO) {
        val userId = requireNotNull(client.auth.currentUserOrNull()?.id) {
            "User session not found"
        }

        val rows = client.postgrest["appointments"]
            .select {
                filter {
                    eq("patient_id", userId)
                }
            }
            .decodeList<AppointmentRow>()

        val doctorNames = loadDoctorNames()
        rows.map { row -> row.toAppointment(doctorNames) }
    }

    suspend fun getPendingAppointments(): List<Appointment> = withContext(Dispatchers.IO) {
        val rows = client.postgrest["appointments"]
            .select {
                filter {
                    eq("status", "pending")
                }
            }
            .decodeList<AppointmentRow>()

        val doctorNames = loadDoctorNames()
        rows.map { row -> row.toAppointment(doctorNames) }
    }

    suspend fun getAppointmentsByDate(date: String): List<Appointment> = withContext(Dispatchers.IO) {
        val rows = client.postgrest["appointments"]
            .select {
                filter {
                    eq("appointment_date", date)
                }
            }
            .decodeList<AppointmentRow>()

        val doctorNames = loadDoctorNames()
        rows.map { row -> row.toAppointment(doctorNames) }
    }

    suspend fun getAllDoctors(): List<Doctor> = withContext(Dispatchers.IO) {
        client.postgrest["doctors"]
            .select()
            .decodeList<Doctor>()
    }

    suspend fun bookAppointment(appointment: Appointment): Appointment = withContext(Dispatchers.IO) {
        client.postgrest["appointments"].insert(
            AppointmentInsertRow(
                id = appointment.id,
                patientId = appointment.patientId,
                doctorId = appointment.doctorId,
                date = appointment.date,
                time = appointment.time,
                status = appointment.status
            )
        )
        appointment
    }

    suspend fun updateAppointmentStatus(id: String, status: String) = withContext(Dispatchers.IO) {
        client.postgrest["appointments"].update({
            set("status", status)
        }) {
            filter {
                eq("id", id)
            }
        }
    }

    suspend fun getAdminStats(): AdminStats = withContext(Dispatchers.IO) {
        try {
            // Fetch all profiles to count total patients.
            // Note: Ensure RLS policies allow the admin to select all profiles.
            val allProfiles = client.postgrest["profiles"]
                .select()
                .decodeList<Profile>()

            // Count profiles where role is 'patient' (case-insensitive)
            val patientCount = allProfiles.count { it.role.equals("patient", ignoreCase = true) }

            val pendingAppointments = getPendingAppointments()

            AdminStats(
                totalPatients = patientCount,
                pendingAppointments = pendingAppointments.size
            )
        } catch (e: Exception) {
            android.util.Log.e("AdminDashboard", "Error fetching stats", e)
            AdminStats(0, 0)
        }
    }

    private fun AppointmentRow.toAppointment(doctorNames: Map<String, String>): Appointment {
        return Appointment(
            patientId = patientId,
            doctorId = doctorId,
            id = id,
            doctorName = doctorNames[doctorId] ?: "Doctor",
            date = date,
            time = time,
            status = status,
            createdAt = createdAt
        )
    }
}
