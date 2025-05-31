package com.example.amenazasandroid.models

import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.ViewModel
import com.example.amenazasandroid.ConnectionReport

class SharedConnectionViewModel : ViewModel() {
    private val _reports = mutableStateListOf<AppReport>()
    val reports: List<AppReport> = _reports

    private val _connectionReports = mutableStateListOf<ConnectionReport>()
    val connectionReports: List<ConnectionReport> = _connectionReports

    fun addReport(report: AppReport) {
        _reports.add(report)
    }

    fun addConnectionReport(connectionReport: ConnectionReport) {
        _connectionReports.add(connectionReport)
    }

    fun clearReports() {
        _reports.clear()
    }

    fun clearConnectionReports() {
        _connectionReports.clear()
    }
}