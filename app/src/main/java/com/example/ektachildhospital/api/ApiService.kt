package com.example.ektachildhospital.api

import retrofit2.http.*

data class LoginRequest(val phone: String)
data class VerifyOtpRequest(val phone: String, val otp: String)
data class AuthResponse(val token: String, val user: UserProfile)
data class UserProfile(val id: String, val name: String, val phone: String, val email: String?)
data class Doctor(val id: String, val name: String, val specialty: String, val availability: List<String>)
data class Appointment(val id: String, val doctorId: String, val doctorName: String, val date: String, val time: String, val status: String)

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
}