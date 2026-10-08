package com.example.monisom.data

import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order

object Repository {

    suspend fun currentTeacherId(): String? = supabase.auth.currentUserOrNull()?.id

    suspend fun getTeacher(teacherId: String): Teacher? =
        supabase.from("teacher").select {
            filter { eq("id", teacherId) }
        }.decodeList<Teacher>().firstOrNull()

    suspend fun getSchoolsForTeacher(teacherId: String): List<School> {
        val rows = supabase.from("school_teacher").select {
            filter { eq("teacher_id", teacherId) }
        }.decodeList<SchoolTeacher>()

        val schoolIds = rows.map { it.schoolId }
        if (schoolIds.isEmpty()) return emptyList()

        return supabase.from("school").select {
            filter { isIn("id", schoolIds) }
        }.decodeList<School>()
    }

    suspend fun getClassesForTeacherInSchool(teacherId: String, schoolId: String): List<ClassGroup> {
        val rows = supabase.from("class_teacher").select {
            filter { eq("teacher_id", teacherId) }
        }.decodeList<ClassTeacher>()

        val classIds = rows.map { it.classId }
        if (classIds.isEmpty()) return emptyList()

        return supabase.from("class").select {
            filter {
                isIn("id", classIds)
                eq("school_id", schoolId)
            }
        }.decodeList<ClassGroup>()
    }

    suspend fun getRoomsForSchool(schoolId: String): List<Room> =
        supabase.from("room").select {
            filter { eq("school_id", schoolId) }
            order("name", Order.ASCENDING)
        }.decodeList<Room>()

    suspend fun getMonitorings(classId: String, teacherId: String): List<Monitoring> =
        supabase.from("monitoring").select {
            filter {
                eq("class_id", classId)
                eq("teacher_id", teacherId)
            }
            order("created_at", Order.DESCENDING)
        }.decodeList<Monitoring>()

    suspend fun createMonitoring(monitoring: Monitoring): Monitoring =
        supabase.from("monitoring").insert(monitoring) {
            select()
        }.decodeSingle<Monitoring>()

    suspend fun updateMonitoring(id: String, finishedAt: String, perception: Int?, stress: Int?) {
        supabase.from("monitoring").update({
            set("finished_at", finishedAt)
            if (perception != null) set("perception", perception) else setToNull("perception")
            if (stress != null) set("stress", stress) else setToNull("stress")
        }) {
            filter { eq("id", id) }
        }
    }

    suspend fun insertReadings(readings: List<MonitoringReading>) {
        if (readings.isEmpty()) return
        supabase.from("monitoring_reading").insert(readings)
    }
}
