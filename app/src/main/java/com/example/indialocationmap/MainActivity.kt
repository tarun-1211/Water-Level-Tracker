package com.example.indialocationmap
import android.graphics.Color
import java.util.Locale
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.widget.SearchView
import org.osmdroid.views.overlay.infowindow.InfoWindow

import android.widget.Toast
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.util.MapTileIndex
import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.widget.Button
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONArray
import org.osmdroid.config.Configuration
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import java.io.BufferedReader
import java.io.InputStreamReader

class MainActivity : AppCompatActivity() {

    private lateinit var map: MapView

    override fun onCreate(savedInstanceState: Bundle?) {



        super.onCreate(savedInstanceState)
        Configuration.getInstance().load(applicationContext, getSharedPreferences("osmdroid", MODE_PRIVATE))
        setContentView(R.layout.activity_main)

        map = findViewById(R.id.map)
        map.setMultiTouchControls(true)

        // Default to Chennai
        val defaultPoint = GeoPoint(13.0827, 80.2707)
        map.controller.setZoom(6.0)
        map.controller.setCenter(defaultPoint)

        // Load JSON data and add markers
        loadJsonAndAddMarkers()


        val autoComplete = findViewById<AutoCompleteTextView>(R.id.autoCompleteSearch)

// Collect names for dropdown
        val placeNames = markers.mapNotNull { marker ->
            marker.title // we set marker.title = "Village, District"
        }

// Remove duplicates
        val uniquePlaces = placeNames.distinct()

// Adapter for dropdown
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, uniquePlaces)
        autoComplete.setAdapter(adapter)

// Handle selection from dropdown
        autoComplete.setOnItemClickListener { _, _, position, _ ->
            val selected = adapter.getItem(position)
            val match = markers.firstOrNull { it.title == selected }
            if (match != null) {
                map.controller.animateTo(match.position)
                match.showInfoWindow()
            }
        }
        val btnMyLocation = findViewById<Button>(R.id.btn_location)
        btnMyLocation.setOnClickListener {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                    101
                )
            } else {
                zoomToMyLocation()
            }
        }




    }

    private fun zoomToMyLocation() {
        val locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
        val providers = locationManager.getProviders(true)

        var bestLocation: Location? = null

        // ✅ Check permissions before accessing location
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED
        ) {
            return   // permissions not granted → exit
        }

        for (provider in providers) {
            val l = locationManager.getLastKnownLocation(provider) ?: continue
            if (bestLocation == null || l.accuracy < bestLocation.accuracy) {
                bestLocation = l
            }
        }

        bestLocation?.let {
            val myPoint = GeoPoint(it.latitude, it.longitude)
            map.controller.setZoom(15.0)
            map.controller.animateTo(myPoint)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 101 && grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            zoomToMyLocation()
        }
    }


    private fun searchPlace(query: String) {
        val match = markers.firstOrNull {
            it.title?.contains(query, ignoreCase = true) == true ||
                    it.subDescription?.contains(query, ignoreCase = true) == true
        }

        if (match != null) {
            map.controller.animateTo(match.position)
            match.showInfoWindow()
        } else {
            Toast.makeText(this, "No match found", Toast.LENGTH_SHORT).show()
        }
    }


    private val markers = mutableListOf<Marker>()

    private fun loadJsonAndAddMarkers() {
        val inputStream = resources.openRawResource(R.raw.nodes)
        val reader = BufferedReader(InputStreamReader(inputStream))
        val jsonText = reader.readText()
        reader.close()

        val jsonArray = JSONArray(jsonText)

        for (i in 0 until jsonArray.length()) {
            val obj = jsonArray.getJSONObject(i)

            val lat = obj.optString("Latitude").toDoubleOrNull() ?: continue
            val lon = obj.optString("Longitude").toDoubleOrNull() ?: continue

            val rawWaterLevel = obj.optDouble("Water_level", Double.NaN)
            val color = when {
                !rawWaterLevel.isNaN() && rawWaterLevel < 5.0   -> Color.parseColor("#85DBD9")
                !rawWaterLevel.isNaN() && rawWaterLevel < 10.0  -> Color.parseColor("#9DCD5A")
                !rawWaterLevel.isNaN() && rawWaterLevel < 15.0  -> Color.parseColor("#FFD483")
                !rawWaterLevel.isNaN() && rawWaterLevel >= 15.0 -> Color.parseColor("#DF5745")
                else -> Color.GRAY
            }

            val marker = Marker(map)
            marker.position = GeoPoint(lat, lon)
            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)

            // --- Color + resize ---
            val basePin = resources.getDrawable(org.osmdroid.library.R.drawable.osm_ic_follow_me_on, null).mutate()
            val size = 32
            basePin.setBounds(0, 0, size, size)
            val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bmp)
            basePin.setTint(color)
            basePin.draw(canvas)
            marker.icon = BitmapDrawable(resources, bmp)

            // --- Info window ---
            marker.title = "${obj.optString("Village", "Unknown")}, ${obj.optString("DistrictName", "")}"
            marker.subDescription = """
            Location: ${obj.optString("LocationName", "N/A")}
            District: ${obj.optString("DistrictName", "N/A")}
            Block/Taluk: ${obj.optString("Block_Taluk", "N/A")}
            Village: ${obj.optString("Village", "N/A")}
            Well Depth: ${obj.optDouble("Well_Depth", Double.NaN)}
            Date: ${obj.optString("date", "N/A")}
            Water Level: ${if (rawWaterLevel.isNaN()) "N/A" else String.format("%.2f m", rawWaterLevel)}
        """.trimIndent()
            val regionId = obj.optString("RegionID", "Unknown")
            val district = obj.optString("DistrictName", "Unknown")
            val village = obj.optString("Village", "Unknown")
            val wellDepth = obj.optDouble("Well_Depth", -1.0)
            val waterLevel = obj.optDouble("Water_level", -1.0)
            val date = obj.optString("date", "N/A")

            marker.title = "$village, $district"
            marker.snippet = "Region: $regionId\nWell Depth: $wellDepth m\nWater Level: $waterLevel m\nDate: $date"


// attach custom info window
            marker.infoWindow = CustomInfoWindow(map, this)
            marker.setOnMarkerClickListener { m, mapView ->
                InfoWindow.closeAllInfoWindowsOn(mapView)
                m.showInfoWindow()
                true
            }

            map.overlays.add(marker)
            markers.add(marker) // store marker for search
        }

        map.invalidate()
    }



}
