package com.example.amenazasandroid.models

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.example.amenazasandroid.models.AppReport

class SharedReportViewModel : ViewModel() {
    // Cambiamos de un solo reporte a una lista mutable de reportes
    var reports by mutableStateOf<List<AppReport>>(emptyList())
        private set

    // Método para añadir un reporte nuevo a la lista
    fun addReport(newReport: AppReport) {
        reports = reports + newReport
    }

    // Opcional: método para limpiar reportes
    fun clearReports() {
        reports = emptyList()
    }
}