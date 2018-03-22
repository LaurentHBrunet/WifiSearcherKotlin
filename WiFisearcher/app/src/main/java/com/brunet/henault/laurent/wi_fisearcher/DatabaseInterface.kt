package com.brunet.henault.laurent.wi_fisearcher

import android.location.Location
import com.google.firebase.database.*

/**
 * Created by laurent on 2018-02-22.
 */

//Class that interfaces with the FireBase database where AP locations and favorites are
class DatabaseInterface(private var phoneID : String) { //unique phone ID to store favorites in DB
    private var mDatabaseRef : DatabaseReference
    private var mDatabase : FirebaseDatabase
    private lateinit var referencedApManager : AccessPointsManager //reference to apManager to send back data to it asynchronously

    init{
        mDatabase = FirebaseDatabase.getInstance()
        mDatabaseRef = mDatabase.reference
    }


    //Request to update the current APs positions
    fun updateLatLonRSSI(apManager: AccessPointsManager, currentPos: Location){

        referencedApManager = apManager

        for (accessPoint in referencedApManager.getCurrentAccessPoints()) {
            val singleValueListener = object: ValueEventListener{
                override fun onCancelled(p0: DatabaseError?) {

                }

                override fun onDataChange(dataSnapshot: DataSnapshot?) {
                    //Log.d("debug", dataSnapshot.toString())
                    if (dataSnapshot!!.value != null) { //if return is not null, update or keep current value in DB
                        manageQueryReturn(dataSnapshot)
                    }
                    else{
                        addNewAccessPointToDB(accessPoint, currentPos) //if return is null, check if new AP needs to be added
                    }
                }
            }

            var dbRefTest = mDatabase.getReference(accessPoint.getBSSID())
            dbRefTest.addListenerForSingleValueEvent(singleValueListener)
        }
    }


    private fun manageQueryReturn(dataSnapshot: DataSnapshot){
        if(dataSnapshot.child("LAT").value as Double != -0.123){ //If the value in DB was initialized correctly

            val LAT = dataSnapshot.child("LAT").value as Double
            val LON = dataSnapshot.child("LON").value as Double
            val RSSI = dataSnapshot.child("RSSI").value as Long
            val BSSID = dataSnapshot.key.toString()

            val currentAPBSSID = referencedApManager.getRSSIFromBSSID(BSSID)

            if(currentAPBSSID != null && currentAPBSSID > RSSI){ //If the current RSSI is better than the value in the DB
                val newBDLatLng = referencedApManager.getLatLonFromBSSID(BSSID)

                if(newBDLatLng != null) {
                    replaceValueInBD(BSSID, newBDLatLng.latitude, newBDLatLng.longitude,currentAPBSSID) //Replace value in DB with current pos and update RSSI to current RSSI
                }
            } else {
                referencedApManager.setLatLonSec(BSSID, LAT, LON) //If current RSSI is worse, use the position that is currently in the DB
            }
        }
    }

    private fun replaceValueInBD(bssid: String, lat: Double, lon: Double, rssi: Int){
        mDatabaseRef.child(bssid).child("LAT").setValue(lat)
        mDatabaseRef.child(bssid).child("LON").setValue(lon)
        mDatabaseRef.child(bssid).child("RSSI").setValue(rssi)
    }

    private fun addNewAccessPointToDB(accessPoint: AccessPoint, currentPos: Location) {
        if(currentPos.latitude != -0.123 || currentPos.longitude != -0.123) { //Check if currentPos was initialized correctly
            mDatabaseRef.child(accessPoint.getBSSID())
            mDatabaseRef.child(accessPoint.getBSSID()).child("LAT").setValue(currentPos.latitude)
            mDatabaseRef.child(accessPoint.getBSSID()).child("LON").setValue(currentPos.longitude)
            mDatabaseRef.child(accessPoint.getBSSID()).child("RSSI").setValue(accessPoint.getRSSI())
        }
    }

    //Reset favorites for a specific phone and fill favorites with current favorites
    fun updateFavoritesDb(favorites: List<AccessPoint>){
        mDatabaseRef.child("UserFavorites").child(phoneID).removeValue()
        favorites.forEach {
            mDatabaseRef.child("UserFavorites")
            mDatabaseRef.child("UserFavorites").child(phoneID).child(it.getBSSID()).child("LAT").setValue(it.getLatLon().latitude)
            mDatabaseRef.child("UserFavorites").child(phoneID).child(it.getBSSID()).child("LON").setValue(it.getLatLon().longitude)
            mDatabaseRef.child("UserFavorites").child(phoneID).child(it.getBSSID()).child("SSID").setValue(it.getSSID())
            mDatabaseRef.child("UserFavorites").child(phoneID).child(it.getBSSID()).child("SEC").setValue(it.getSecurity())
        }
    }

    //Request favorites from DB for this phoneID
    fun getFavorites(apManager:AccessPointsManager){
        referencedApManager = apManager

        val favoritesRequestListener = object: ValueEventListener{
            override fun onCancelled(p0: DatabaseError?) {

            }

            override fun onDataChange(dataSnapshot: DataSnapshot?) {
                if (dataSnapshot!!.value != null) {
                    updateLocalFavorites(dataSnapshot) //If favorites exist
                }
            }
        }

        var dbRefTest = mDatabase.getReference("UserFavorites/"+ phoneID)
        dbRefTest.addListenerForSingleValueEvent(favoritesRequestListener)
    }

    //Parse the values returned from DB favorites
    fun updateLocalFavorites(dataSnapshot: DataSnapshot){
        var updatedLocalFavorites = mutableListOf<AccessPoint>()
        var bssid: String
        var ssid: String
        var lat: Double
        var lon: Double
        var security: String

        for(ap in dataSnapshot.children){
            bssid = ap.key
            ssid = ap.child("SSID").value as String
            security = ap.child("SEC").value as String
            lat = ap.child("LAT").value as Double
            lon = ap.child("LON").value as Double

            updatedLocalFavorites.add(AccessPoint(bssid,ssid,lat,lon,null,null,security))
        }

        referencedApManager.setFavorites(updatedLocalFavorites) //Set the local favorites to the new values found from DB
    }

}