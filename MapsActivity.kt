package com.vapergift.app.map

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.PointF
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Status
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.GoogleMap.OnPoiClickListener
import com.google.android.gms.maps.MapsInitializer
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.OnMapsSdkInitializedCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.PointOfInterest
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.model.PlaceLikelihood
import com.google.android.libraries.places.api.net.FetchPhotoRequest
import com.google.android.libraries.places.api.net.FetchPhotoResponse
import com.google.android.libraries.places.api.net.FindCurrentPlaceRequest
import com.google.android.libraries.places.api.net.PlacesClient
import com.google.android.libraries.places.widget.AutocompleteSupportFragment
import com.google.android.libraries.places.widget.listener.PlaceSelectionListener
import com.hjq.permissions.Permission
import com.hjq.permissions.XXPermissions
import com.kaopiz.kprogresshud.KProgressHUD
import com.vapergift.app.R
import com.vapergift.app.adapter.YandexMapTipAdapter
import com.vapergift.app.databinding.ActivityMapsBinding
import com.vapergift.base.utils.getResDrawable
import com.vapergift.base.utils.getResString
import com.yandex.mapkit.Animation
import com.yandex.mapkit.GeoObject
import com.yandex.mapkit.MapKitFactory
import com.yandex.mapkit.geometry.Point
import com.yandex.mapkit.layers.GeoObjectTapEvent
import com.yandex.mapkit.layers.GeoObjectTapListener
import com.yandex.mapkit.map.CameraPosition
import com.yandex.mapkit.map.IconStyle
import com.yandex.mapkit.map.InputListener
import com.yandex.mapkit.map.MapObjectTapListener
import com.yandex.mapkit.map.TextStyle
import com.yandex.mapkit.map.VisibleRegionUtils
import com.yandex.mapkit.search.Response
import com.yandex.mapkit.search.SearchFactory
import com.yandex.mapkit.search.SearchManager
import com.yandex.mapkit.search.SearchManagerType
import com.yandex.mapkit.search.SearchOptions
import com.yandex.mapkit.search.SearchType
import com.yandex.mapkit.search.Session
import com.yandex.mapkit.search.Session.SearchListener
import com.yandex.runtime.image.ImageProvider


class MapsActivity : AppCompatActivity(), OnMapReadyCallback, OnPoiClickListener,
    OnMapsSdkInitializedCallback {

    private lateinit var binding: ActivityMapsBinding

    private var map: GoogleMap? = null
    private var currentLocation: Location? = null
    private var placesClient: PlacesClient? = null
    private var hasLocation: Boolean = false

    //当前定位marker点

    private var currentMarker: Marker? = null

    private lateinit var fusedLocationProviderClient: FusedLocationProviderClient

    var startGoogleMap: Boolean = false
    var countryCode: String? = ""
    var mapFragment: SupportMapFragment? = null
    var searchManager: SearchManager? = null
    var searchOptions: SearchOptions? = null
    var searchSession: Session? = null
    var currentYandexPoint: GeoObject? = null
    var currentPoi: PointOfInterest? = null

    companion object {
        const val TAG = "MapsActivity"
        const val RESULT_CODE = 1102
        const val STORE_NAME = "store_name"
        const val STORE_ADD = "store_address"
        const val STORE_LAT = "store_latitude"
        const val STORE_LONG = "store_longitude"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMapsBinding.inflate(layoutInflater)

        startGoogleMap = intent.getBooleanExtra("google", false)
        countryCode = intent.getStringExtra("country_code")
        mapFragment = supportFragmentManager.findFragmentById(R.id.google_map) as SupportMapFragment
        if (startGoogleMap) {
            binding.cardView.visibility = View.VISIBLE
            mapFragment?.view?.visibility = View.VISIBLE
            binding.mapview.visibility = View.GONE
            binding.yandexCv.visibility = View.GONE
            binding.clBottom.visibility = View.VISIBLE
            binding.llYandexSearch.visibility = View.GONE
            binding.yandexLocation.visibility = View.GONE
            initGoogle()
        } else {
            binding.mapview.visibility = View.VISIBLE
            binding.yandexCv.visibility = View.VISIBLE
            binding.clBottom.visibility = View.VISIBLE
            binding.llYandexSearch.visibility = View.VISIBLE
            binding.yandexLocation.visibility = View.VISIBLE
            binding.cardView.visibility = View.GONE
            mapFragment?.view?.visibility = View.GONE
            initYandex()
        }
        setContentView(binding.root)

        binding.btnSure.setOnClickListener {
            if (!startGoogleMap) {
                yandexBack()
            } else {
                googleBack()
            }
        }
    }

    fun upBottom() {
        if (!startGoogleMap) {
            binding.tvName.text =
                "${currentYandexPoint?.name} \n ${currentYandexPoint?.descriptionText}"
        } else {
            // google
            binding.tvName.text = "${currentPoi?.name}"
            Toast.makeText(
                this, """Clicked: ${currentPoi?.name}
            Place ID:${currentPoi?.placeId}
            Latitude:${currentPoi?.latLng?.latitude} Longitude:${currentPoi?.latLng?.longitude}""",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun initYandex() {
        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this)


        MapKitFactory.initialize(this)
        searchManager = SearchFactory.getInstance().createSearchManager(SearchManagerType.COMBINED)
        searchOptions = SearchOptions().apply {
            searchTypes = SearchType.NONE.value
            resultPageSize = 32
        }
        val searchSessionListener = object : SearchListener {
            override fun onSearchResponse(response: Response) {
                // Handle search response.
                dismissLoadingDialog()
                data.clear()
                response.collection.children.forEach {
                    it.obj?.let { it1 -> data.add(it1) }
                }
                binding.rv.post {
                    yandexTipAdapter?.submitList(data)
                }
            }

            override fun onSearchError(p0: com.yandex.runtime.Error) {
                dismissLoadingDialog()
            }
        }
        val searchSessionListener2 = object : SearchListener {
            override fun onSearchResponse(response: Response) {
                dismissLoadingDialog()
                data.clear()
                response.collection.children.forEach {
                    it.obj?.let { it1 -> data.add(it1) }
                }
                binding.rv.post {
                    yandexTipAdapter?.submitList(data)
                }
//                currentYandexPoint = response.collection.children.get(0).obj
//                upBottom()
            }

            override fun onSearchError(p0: com.yandex.runtime.Error) {
                dismissLoadingDialog()
            }
        }
        val inputListener = object : InputListener {
            override fun onMapTap(map: com.yandex.mapkit.map.Map, point: Point) {
                showLoadingDialog()
                searchSession?.cancel()
                searchSession = searchManager?.submit(
                    point,
                    map.poiLimit,
                    searchOptions!!,
                    searchSessionListener2,
                )
            }

            override fun onMapLongTap(map: com.yandex.mapkit.map.Map, point: Point) {

            }
        }
        binding.mapview.map.addInputListener(inputListener)

        val geoObjectTapListener = object : GeoObjectTapListener {
            override fun onObjectTap(event: GeoObjectTapEvent): Boolean {
                println("onObjectTap ${event}")
                return false
            }
        }
        binding.mapview.map.addTapListener(geoObjectTapListener)

//        val region = VisibleRegionUtils.toPolygon(VisibleRegion())

        binding.btnYandexSearch.setOnClickListener {
            showLoadingDialog()
            searchSession?.cancel()
            searchSession = searchManager?.submit(
                binding.yandexEt.text.toString(),
                VisibleRegionUtils.toPolygon(binding.mapview.map.visibleRegion),
                searchOptions!!,
                searchSessionListener,
            )

        }
        showYandexTip()

        binding.yandexLocation.setOnClickListener {
            XXPermissions.with(this)
                .permission(Permission.ACCESS_FINE_LOCATION)
                .permission(Permission.ACCESS_COARSE_LOCATION)
                .request { permissions, allGranted ->
                    if (allGranted) {
                        getLastLocation()
//                        hasLocation = false
//                        startLocationUpdates()
                    } else {
                        permissionFail()
                    }
                }
        }

        XXPermissions.with(this)
            .permission(Permission.ACCESS_FINE_LOCATION)
            .permission(Permission.ACCESS_COARSE_LOCATION)
            .request { permissions, allGranted ->
                if (allGranted) {
//                    getLastLocation()
                    startLocationUpdates()
                } else {
                    permissionFail()
                }
            }

    }

    private val data: MutableList<GeoObject> = mutableListOf()
    private var yandexTipAdapter: YandexMapTipAdapter? = YandexMapTipAdapter()

    @SuppressLint("MissingPermission")
    private fun getLastLocation() {
        val locationManager: LocationManager = this.getSystemService(LOCATION_SERVICE) as LocationManager

        var loc = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
        Log.e("yandex", loc?.latitude.toString())
        Log.e("yandex", loc?.longitude.toString())
        if (loc != null) {
            moveYandexMap(Point(loc.latitude, loc.longitude))
            return
        }

        locationManager.requestLocationUpdates(
            LocationManager.GPS_PROVIDER,
            0,
            0f,
            mLocationListener
        );
    }

    private val mLocationListener = object : LocationListener {
        override fun onProviderEnabled(provider: String) {
        }

        override fun onProviderDisabled(provider: String) {
        }

        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {
        }


        override fun onLocationChanged(p0: Location) {
            moveYandexMap(Point(p0.latitude, p0.longitude))
        }
    };

    fun moveYandexMap(point: Point){
        val position = binding.mapview.map?.cameraPosition?.run {
            CameraPosition(
                point,
                15f,
                azimuth,
                tilt
            )
        } ?: return
        binding.mapview.map?.move(
            position,
            Animation(Animation.Type.SMOOTH, 0.5f),
            null
        )
    }


    private fun showYandexTip() {
        binding.rv.adapter = yandexTipAdapter
        binding.rv.layoutManager = LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false)
        yandexTipAdapter?.setOnItemClickListener { adapter, _, position ->
            currentYandexPoint = data[position]
            currentYandexPoint?.let {
                moveYandexTip(it)
            }
            upBottom()
        }
    }

    private fun yandexBack() {
        val add = currentYandexPoint?.descriptionText ?: currentYandexPoint?.name.toString()
        intent.putExtra(STORE_NAME, currentYandexPoint?.name.toString())
        intent.putExtra(STORE_ADD, add)
        intent.putExtra(STORE_LAT, currentYandexPoint?.geometry?.get(0)?.point?.latitude)
        intent.putExtra(STORE_LONG, currentYandexPoint?.geometry?.get(0)?.point?.longitude)
        setResult(RESULT_CODE, intent)
        finish()
    }

    private fun googleBack() {
        val list = currentPoi?.name?.split("\n")
        intent.putExtra(STORE_NAME, list?.get(0))
        intent.putExtra(STORE_ADD, list?.get(1))
        intent.putExtra(STORE_LAT, currentPoi?.latLng?.latitude)
        intent.putExtra(STORE_LONG, currentPoi?.latLng?.longitude)
        setResult(RESULT_CODE, intent)
        finish()
    }

    private fun initGoogle() {

        MapsInitializer.initialize(this@MapsActivity, MapsInitializer.Renderer.LATEST, this)

        Places.initialize(applicationContext, getResString(R.string.google_map_key))

        placesClient = Places.createClient(this)

        val autocompleteFragment =
            supportFragmentManager.findFragmentById(R.id.autocomplete_fragment) as AutocompleteSupportFragment
        autocompleteFragment.setPlaceFields(
            listOf(
                Place.Field.ID,
                Place.Field.NAME,
                Place.Field.RATING,
                Place.Field.LAT_LNG,
                Place.Field.OPENING_HOURS,
                Place.Field.ADDRESS,
                Place.Field.PHOTO_METADATAS,
                Place.Field.SECONDARY_OPENING_HOURS,
                Place.Field.CURRENT_OPENING_HOURS,
            )
        )
        if (!countryCode.isNullOrBlank()) {
            autocompleteFragment.setCountry(countryCode)
        }

//         Set up a PlaceSelectionListener to handle the response.
        autocompleteFragment.setOnPlaceSelectedListener(object : PlaceSelectionListener {
            override fun onPlaceSelected(place: Place) {
                addMarker(place)
                Log.i(TAG, "Place: ${place.name}, ${place.id}")
            }

            override fun onError(status: Status) {
                Log.i(TAG, "An error occurred: $status")
            }
        })


        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this)

        mapFragment?.getMapAsync(this)
    }

    override fun onStop() {
        super.onStop()
        if (!startGoogleMap) {
            binding.mapview.onStop()
            MapKitFactory.getInstance().onStop()
        }
    }

    override fun onStart() {
        super.onStart()
        if (!startGoogleMap) {
            MapKitFactory.getInstance().onStart()
            binding.mapview.onStart()
        }

    }

    private var progress: KProgressHUD? = null
    fun showLoadingDialog() {
        if (progress != null) {
            progress?.show()
        } else {
            progress = KProgressHUD.create(this)
                .setStyle(KProgressHUD.Style.SPIN_INDETERMINATE)
                .show()
        }
    }

    fun dismissLoadingDialog() {
        progress?.dismiss()
    }

    override fun onMapReady(googleMap: GoogleMap) {
        map = googleMap

        googleMap.mapType = GoogleMap.MAP_TYPE_NORMAL
        val uiSettings = googleMap.uiSettings
        uiSettings.isZoomControlsEnabled = true
        uiSettings.isMapToolbarEnabled = true
        uiSettings.isMyLocationButtonEnabled = true

        map?.setInfoWindowAdapter(MapInfoWindowAdapter(this.layoutInflater))
        map?.setOnInfoWindowClickListener {
            intent.putExtra(STORE_NAME, it.title)
            intent.putExtra(STORE_ADD, it.snippet)
            intent.putExtra(STORE_LAT, it.position.latitude)
            intent.putExtra(STORE_LONG, it.position.longitude)
            setResult(RESULT_CODE, intent)
            finish()
        }

        XXPermissions.with(this)
            .permission(Permission.ACCESS_FINE_LOCATION)
            .permission(Permission.ACCESS_COARSE_LOCATION)
            .request { permissions, allGranted ->
                if (allGranted) {
                    startLocationUpdates()
                } else {
                    permissionFail()
                }
            }
    }

    private fun startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        map?.isIndoorEnabled = true
        map?.isMyLocationEnabled = true  //定位
        map?.setOnPoiClickListener(this)

        val placeFields: List<Place.Field> = listOf(Place.Field.NAME)
        val ttsk = placesClient?.findCurrentPlace(FindCurrentPlaceRequest.newInstance(placeFields))

        ttsk?.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val response = task.result
                for (placeLikelihood: PlaceLikelihood in response?.placeLikelihoods ?: emptyList()) {
                    Log.i(
                        "wzz",
                        "Place '${placeLikelihood.place.name}' has likelihood: ${placeLikelihood.likelihood}"
                    )
                }
            } else {
                val exception = task.exception
                if (exception is ApiException) {
                    Log.e("wzz", "Place not found: ${exception.statusCode}")
                }
            }
        }

        fusedLocationProviderClient.requestLocationUpdates(
            LocationRequest.create().setPriority(LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY)//设置高精度
            , //3秒一次定位请求
            locationCallback,
            Looper.getMainLooper()
        )

        //获取地图中心位置
        map?.setOnCameraMoveListener {
            map?.cameraPosition?.target?.let {
                Log.e("地图中心位置", "Lat：${it.latitude}，Lng：${it.longitude}")
            }
        }

    }

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            if (hasLocation) {
                return
            }
            hasLocation = true
            for (location in locationResult.locations) {
                if (!startGoogleMap) {
                    println("yandex" + startGoogleMap + "---" + location.latitude + "====" + location.longitude)
                    val point = Point(location.latitude, location.longitude)
                    val position = binding.mapview.map?.cameraPosition?.run {
                        CameraPosition(
                            point,
                            15f,
                            azimuth,
                            tilt
                        )
                    } ?: return
                    yandexAddPlaceMarker(point)
                    println("yandex" + position.toString())
                    binding.mapview.map?.move(
                        position,
                        Animation(Animation.Type.SMOOTH, 0.5f),
                        null
                    )
                } else {
                    //google
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        drawLocationMarker(location, LatLng(location.latitude, location.longitude))
                    }
                }
            }
        }

    }
    private fun yandexAddPlaceMarker(point: Point){
        println("yandex" + binding.mapview.map.mapObjects)
        binding.mapview.map.mapObjects.addPlacemark().apply {
            geometry = point
            setIcon(ImageProvider.fromResource(this@MapsActivity, R.drawable.icon_location_select))
            setIconStyle(
                IconStyle().apply {
                    anchor = PointF(0.5f, 2.0f)
                    scale = 2.6f
                    zIndex = 20f
                }
            )
            setText("current location", TextStyle().apply {
                placement = TextStyle.Placement.BOTTOM
            })
        }
    }

    private fun moveYandexTip(geoObject: GeoObject) {
        val point = geoObject.geometry.get(0).point ?: return
        val imageProvider = ImageProvider.fromResource(this, R.drawable.search_result)
        val listener = MapObjectTapListener { p0, p1 ->
            yandexBack()
            true
        }
        binding.mapview.map?.mapObjects?.addPlacemark()?.apply {
            geometry = point
            setIcon(imageProvider)
            setIconStyle(
                IconStyle().apply {
                    anchor = PointF(0.5f, 2.0f)
                    scale = 1.6f
                    zIndex = 20f
                }
            )
            setText("${geoObject.name.toString()} \n ", TextStyle().apply {
                placement = TextStyle.Placement.BOTTOM
                size = 12f
            })
            addTapListener(listener)
        }
        val position = binding.mapview.map?.cameraPosition?.run {
            CameraPosition(point, 15f, azimuth, tilt)
        } ?: return
        binding.mapview.map?.move(position, Animation(Animation.Type.SMOOTH, 0.5f), null)
        val text = geoObject.name.toString()
        binding.yandexEt.setText(text.toCharArray(), 0, text.length)
        yandexTipAdapter?.submitList(mutableListOf())
    }
    @RequiresApi(Build.VERSION_CODES.O)
    private fun drawLocationMarker(location: Location, latLng: LatLng) {
        if (currentLocation == null) {//第一次定位画定位marker
            currentMarker = map?.addMarker(
                MarkerOptions().position(latLng).title("")
                //.icon(BitmapDescriptorFactory.fromResource(R.drawable.ic_vehicle_location))
            )
            map?.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, 14f))

        } else {
            val deltaTime = location.time - currentLocation!!.time
            //有方位精度
            if (location.hasBearingAccuracy()) {
                if (deltaTime <= 0) {
                    map?.animateCamera(
                        CameraUpdateFactory.newCameraPosition(
                            com.google.android.gms.maps.model.CameraPosition.Builder()
                                .target(latLng)
                                .zoom(map?.cameraPosition!!.zoom)
                                .bearing(location.bearing)
                                .build()

                        )
                    )
                } else {
                    map?.animateCamera(
                        CameraUpdateFactory.newCameraPosition(
                            com.google.android.gms.maps.model.CameraPosition.Builder()
                                .target(latLng)
                                .zoom(map?.cameraPosition!!.zoom)
                                .bearing(location.bearing)
                                .build()
                        ), deltaTime.toInt(), null
                    )
                }
                currentMarker?.rotation = 0f
            } else {
                if (deltaTime <= 0) {
                    map?.animateCamera(
                        CameraUpdateFactory.newLatLngZoom(
                            latLng,
                            map?.cameraPosition!!.zoom
                        )
                    )
                } else {
                    map?.animateCamera(
                        CameraUpdateFactory.newLatLngZoom(
                            latLng,
                            map?.cameraPosition!!.zoom
                        ), deltaTime.toInt(), null
                    )
                }
                //设置marker的指针方向
                currentMarker?.rotation = location.bearing - (map?.cameraPosition?.bearing ?: 0f)
            }

        }

        currentLocation = location

    }

    fun addMarker(place: Place) {
        currentPoi = PointOfInterest(place.latLng, place.id, "${place.name}\n${place.address}")
        upBottom()
        val latLng = place.latLng ?: return

        val metada = place.photoMetadatas
        if (metada == null || metada.isEmpty()) {
            Log.w(TAG, "No photo metadata.")
            val bitmap = getResDrawable(R.drawable.icon_launcher)?.toBitmap(300, 300)
            val marker = map?.addMarker(
                MarkerOptions()
                    .position(latLng)
                    .title(place.name)
                    .snippet("${place.address} ")
//                    ${place.secondaryOpeningHours}
            )
            marker?.tag = bitmap
            marker?.showInfoWindow()
            map?.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15f))
            return

        }
        val photoMetadata = metada.first()

        // Get the attribution text.
        val attributions = photoMetadata?.attributions

        // Create a FetchPhotoRequest.
        val photoRequest = FetchPhotoRequest.builder(photoMetadata)
            .setMaxWidth(400) // Optional.
            .setMaxHeight(400) // Optional.
            .build()

        placesClient?.fetchPhoto(photoRequest)
            ?.addOnSuccessListener { fetchPhotoResponse: FetchPhotoResponse ->
                val bitmap = fetchPhotoResponse.bitmap
                val marker = map?.addMarker(
                    MarkerOptions()
                        .position(latLng)
                        .title(place.name)
                        .snippet("${place.address} ")
//                    ${place.secondaryOpeningHours}
                )
                marker?.tag = fetchPhotoResponse.bitmap
                marker?.showInfoWindow()
                map?.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15f))
            }?.addOnFailureListener { exception: Exception ->
                if (exception is ApiException) {
                    Log.e(TAG, "Place not found: " + exception.message)
                    val statusCode = exception.statusCode
                }
            }


    }


    fun permissionFail() {

//        Log.e(TAG, "获取权限失败=$requestCode")

    }

    override fun onPoiClick(poi: PointOfInterest) {
//        currentPoi = poi
        currentPoi = PointOfInterest(poi.latLng, poi.placeId, "${poi.name}\n${poi.placeId}")
        upBottom()
    }

    override fun onMapsSdkInitialized(p0: MapsInitializer.Renderer) {
        println("22")
    }

}
