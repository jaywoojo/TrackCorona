package com.example.trackcorona

import android.graphics.Color
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle

import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import com.google.maps.android.clustering.ClusterManager
import com.google.maps.android.clustering.algo.NonHierarchicalDistanceBasedAlgorithm
import com.google.maps.android.heatmaps.HeatmapTileProvider
import com.google.maps.android.heatmaps.WeightedLatLng
import java.net.URL
import android.widget.Button
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import kotlin.text.Charsets.UTF_8
import com.github.mikephil.charting.components.Description
import com.github.mikephil.charting.utils.ColorTemplate


class VirusMap : AppCompatActivity(), OnMapReadyCallback {

    // google map
    private lateinit var mMap: GoogleMap
    // Hash map to pair countries with confirmed case count
    private var pieChartMap = HashMap<Int, String>()
    private var mostCasesUSA: Double = 0.0

    // gaussian sphere radius of data points on heat map
    private val RADIUS = 50

    // intensity multiplier of heat map for best visualization of US data
    private val INTENSITY = 1/2.0

    // using COVID-19 data from Johns Hopkins University
    //https://github.com/CSSEGISandData/COVID-19
    private val usaData =
        "https://raw.githubusercontent.com/CSSEGISandData/COVID-19/master/csse_covid_19_data/csse_covid_19_time_series/time_series_covid19_confirmed_US.csv"
    private val globalData =
        "https://raw.githubusercontent.com/CSSEGISandData/COVID-19/master/csse_covid_19_data/csse_covid_19_time_series/time_series_covid19_confirmed_global.csv"

    //indices of parsed csv file for needed information (global data)
    private val PROVINCE = 0
    private val COUNTRY = 1
    private val GBL_LAT = 2
    private val GBL_LNG = 3

    //indices for US data
    private val COUNTY = 5
    private val STATE = 6
    private val US_LAT = 8
    private val US_LNG = 9


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.virus_map)

        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)
    }


    // this callback is triggered when the map is ready to be used
    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap

        // list of weighted lat lng coordinates used to create heat map
        var listOfDataPoints = mutableListOf<WeightedLatLng>()

        // market cluster manager to add markers on map
        var mClusterManager = ClusterManager<DataPoint>(this, mMap)
        // setting algorithm to showcase market distribution most accurately
        mClusterManager.setAlgorithm(NonHierarchicalDistanceBasedAlgorithm<DataPoint>())

        mMap.setOnCameraIdleListener(mClusterManager)
        mMap.setOnMarkerClickListener(mClusterManager)

        // iterating line by line of data set csv file
        var reader = URL(usaData).openStream().bufferedReader(UTF_8)
        var line: String? = ""


        while (line != null) {

            line = reader.readLine()

            if (line == null) {
                break
            }

            val row = line.split(",")


            // skipping first row of column labels
            // skipping cruise ships (labeled with lat lng of (0.0, 0.0)
            // ensuring latitue and longitude are provided
            if (row[0] != "UID" && row[US_LNG] != "" && row[US_LAT].toDouble() != 0.0) {

                val coordinates = LatLng(row[US_LAT].toDouble(), row[US_LNG].toDouble())
                var name = row[STATE]

                if (row[COUNTY].isNotBlank()) {
                    name += " (" + row[COUNTY] + " County)"
                }

                val numOfCases = row[row.size - 1].toDouble()

                // keeping track of which counties have the highest confirmed case count
                if (mostCasesUSA < numOfCases) {
                    mostCasesUSA = numOfCases
                }


                // creating weighted data point for heat map
                val locationData = DataPoint(name, coordinates, numOfCases)

                // adding marker for each county
                mClusterManager.addItem(locationData)
                listOfDataPoints.add(locationData.makeWeighted())
            }
        }



        // now iterating through global confirmed case data
        reader = URL(globalData).openStream().bufferedReader(UTF_8)
        line = ""

        while (line != null) {
            line = reader.readLine()
            if (line == null) {
                break
            }

            val row = line.split(",")

            // accounting for errors in data set (South Korea and Netherlands)
            // (extra commas in country name pushes back all indices)
            if (row[COUNTRY] == "\"Korea") {
                val coordinates = LatLng(row[3].toDouble(), row[4].toDouble())
                val name = "South Korea"
                val numOfCases = row[row.size - 1].toDouble()
                val locationData = DataPoint(name, coordinates, numOfCases)
                mClusterManager.addItem(locationData)
                pieChartMap.put(numOfCases.toInt(), name)


            } else if (row[PROVINCE] == "\"Bonaire") {
                val coordinates = LatLng(row[3].toDouble(), row[4].toDouble())
                var name = "Netherlands (Bonaire, Sint Eustatius, and Saba"
                val locationData = DataPoint(name, coordinates, row[row.size - 1].toDouble())
                mClusterManager.addItem(locationData)
                pieChartMap.put(row[row.size - 1].toInt(), name)


            } else {

                // making sure row contains data and is populated
                if (row.size > 1 && row[GBL_LAT] != "Lat" && row[GBL_LAT].toDouble() != 0.0) {

                    // recording data
                    val coordinates = LatLng(row[GBL_LAT].toDouble(), row[GBL_LNG].toDouble())
                    var name = row[COUNTRY]
                    if (row[PROVINCE].isNotBlank()) {
                        name += " (" + row[PROVINCE] + ")"
                    }

                    // adding marker for each country
                    val locationData = DataPoint(name, coordinates, row[row.size - 1].toDouble())
                    mClusterManager.addItem(locationData)

                    // tracking countries and their total confirmed case count
                    pieChartMap.put(row[row.size - 1].toInt(), name)

                }
            }
        }


        //making heat map
        val mProvider = HeatmapTileProvider.Builder().weightedData(listOfDataPoints).build()
        mProvider.setMaxIntensity(mostCasesUSA * INTENSITY)
        mProvider.setRadius(RADIUS)
        val mOverlay = mMap.addTileOverlay(TileOverlayOptions().tileProvider(mProvider))


        // button on google map to view graphs
        val graphButton = findViewById<Button>(R.id.graphview)
        graphButton.setOnClickListener {
            setUpChart()
        }

    }


    // helper function to set up charts using confirmed case data
    fun setUpChart() {

        //sorting map by decreasing order of confirmed cases
        val sorted = pieChartMap.toSortedMap(reverseOrder())
        var counter = 0

        // data for pie chart
        var pieEntries = ArrayList<PieEntry>()

        // recording top 10 countries
        for ((key, value) in sorted) {
            if (counter == 10) {
                break
            } else {
                pieEntries.add(PieEntry(key.toFloat(), value))
            }
            counter++;
        }


        // creating chart and setting attributes
        var setData = PieDataSet(pieEntries, "KEY")
        var colors = ColorTemplate.COLORFUL_COLORS
        setData.setColors(colors.toMutableList())
        var data = PieData(setData)
        data.setValueTextSize(12.5.toFloat())


        setContentView(R.layout.data_viz)

        // setting data
        var chart = findViewById<PieChart>(R.id.piechart)
        chart.data = data

        // setting animation, colors, and text sizes
        chart.animateY(1000)
        chart.setEntryLabelTextSize(15.toFloat())
        chart.setEntryLabelColor(Color.BLACK)
        chart.setCenterTextSizePixels(30.toFloat())
        chart.transparentCircleRadius = 60f
        var description = Description()
        description.text = "Top 10 countries with highest COVID-19 confirmed case count"
        description.textSize = 12.5.toFloat()
        chart.description = description
        chart.invalidate()


    }


}
