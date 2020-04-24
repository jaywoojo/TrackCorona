package com.example.trackcorona

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle


import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import com.google.maps.android.clustering.ClusterManager
import com.google.maps.android.clustering.algo.NonHierarchicalDistanceBasedAlgorithm
import com.google.maps.android.heatmaps.HeatmapTileProvider
import com.google.maps.android.heatmaps.WeightedLatLng
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.charset.Charset

class VirusMap : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var mMap: GoogleMap

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_virus_map)
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)
        println("WORKING")
    }

    /**
     * Manipulates the map once available.
     * This callback is triggered when the map is ready to be used.
     * This is where we can add markers or lines, add listeners or move the camera. In this case,
     * we just add a marker near Sydney, Australia.
     * If Google Play services is not installed on the device, the user will be prompted to install
     * it inside the SupportMapFragment. This method will only be triggered once the user has
     * installed Google Play services and returned to the app.
     */
    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap

        var listOfDataPoints = mutableListOf<WeightedLatLng>()

        var mClusterManager = ClusterManager<DataPoint>(this, mMap)
        mClusterManager.setAlgorithm(NonHierarchicalDistanceBasedAlgorithm<DataPoint>())

        mMap.setOnCameraIdleListener(mClusterManager)
        mMap.setOnMarkerClickListener(mClusterManager)


        val coronaData = resources.openRawResource(R.raw.data_us)
        val reader = BufferedReader(InputStreamReader(coronaData, Charset.forName("UTF-8")))

        reader.forEachLine {

            val row = it.split(",")

            if (row.size > 1 && row[0] != "UID" && row[9] != "" && row[8].toDouble() != 0.0) {

                val coordinates = LatLng(row[8].toDouble(), row[9].toDouble())
                var name = row[6]

                if (row[5] != "") {
                    name += " (" + row[5] + " County)"
                }

                val locationData = DataPoint(name, coordinates, row[102].toDouble())

                mClusterManager.addItem(locationData)
                listOfDataPoints.add(locationData.makeWeighted())

            }
        }

        val globalData = resources.openRawResource(R.raw.corona_data)
        val readerGlobal = BufferedReader(InputStreamReader(globalData, Charset.forName("UTF-8")))

        readerGlobal.forEachLine {

            val row = it.split(",")

            if (row.size > 1 && row[2] != "Lat" && row[1] != "US") {

                val coordinates = LatLng(row[2].toDouble(), row[3].toDouble())
                var name = row[1]
                if (row[0] != "") {
                    name += " (" + row[0] +")"
                }
                val locationData = DataPoint(name, coordinates, row[95].toDouble())
                mClusterManager.addItem(locationData)

            }

        }

        var mProvider = HeatmapTileProvider.Builder().weightedData(listOfDataPoints).build()
        mProvider.setMaxIntensity(10000.00)
        mProvider.setRadius(50)
        val mOverlay = mMap.addTileOverlay(TileOverlayOptions().tileProvider(mProvider))

    }

}
