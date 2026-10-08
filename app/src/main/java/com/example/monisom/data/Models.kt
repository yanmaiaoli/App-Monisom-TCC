package com.example.monisom.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class School(
    val id: String,
    val name: String,
)

@Serializable
data class Teacher(
    val id: String,
    val name: String,
    val email: String,
)

@Serializable
data class ClassGroup(
    val id: String,
    @SerialName("school_id") val schoolId: String,
    val name: String,
    val turno: String = "",
    val capacidade: Int = 0,
)

@Serializable
data class Room(
    val id: String,
    @SerialName("school_id") val schoolId: String,
    val name: String,
)

@Serializable
data class SchoolTeacher(
    @SerialName("school_id") val schoolId: String,
    @SerialName("teacher_id") val teacherId: String,
)

@Serializable
data class ClassTeacher(
    @SerialName("class_id") val classId: String,
    @SerialName("teacher_id") val teacherId: String,
)

@Serializable
data class Monitoring(
    val id: String? = null,
    @SerialName("class_id") val classId: String,
    @SerialName("teacher_id") val teacherId: String,
    @SerialName("room_id") val roomId: String? = null,
    @SerialName("started_at") val startedAt: String,
    @SerialName("finished_at") val finishedAt: String? = null,
    val perception: Int? = null,
    val stress: Int? = null,
)

@Serializable
data class MonitoringReading(
    @SerialName("monitoring_id") val monitoringId: String,
    @SerialName("recorded_at") val recordedAt: String,
    val laeq: Double,
)
