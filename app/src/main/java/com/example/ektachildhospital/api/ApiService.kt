package com.example.ektachildhospital.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.*

data class LoginRequest(val phone: String)
data class VerifyOtpRequest(val phone: String, val otp: String)
data class AuthResponse(val token: String, val user: UserProfile)
@Serializable
data class UserProfile(
    val id: String,
    val name: String,
    val phone: String,
    val email: String?,
    val role: String // "PATIENT", "ADMIN", or "SUPER_ADMIN"
)

@Serializable
data class Doctor(
    val id: String,
    val name: String,
    val email: String,
    val specialty: String,
    @SerialName("consultation_fee")
    val consultationFee: Double = 500.0,
    val rating: Double = 4.5,
    @SerialName("image_url")
    val imageUrl: String? = null,
    val availability: Map<String, List<String>> = emptyMap()
)

data class Appointment(
    val patientId: String,
    val doctorId: String,
    val id: String,
    val doctorName: String,
    val date: String,
    val time: String,
    val status: String,
    val createdAt: String? = null
)

interface ApiService {
    @POST("/auth/login")
    suspend fun login(@Body request: LoginRequest): AuthResponse

    @POST("/auth/verify-otp")
    suspend fun verifyOtp(@Body request: VerifyOtpRequest): AuthResponse

    @GET("/doctors")
    suspend fun getDoctors(): List<Doctor>

    @GET("/appointments")
    suspend fun getAppointments(): List<Appointment>

    @POST("/appointments/book")
    suspend fun bookAppointment(@Body appointment: Appointment): Appointment

    @PUT("/patient/profile/update")
    suspend fun updateProfile(@Body profile: UserProfile): UserProfile

    // Admin Endpoints
    @POST("/admin/doctors")
    suspend fun addDoctor(@Body doctor: Doctor): Doctor

    @DELETE("/admin/doctors/{id}")
    suspend fun deleteDoctor(@Path("id") id: String): retrofit2.Response<Unit>

    @PUT("/admin/doctors/{id}")
    suspend fun updateDoctor(@Path("id") id: String, @Body doctor: Doctor): Doctor

    @GET("/admin/stats")
    suspend fun getAdminStats(): AdminStats

    @GET("/admin/appointments/pending")
    suspend fun getPendingAppointments(): List<Appointment>

    @POST("/admin/appointments/{id}/accept")
    suspend fun acceptAppointment(@Path("id") id: String): retrofit2.Response<Unit>

    @POST("/admin/appointments/{id}/reject")
    suspend fun rejectAppointment(@Path("id") id: String): retrofit2.Response<Unit>
}

data class AdminStats(val totalPatients: Int, val pendingAppointments: Int)
