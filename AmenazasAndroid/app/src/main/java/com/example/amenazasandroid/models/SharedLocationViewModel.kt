package com.example.amenazasandroid.models

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import locationAccess.LocationAccess

class SharedLocationViewModel : ViewModel() {
    // Cambiamos de un solo reporte a una lista mutable de reportes
    var locationStats by mutableStateOf<List<LocationAccess.LocationStatEntry>>(emptyList())
        private set

    // Método para añadir un reporte nuevo a la lista
    fun updateLocationStats(newlocationStats: List<LocationAccess.LocationStatEntry>) {
        locationStats = newlocationStats
    }

    // Opcional: método para limpiar reportes
    fun clearLocationStats() {
        locationStats = emptyList()
    }
}