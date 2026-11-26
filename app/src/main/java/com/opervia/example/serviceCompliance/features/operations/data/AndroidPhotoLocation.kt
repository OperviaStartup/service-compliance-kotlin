package com.opervia.example.serviceCompliance.features.operations.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.location.Location
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import com.opervia.example.serviceCompliance.features.operations.domain.model.PhotoLocation
import com.opervia.example.serviceCompliance.features.operations.domain.model.OperationsFailure
import com.opervia.example.serviceCompliance.features.operations.domain.model.OperationsFailureKind
import com.opervia.example.serviceCompliance.features.operations.domain.repository.PhotoLocationProvider
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/** Solo se consulta en primer plano cuando el requisito exige ubicación y el usuario lo solicita. */
internal class AndroidPhotoLocation(context: Context) : PhotoLocationProvider {
    private val context = context.applicationContext

    @SuppressLint("MissingPermission")
    override suspend fun current(): PhotoLocation {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!fine && !coarse) throw unavailable("Concede el permiso de ubicación para este requisito.")
        val manager = context.getSystemService(LocationManager::class.java)
        val provider = when {
            fine && manager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
            manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
            else -> throw unavailable("Activa la ubicación del dispositivo y vuelve a intentarlo.")
        }
        val location = withTimeoutOrNull(15_000) {
            suspendCancellableCoroutine<Location?> { continuation ->
                val signal = CancellationSignal()
                continuation.invokeOnCancellation { signal.cancel() }
                manager.getCurrentLocation(provider, signal, context.mainExecutor) {
                    if (continuation.isActive) continuation.resume(it)
                }
            }
        } ?: throw unavailable("No se pudo obtener la ubicación. Revisa que esté activada y vuelve a intentarlo.")
        if (!location.hasAccuracy() || location.accuracy <= 0) throw unavailable("La ubicación recibida no tiene una precisión válida.")
        return PhotoLocation(location.latitude, location.longitude, location.accuracy.toDouble(),
            java.time.Instant.ofEpochMilli(location.time).toString())
    }

    private fun unavailable(message: String) = OperationsFailure(OperationsFailureKind.INVALID_REQUEST, message)
}
