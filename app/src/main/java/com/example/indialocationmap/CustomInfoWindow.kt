package com.example.indialocationmap

import android.content.Context
import android.view.View
import android.widget.TextView
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.infowindow.InfoWindow

class CustomInfoWindow(
    mapView: MapView,
    private val context: Context
) : InfoWindow(R.layout.custom_info_window, mapView) {

    override fun onOpen(item: Any?) {
        val marker = item as? Marker ?: return

        val titleView = mView.findViewById<TextView>(R.id.title)
        val snippetView = mView.findViewById<TextView>(R.id.snippet)

        titleView.text = marker.title
        snippetView.text = marker.snippet

        // 🔹 Scale text based on zoom level
        val zoom = mMapView.zoomLevelDouble
        val scaleFactor = (zoom / 10).toFloat().coerceIn(0.8f, 2.0f)

        titleView.textSize = 14f * scaleFactor
        snippetView.textSize = 12f * scaleFactor
    }

    override fun onClose() {
        // nothing special
    }
}
