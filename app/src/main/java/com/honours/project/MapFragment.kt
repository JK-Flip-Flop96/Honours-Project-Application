package com.honours.project

import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.os.Parcelable
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Observer
import com.google.android.gms.common.GooglePlayServicesNotAvailableException
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.*
import com.google.android.gms.maps.model.*
import com.google.firebase.storage.FirebaseStorage
import com.honours.project.models.Report
import com.squareup.picasso.Callback
import com.squareup.picasso.Picasso
import kotlinx.android.synthetic.main.fragment_main.*
import java.lang.Exception
import java.util.*


// Log Tag
private const val TAG = "MapFragment"

private const val KEY_CAMERA_POSITION = "camera_position"
private const val KEY_LOCATION = "location"
private const val MAPVIEW_BUNDLE_KEY = "MapViewBundleKey"

private const val DEFAULT_ZOOM: Float = 17.0F

// Permissions Checks
var mLocationPermissionGranted: Boolean = false
private const val PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION = 101

class MapsFragment : Fragment(), OnMapReadyCallback {

    // Activities
    private lateinit var mFrgAct: MainActivity
    private lateinit var mIntent: Intent

    // Views
    private lateinit var mMap: GoogleMap

    // Map Defaults

    private val mDefaultLocation = LatLng(0.0, 0.0)

    private var mCurrentLocation: Parcelable? = null
    private var mLastKnownLocation: Location? = null

    private var mCameraPosition: CameraPosition? = null

    // Location Provider
    private lateinit var mFusedLocationProviderClient: FusedLocationProviderClient

    private val model: MarkerViewModel by activityViewModels()

    /**
     * onCreateView
     */
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        var mapViewBundle: Bundle? = null

        if (savedInstanceState != null) {
            mapViewBundle = savedInstanceState.getBundle(MAPVIEW_BUNDLE_KEY)
            mCurrentLocation = savedInstanceState.getParcelable(KEY_LOCATION)
            mCameraPosition = savedInstanceState.getParcelable(KEY_CAMERA_POSITION)
        }

        val view = inflater.inflate(R.layout.fragment_main, container, false)

        val m = view.findViewById<MapView>(R.id.map)
        m.onCreate(mapViewBundle)
        m.getMapAsync(this)

        return view
    }

    /**
     * onViewCreated
     */
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.i(TAG, "Map Fragment Created")

        /*
            MAP SETUP
         */
        // Construct a FusedLocationProviderClient.
        mFusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(
            requireActivity()
        )
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        mFrgAct = requireActivity() as MainActivity
        mIntent = mFrgAct.intent
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        var mapViewBundle: Bundle? = outState.getBundle(MAPVIEW_BUNDLE_KEY)
        if(mapViewBundle == null) {
            mapViewBundle = Bundle()
            outState.putBundle(MAPVIEW_BUNDLE_KEY, mapViewBundle)
        }

        map.onSaveInstanceState(mapViewBundle)
        outState.putParcelable(KEY_CAMERA_POSITION, mMap.cameraPosition)
        outState.putParcelable(KEY_LOCATION, mLastKnownLocation)
    }

    override fun onResume() {
        super.onResume()
        map.onResume()
    }

    override fun onStart() {
        super.onStart()
        map.onStart()
    }

    override fun onStop() {
        super.onStop()
        map.onStop()
    }

    /**
     * Callback when the map is ready for use
     */
    override fun onMapReady(googleMap: GoogleMap) {
        try {
            MapsInitializer.initialize(this.activity)
        } catch (e: GooglePlayServicesNotAvailableException) {
            e.printStackTrace()
        }

        mMap = googleMap

        //Load the Map Style
        mMap.setMapStyle(MapStyleOptions(resources.getString(R.string.style_json)))

        mMap.setInfoWindowAdapter(ReportWindowAdapter())

        mMap.uiSettings.isMapToolbarEnabled = false
        mMap.uiSettings.isCompassEnabled = false

        model.toMark.observe(viewLifecycleOwner, Observer {
            addMarker(it)
        })

        getLocationPermission()

        // Set onClickListener for the Floating Action Button
        fab.setOnClickListener {
            getDeviceLocation(true)
        }
    }

    override fun onPause() {
        map.onPause()
        super.onPause()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        map.onLowMemory()
    }

    private fun updateLocationUI() {
        try {
            if (mLocationPermissionGranted) {
                mMap.isMyLocationEnabled = true
                mMap.uiSettings.isMyLocationButtonEnabled = true
                getDeviceLocation(false)
            } else {
                mMap.isMyLocationEnabled = false
                mMap.uiSettings.isMyLocationButtonEnabled = false
                mLastKnownLocation = null
                getLocationPermission()
            }
        } catch (e: SecurityException) {

        }
    }

    /**
     * Function to check if the application has location access
     */
    private fun getLocationPermission() {
        if (ContextCompat.checkSelfPermission(this.requireContext(),
                android.Manifest.permission.ACCESS_FINE_LOCATION
            )
            == PackageManager.PERMISSION_GRANTED
        ) {
            //If location permission is granted
            Log.i(TAG, "Location Permission Granted")
            mLocationPermissionGranted = true
            updateLocationUI()
        } else {
            //If location permission is not granted, request it.
            requestPermissions(arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION),
                PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION
            )
            Log.i(TAG, "Location Permission Missing")
        }
    }

    /**
     * Listener for the return of permission requests
     */
    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>,
        grantResults: IntArray
    ) {
        when (requestCode) {
            PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION -> {
                if (grantResults.isNotEmpty() && grantResults[0] ==
                    PackageManager.PERMISSION_GRANTED) {
                    mLocationPermissionGranted = true
                    updateLocationUI()
                }
            }
        }
    }

    private fun getDeviceLocation(toActivity: Boolean) {
        try {
            if (mLocationPermissionGranted) {
                Log.i(TAG, "Location Access Approved")
                mFusedLocationProviderClient.lastLocation.addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        Log.i(TAG, "Location Accessed")
                        mLastKnownLocation = task.result as Location
                        mMap.moveCamera(
                            CameraUpdateFactory.newCameraPosition(
                                CameraPosition.Builder().target(
                                    LatLng(
                                        mLastKnownLocation!!.latitude,
                                        mLastKnownLocation!!.longitude
                                    )
                                ).zoom(DEFAULT_ZOOM).tilt(40f).build()
                            )
                        )
                        if(toActivity){
                            mFrgAct.onFabClick(mLastKnownLocation!!)
                        }
                    } else {
                        Log.i(TAG, "Location Failed")
                        mMap.moveCamera(
                            CameraUpdateFactory.newLatLngZoom(
                                mDefaultLocation, DEFAULT_ZOOM
                            )
                        )
                        mMap.uiSettings.isMyLocationButtonEnabled = false
                    }
                }
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Get Location Function Failed")
        }
    }

    private fun addMarker(report: Report){
        getDeviceLocation(false)
        val categories = resources.getStringArray(R.array.categories_array)
        var colour = BitmapDescriptorFactory.HUE_RED
        when(report.category){
            categories[0] -> colour = BitmapDescriptorFactory.HUE_RED
            categories[1] -> colour = BitmapDescriptorFactory.HUE_ORANGE
            categories[2] -> colour = BitmapDescriptorFactory.HUE_YELLOW
            categories[3] -> colour = BitmapDescriptorFactory.HUE_GREEN
            categories[4] -> colour = BitmapDescriptorFactory.HUE_AZURE
            categories[5] -> colour = BitmapDescriptorFactory.HUE_BLUE
            categories[6] -> colour = BitmapDescriptorFactory.HUE_MAGENTA
            categories[7] -> colour = BitmapDescriptorFactory.HUE_ROSE
            categories[8] -> colour = BitmapDescriptorFactory.HUE_CYAN
            categories[9] -> colour = BitmapDescriptorFactory.HUE_VIOLET
        }
        mMap.addMarker(MarkerOptions().position(LatLng(report.lat, report.long))
            .icon(BitmapDescriptorFactory.defaultMarker(colour)).title(report.category).snippet(report.imgRef))
    }

    internal inner class ReportWindowAdapter: GoogleMap.InfoWindowAdapter {

        private val mWindow = layoutInflater.inflate(R.layout.custom_info_window, null)
        private var markers = Hashtable<String, Boolean>()

        override fun getInfoContents(marker: Marker): View? {
            val imageView = mWindow.findViewById<ImageView>(R.id.window_image)
            mWindow.findViewById<TextView>(R.id.window_title).text = marker.title
            val isLoaded = markers[marker.id] != null
            FirebaseStorage.getInstance()
                .reference.child("images/")
                .child(marker.snippet).downloadUrl.addOnSuccessListener {
                    if(!isLoaded){
                        markers[marker.id] = true
                        Picasso.get().load(it).resize(600,0).into(imageView, InfoWindowRefresher(marker))
                    } else {
                        Picasso.get().load(it).resize(600,0).centerCrop().into(imageView)
                    }
                }
            return mWindow
        }

        override fun getInfoWindow(p0: Marker?): View? {
            return null
        }
    }
}

class InfoWindowRefresher(private val markerToRefresh: Marker) : Callback {
    override fun onSuccess() {
        markerToRefresh.showInfoWindow()
    }

    override fun onError(e: Exception?) {
        Log.e(TAG, e.toString())
    }

}
