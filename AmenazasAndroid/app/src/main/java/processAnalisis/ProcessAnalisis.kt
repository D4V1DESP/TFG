package processAnalisis

import android.content.Context
import android.util.Log
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader


class ProcessAnalisis {

    fun runCommand(command: String): String {
        try {
            val process = Runtime.getRuntime().exec(command)
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val output = StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                output.append(line).append("\n")
            }
            process.waitFor()
            return output.toString()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return ""
    }

    fun getAllProcesses() {
        // Verifica si tienes acceso root antes de ejecutar el comando
        val isRooted = checkRoot()
        if (isRooted) {
            val command = "su -c ps -A"  // Obtiene todos los procesos
            val result = runCommand(command)
            Log.d("PROCESOS", "$result")
        } else {
            Log.d("PROCESOS", "El dispositivo no tiene acceso root.")
        }
    }

    fun checkRoot(): Boolean {
        val paths = arrayOf("/system/bin/su", "/system/xbin/su", "/data/local/bin/su", "/data/local/xbin/su")
        for (path in paths) {
            if (File(path).exists()) {
                return true
            }
        }
        return false
    }
}