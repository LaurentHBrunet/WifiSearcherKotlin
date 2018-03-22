package com.brunet.henault.laurent.wi_fisearcher

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.support.v4.app.ActivityCompat
import android.support.v7.app.AlertDialog
import android.support.v7.app.AppCompatActivity
import android.view.*
import android.widget.*
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import kotlinx.android.synthetic.main.activity_wifi_searcher.*

class wifiSearcher : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var mMap: GoogleMap   //instance of the google maps fragment
    private lateinit var wifiManager: WifiManager  //wifi manager, to scan networks
    private lateinit var databaseManager : DatabaseInterface //interface with location/favorites db
    private lateinit var broadCastReceiver: BroadcastReceiver //BroadcastReceiver to manage scanResults
    private lateinit var apManager: AccessPointsManager  //manager of current access points and favorites
    private val hashMapMarker = HashMap<String, AccessPointMarker>()  //map of markers currently on map
    private val hashMapFavoritesMarker = HashMap<String, Marker>()  //map of favorites markers currently on map

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_wifi_searcher)

        databaseManager = DatabaseInterface(Settings.Secure.getString(this.getContentResolver(), Settings.Secure.ANDROID_ID))
        apManager = AccessPointsManager(databaseManager)
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment

        mapFragment.getMapAsync(this)

        checkPermission() //Checks with user to give needed permissions to run app

        setupBroadCastReceiver() //Setup broadcast receiver to get scanResults complete events
    }

    //Uses custom menu bar
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_wifi_searcher,menu)
        return super.onCreateOptionsMenu(menu)
    }


    //Sets up broadcast receiver, to manage when SCAN_RESULTS_AVAILABLE_ACTION is triggered
    fun setupBroadCastReceiver() {

        wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

        broadCastReceiver = object: BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    WifiManager.SCAN_RESULTS_AVAILABLE_ACTION -> startUpdateLocationProccess() //called in answer to scanResults()
                }
            }
        }

        var intentFilt = IntentFilter()
        intentFilt.addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
        registerReceiver(broadCastReceiver,intentFilt)
    }

    //Clears the current access points and updates them with the new scanResults
    fun startUpdateLocationProccess(){
        apManager.clearCurrentAccessPoints()  //Clear aps

        //assign newly scanned aps
        wifiManager.scanResults.forEach {
            apManager.addAPcurrentList(AccessPoint(it.BSSID,it.SSID, currentLocation!!.latitude, currentLocation!!.longitude,it.level,it.capabilities,null))
        }

        databaseManager.updateLatLonRSSI(apManager, this.currentLocation!!) //updates aps location in DB
    }

    var ACCESLOCATION=123

    //Checks if the user has the right permissions enabled to use GPS
    fun checkPermission() {
        if(Build.VERSION.SDK_INT >= 23) {
            if(ActivityCompat.checkSelfPermission(this,android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION), ACCESLOCATION) //if permission isn't enabled, ask user for it
                return
            }
        }
        GetUserLocation()
    }

    //Sets up location listeners and threads to update map once permission is granted from user
    @SuppressLint("MissingPermission")
    fun GetUserLocation() {
        Toast.makeText(this,"User location access on", Toast.LENGTH_LONG).show()

        var myLocation = MyLocationListener()

        var locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager

        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 3, 3f, myLocation)

        val scanWifiThread = ScanWifiThread()  //Starts thread to scan nearby aps
        scanWifiThread.start()

        val updateMapThread = updateMap()  //Starts thread to update map with nearby aps
        updateMapThread.start()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        when(requestCode){
            ACCESLOCATION->{

                if(grantResults[0]==PackageManager.PERMISSION_GRANTED){
                    GetUserLocation() //If user grants permission, start location and map threads
                } else {
                    Toast.makeText(this,"User location access denied", Toast.LENGTH_LONG).show()
                }
            }
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
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

        val montrealPos = LatLng(45.546937, -73.688703) //Assign base position
        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(montrealPos,9.0f)) //Zoom in to that base position

        if(ActivityCompat.checkSelfPermission(this,android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            mMap.isMyLocationEnabled = true //enables map marker for user position
        }

        mMap.setOnMapClickListener {
            selectedView.text = "No access point selected"  //When map is clicked, "un-select" access point
        }



        mMap.setOnMarkerClickListener{ marker ->
            val BSSID = marker.snippet
            val SSID = marker.title
            val accessPoint = apManager.getAPFromBSSID(BSSID)
            if(accessPoint != null){  //if marker clicked is current AP, show SSID, BSSID, RSSI, SECURITY in selected Text View
                val RSSI = accessPoint?.getRSSI()
                val sec = accessPoint?.getSecurity()
                selectedView.text = "SSID : " + SSID + "\n" + "BSSID : " + BSSID + "\n" + "RSSI: " + RSSI + "\n" + "Security : " + sec
            } else { //if marker clicked is a favorite, show SSID, BSSID, SECURITY, (There is no RSSI since it is not in range)
               val favorite = apManager.getFavoriteFromBSSID(BSSID)
                if(favorite != null){
                    val sec = favorite?.getSecurity()
                    selectedView.text = "SSID : " + SSID + "\n" + "BSSID : " + BSSID + "\n" +  "Security : " + sec
                } else {
                    selectedView.text = " There is no information about access point"
                }
            }

            false //Keep using normal onMarkerClick events ( show title, enable directions, etc)
        }

    }

    var currentLocation:Location ?= null

    //Location listener, that updates whenever the location of the user changes
    inner class MyLocationListener: LocationListener {
        constructor() {
            currentLocation = Location("Start")
            currentLocation!!.longitude = -0.123  //Base value to manage when user position hasn't been found yet
            currentLocation!!.latitude = -0.123   // "" , probability of lat and long == -0.123f is functionally zero
        }

        override fun onLocationChanged(location: Location?) {
            currentLocation = location //Assigns new user location
        }

        override fun onStatusChanged(p0: String?, p1: Int, p2: Bundle?) {
        }

        override fun onProviderEnabled(p0: String?) {
        }

        override fun onProviderDisabled(p0: String?) {
        }
    }


    //Thread scans the network every few seconds to update the current APs
    inner class ScanWifiThread:Thread {
        constructor():super(){
        }

        override fun run(){
            while(true){
                try{
                    runOnUiThread {
                        wifiManager.startScan()
                    }

                    Thread.sleep(5000)
                }catch (ex:Exception){

                }
            }
        }
    }

    //Thread takes the current APs that were scanned, and manages the markers of these nearby APs
    inner class updateMap:Thread {
        constructor():super(){

        }

        override fun run(){
            while(true){
                try{
                    runOnUiThread {

                        hashMapMarker.forEach { //Sets every marker to unused, so they are removed if not updated in this cycle
                            it.value.setUsed(false)
                        }

                        for (currentAccessPoint in apManager.getCurrentAccessPoints()) {
                            val currentBSSID = currentAccessPoint.getBSSID()
                            val currentLatLon = currentAccessPoint.getLatLon()

                            if (hashMapMarker.containsKey(currentAccessPoint.getBSSID())) {  //If marker already exists
                                hashMapMarker.get(currentBSSID)!!.getMarker().position = currentLatLon //Move marker to new position (Without creating new one)
                                hashMapMarker.get(currentBSSID)!!.setUsed(true) //Mark is as used for this cycle
                            } else {
                                if(currentLatLon.latitude != -0.123 || currentLatLon.longitude != -0.123) { //If marker doesn't exist yet, check if it's value is correctly initiated
                                    hashMapMarker.put(currentBSSID, AccessPointMarker(mMap.addMarker(MarkerOptions() //Add new marker in hashmap and googleMap, automatically set to used for this cycle
                                            .title(currentAccessPoint.getSSID())
                                            .snippet(currentBSSID)
                                            .position(currentLatLon))))

                                    if (currentAccessPoint.getSecurity().equals("Open")) { //Check AP  is password protected or not
                                        hashMapMarker.get(currentBSSID)?.getMarker()?.setIcon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)) //Green marker icon if not protected

                                    } else {
                                        hashMapMarker.get(currentBSSID)?.getMarker()?.setIcon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)) //Red marker icon if password protected
                                    }
                                }
                            }
                        }

                        val markersToRemove = mutableListOf<String>()
                        hashMapMarker.forEach { //Checks hashMap for every marker that was not updated or added during this cycle and save it's key
                            if(it.value.isUsed() == false){
                                it.value.getMarker().remove() //Remove from map
                                markersToRemove.add(it.key)
                            }
                        }

                        for(markerKey in markersToRemove){
                            hashMapMarker.remove(markerKey) //Remove from hashMap using previously found keys
                        }
                    }

                    Thread.sleep(4000)
                }catch (ex:Exception){

                }
            }
        }
    }

    //Sets up action bar selection events
    override fun onOptionsItemSelected(item: MenuItem?): Boolean {

        when(item?.itemId){
            R.id.list_item-> showListsDialog()
            R.id.info_item -> showBasicInfo()
        }

        return super.onOptionsItemSelected(item)
    }

    //Shows the lists of favorites and nearby APs in dialog
    fun showListsDialog(){

        val linf = LayoutInflater.from(this) //get layoutInflater
        val inflator = linf.inflate(R.layout.list_dialog,null) //inflate the custom dialog layout
        var dialogBuilder = AlertDialog.Builder(this) //Build a dialog

        var list = inflator.findViewById<ListView>(R.id.current_ap_list)  //Find nearby APs View from  dialog layout
        var favoriteList = inflator.findViewById<ListView>(R.id.favorites_list) //Find favorites View from dialog layout

        dialogBuilder.setView(inflator) //set dialog view to dialog custom layout

        var dialog = dialogBuilder.create()

        //Next two lines take the views for nearby APs and Favorites, sets their adapters to custom Adapter, so their layouts are custom(ap_list_row) and their dataset is currentFavorites and currentAccessPoints respectively
        favoriteList.adapter = AccessPointListAdapter(this,R.layout.ap_list_row,ArrayList(apManager.getFavorites()),true, null)
        list.adapter = AccessPointListAdapter(this,R.layout.ap_list_row,ArrayList(apManager.getCurrentAccessPoints()),false, favoriteList.adapter as AccessPointListAdapter)

        dialog.show()
    }

    inner class AccessPointListAdapter(context: Context,
                                       textViewResourceId: Int,
                                       private val items: ArrayList<AccessPoint>, //Array that will be used to fill list
                                       private val isFavorites: Boolean, //If the adapter is for favorites view or current APs view
                                       private val favoritesListAdapter: AccessPointListAdapter?) //If this is the current APs view, pass favoritesList adapter so it can be accessed from this
        : ArrayAdapter<AccessPoint>(context, textViewResourceId, items) {

        //For each item in items getView is called to fill a row of the List
        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            var view: View? = convertView
            if (view == null) {
                val viewInflater = LayoutInflater.from(context)
                view = viewInflater.inflate(R.layout.ap_list_row, null) //Sets row view to custom ap_list_row
            }
            val obj = items[position]
            if (obj != null) {
                //Get layout Views
                val ssidTV= view!!.findViewById<TextView>(R.id.ap_list_ssid)
                val bssidTV = view!!.findViewById<TextView>(R.id.ap_list_bssid)
                val rssiTV = view!!.findViewById<TextView>(R.id.ap_list_rssi)
                val secTV = view!!.findViewById<TextView>(R.id.ap_list_security)
                val favoriteButton = view!!.findViewById<ImageButton>(R.id.list_favorite_button)
                val shareButton = view!!.findViewById<ImageButton>(R.id.list_share_button)

                //Set layout text based on current object
                if (ssidTV != null) {
                    ssidTV.text = "SSID: " + obj.getSSID()
                }
                if (bssidTV != null) {
                    bssidTV.text = "BSSID: " + obj.getBSSID()
                }
                if (secTV != null) {
                    secTV.text = "Security: " + obj.getSecurity()
                }

                //Setup share button
                shareButton.setOnClickListener{
                    val shareText = "SSID: " + obj.getSSID() + "\n" +
                                    "BSSID: " + obj.getBSSID() + "\n" +
                                    "RSSI: " + obj.getRSSI() + "\n" +
                                    "Security: " + obj.getSecurity()

                    val shareIntent = Intent()
                    shareIntent.action = Intent.ACTION_SEND
                    shareIntent.type="text/plain"
                    shareIntent.putExtra(Intent.EXTRA_TEXT, shareText)
                    context.startActivity(Intent.createChooser(shareIntent,"Share access point info"))
                }


                if(isFavorites){ //If this is the favorites View
                    val pinButton = view!!.findViewById<ImageButton>(R.id.pin_button)
                    val buttonLine = view!!.findViewById<LinearLayout>(R.id.ap_button_line)
                    pinButton.visibility = View.VISIBLE //Show hidden pin Button
                    buttonLine.weightSum = 3F //Make all three buttons equal width

                    pinButton.setOnClickListener{
                        manageFavoriteMarker(obj,false) //add/remove a favorite marker
                    }

                    favoriteButton.setOnClickListener {
                        apManager.removeApFromFavorites(obj) //remove from favorites
                        items.remove(obj)  //Update the list used in the dialog
                        manageFavoriteMarker(obj,true) //Remove favorite marker if it exists
                        notifyDataSetChanged() //notify change in dialog data so the view is refreshed
                    }

                    rssiTV.visibility = View.GONE //Hide RSSI text
                } else { //If this is current APs view
                    favoriteButton.setOnClickListener{
                        if(apManager.addApToFavorites(obj)){ //Add AP to favorites, and update dialog list
                            favoritesListAdapter!!.add(obj)
                            favoritesListAdapter!!.notifyDataSetChanged()
                        }
                    }

                    if (rssiTV != null) {
                        rssiTV.text = "RSSI: " + obj.getRSSI() //If current AP show RSSI
                    }
                }
            }
            return view!!
        }
    }

    //Switched to basic info activity that has some basic text to explain the app
    fun showBasicInfo(){
        unregisterReceiver(broadCastReceiver)
        val infoIntent = Intent(this@wifiSearcher,InfoActivity::class.java)
        startActivity(infoIntent)
    }

    //Manages pin button pressed event inside the list dialog
    fun manageFavoriteMarker(accessPoint: AccessPoint, isFavoriteRemoved: Boolean){
        val apBSSID = accessPoint.getBSSID()

        if(hashMapFavoritesMarker.containsKey(apBSSID)){ //if the marker is in hashMap remove it from hashmap and map
            hashMapFavoritesMarker.get(apBSSID)!!.remove()
            hashMapFavoritesMarker.remove(apBSSID)
        } else if(!isFavoriteRemoved){ //if the marker isn't in hashMap add it to the hashMap and the map, unless a favorite is being removed
            hashMapFavoritesMarker.put(apBSSID, mMap.addMarker(MarkerOptions()
                    .title(accessPoint.getSSID())
                    .snippet(apBSSID)
                    .position(accessPoint.getLatLon())
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_YELLOW))))
        }
    }
}
