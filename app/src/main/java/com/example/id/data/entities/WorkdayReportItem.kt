package com.example.id.data.entities

import com.example.id.data.entities.Workday

data class WorkdayReportItem(
    val workday: Workday,
    val netWorkDuration: Long,
    val totalBreakDuration: Long
)