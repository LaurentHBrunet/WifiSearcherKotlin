package com.brunet.henault.laurent.wi_fisearcher

import com.google.android.gms.maps.model.LatLng

/**
 * Created by laurent on 2018-02-20.
 */

//Manager of the currentAccessPoints and FavoriteAccessPoints
//Has a reference to the database interface
class AccessPointsManager(private var dbInterface: DatabaseInterface) {
    private var currentAccessPoints = mutableListOf<AccessPoint>()  //List of currently in range access points WITH position data
    private var favoriteAccessPoints = mutableListOf<AccessPoint>() //Favorite access points, fetched from DB

    init{
        loadFavorites() //Load favorites from database
    }

    fun clearCurrentAccessPoints(){
        currentAccessPoints.clear()
    }

    //Adds an AP to the current AP list, following a scanResult event
    fun addAPcurrentList(newAP : AccessPoint){
        currentAccessPoints.add(newAP)
    }

    //Set position of AP with specific BSSID from current AP list
    fun setLatLonSec(bssid: String, lat: Double, lon: Double){
        currentAccessPoints.forEach{ value ->
            if(value.getBSSID() == bssid){
                value.setLatLon(lat,lon)
            }
        }
    }

    fun getFavorites(): List<AccessPoint>{
        return favoriteAccessPoints
    }

    fun getCurrentAccessPoints(): List<AccessPoint> {
        return currentAccessPoints
    }

    //Get RSSI of AP with specific BSSID from current AP list
    fun getRSSIFromBSSID(bssid: String) : Int?{
        currentAccessPoints.forEach {
            if(it.getBSSID().equals(bssid)){
                return it.getRSSI()
            }
        }

        return null
    }

    //Get position of AP with specific BSSID from current AP list
    fun getLatLonFromBSSID(bssid : String): LatLng?{
        currentAccessPoints.forEach {
            if(it.getBSSID().equals(bssid)){
                return it.getLatLon()
            }
        }

        return null
    }

    //Get AP with specific BSSID from favorites
    fun getFavoriteFromBSSID(bssid: String): AccessPoint?{
        favoriteAccessPoints.forEach {
            if(it.getBSSID().equals(bssid)){
                return it
            }
        }
        return null
    }

    //Get AP with specific BSSID from currentAP List
    fun getAPFromBSSID(bssid : String): AccessPoint?{
        currentAccessPoints.forEach {
            if(it.getBSSID().equals(bssid)){
                return it
            }
        }
        return null
    }

    //Request database Interface to check for favorites and update favoritesList
    fun loadFavorites(){
        dbInterface.getFavorites(this)
    }

    //Function should be used by database Interface to set favorites after it was request with getFavorites
    fun setFavorites(favorites: MutableList<AccessPoint>){
        favoriteAccessPoints = favorites
    }

    //Function called to add a favorite/ udate the current favorites and the favorites in the DB. When favorite button is clicked in list dialog (current AP listView)
    fun addApToFavorites(accessPoint: AccessPoint): Boolean{
        if(favoriteAccessPoints.contains(accessPoint)){
            return false
        } else {
            favoriteAccessPoints.add(accessPoint)

            dbInterface.updateFavoritesDb(favoriteAccessPoints)
            return true
        }
    }

    //Function called to remove a favorite/ update the current favorites and the favorites in the DB when favorite button is clicked in list dialog (favorite AP listView)
    fun removeApFromFavorites(accessPoint: AccessPoint){
        favoriteAccessPoints.remove(accessPoint)
        dbInterface.updateFavoritesDb(favoriteAccessPoints)
    }
}