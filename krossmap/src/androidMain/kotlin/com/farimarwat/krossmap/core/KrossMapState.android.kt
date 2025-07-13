package com.farimarwat.krossmap.core

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Looper
import androidx.annotation.RequiresPermission
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.platform.LocalContext
import com.farimarwat.krossmap.model.KrossCoordinate
import com.farimarwat.krossmap.model.KrossMarker
import com.farimarwat.krossmap.model.KrossPolyLine
import com.farimarwat.krossmap.utils.hasPermission
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority

actual class KrossMapState(
    val context: Context
) {
    actual var currentLocation by mutableStateOf<KrossCoordinate?>(null)

    actual internal val markers: SnapshotStateList<KrossMarker> = mutableStateListOf()
    actual internal val polylines = mutableStateListOf<KrossPolyLine>()

    actual internal var currentLocationRequested: Boolean = false
    actual var onUpdateLocation:(KrossCoordinate)-> Unit = {  }


    actual var krossMapCameraPositionState: KrossCameraPositionState? = null
    private  var fusedLocationProviderClient: FusedLocationProviderClient? = null
    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            locationResult.lastLocation?.let { location ->
                onUpdateLocation?.invoke(KrossCoordinate(location.latitude,location.longitude))
                currentLocation = currentLocation?.copy(
                    latitude = location.latitude,
                    longitude = location.longitude
                )
                if(currentLocationRequested){
                    krossMapCameraPositionState?.currentCameraPosition = KrossCoordinate(
                       latitude = location.latitude,
                       longitude = location.longitude
                   )
                    stopLocationUpdate()
                    currentLocationRequested = false
                }
            }
        }
    }

    actual fun addOrUpdateMarker(marker: KrossMarker){
        val existingIndex = markers.indexOfFirst { it.title == marker.title }
        if (existingIndex != -1) {
            markers[existingIndex] = marker
        } else {
            markers.add(marker)
        }
    }

    actual fun removeMarker(marker: KrossMarker) {
        markers.remove(marker)
    }
    actual fun addPolyLine(polyLine: KrossPolyLine){
        polylines.add(polyLine)
    }

    actual fun removePolyLine(polyline: KrossPolyLine) {
        polylines.remove(polyline)
    }

    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, android.Manifest.permission.ACCESS_COARSE_LOCATION])
    actual fun startLocationUpdate() {
        if (context.hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)) {
            if (fusedLocationProviderClient == null) {
                fusedLocationProviderClient =
                    LocationServices.getFusedLocationProviderClient(context)
            }
            val locationRequest = LocationRequest.create().apply {
                interval = 5000
                fastestInterval = 2000
                priority = Priority.PRIORITY_HIGH_ACCURACY
            }

            fusedLocationProviderClient?.let { client ->
                client.requestLocationUpdates(
                    locationRequest,
                    locationCallback,
                    Looper.getMainLooper()
                ).addOnFailureListener { exception ->
                    println("Location update failed: $exception")
                }
            }
        } else {
            println("Location permission not granted")
        }
    }

    actual fun stopLocationUpdate() {
        if (fusedLocationProviderClient != null) {
            fusedLocationProviderClient?.removeLocationUpdates(locationCallback)
        }
        fusedLocationProviderClient = null
    }

    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    actual fun requestCurrentLocation() {
        krossMapCameraPositionState?.currentCameraPosition = null
        currentLocationRequested = true
        startLocationUpdate()
    }


    internal actual fun setCameraPositionState(state: KrossCameraPositionState){
        this.krossMapCameraPositionState = state
    }
}

@Composable
actual fun rememberKrossMapState(): KrossMapState {
    val context = LocalContext.current
    val locationState = remember { KrossMapState(context) }
    return locationState
}