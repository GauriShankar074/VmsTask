package com.gauri.vmstask

import android.app.Application
import android.location.Location
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.google.android.gms.maps.model.LatLng
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.AutocompletePrediction
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.net.FetchPlaceRequest
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class MainViewModel  @Inject constructor(
    application: Application
) :ViewModel() {
    private val placesClient = Places.createClient(application)
    var predictions by mutableStateOf<List<AutocompletePrediction>>(emptyList())
        private set
    private val _isBounded = MutableStateFlow(false)
    val isBounded = _isBounded.asStateFlow()

    private val _locationState = MutableStateFlow<Location?>(null)
    val locationState = _locationState.asStateFlow()

    var selectedLatLng by mutableStateOf<LatLng?>(null)

    fun setBounded(value:Boolean){
        _isBounded.value = value
    }

    fun setLocation(value: Location?){
        _locationState.value = value
    }

    fun clearSearch(){
        predictions = emptyList()
    }

    fun search(search:String){
        fetchPredictions(search)
    }
    private fun fetchPredictions(query: String) {
        val request = FindAutocompletePredictionsRequest.builder()
            .setQuery(query)
            .build()

        placesClient.findAutocompletePredictions(request)
            .addOnSuccessListener { response ->
                predictions = response.autocompletePredictions
            }
            .addOnFailureListener {
                predictions = emptyList()
            }
    }

    fun fetchPlaceDetails(placeId: String) {
        val request = FetchPlaceRequest.builder(
            placeId,
            listOf(Place.Field.LAT_LNG, Place.Field.NAME)
        ).build()

        placesClient.fetchPlace(request)
            .addOnSuccessListener { result ->
                selectedLatLng = result.place.latLng
            }
    }
}