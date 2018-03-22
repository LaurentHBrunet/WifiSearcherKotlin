package com.brunet.henault.laurent.wi_fisearcher

import com.google.android.gms.maps.model.LatLng



/**
 * Created by laurent on 2018-02-20.
 */

//Object representing an access point
//If it represents a favorite, RSSI can be null
class AccessPoint(private val BSSID: String,
                  private val SSID: String,
                  private var lat: Double,
                  private var lon: Double,
                  private var RSSI: Int?,
                  capabilities: String?, //Result of scanResult.capabilities, to be parsed
                  security: String?) { //Actual security value, when favorite is being copied from another AccessPoint

    var securityValue: String

    init{
        if(capabilities != null) {
            securityValue = parseSecurityType(capabilities)
        } else if(security != null){
            securityValue = security
        } else {
            securityValue= "N/A"
        }
    }

    fun setLatLon(latitude : Double, longitude : Double){
        lat = latitude
        lon = longitude
    }

    fun setRSSI(rssi: Int){
        RSSI = rssi
    }

    fun getLatLon() : LatLng {
        return LatLng(lat,lon)
    }

    fun getBSSID() : String {
        return BSSID
    }

    fun getSSID() : String {
        return SSID
    }

    fun getSecurity() : String {
        return securityValue
    }

    fun getRSSI() : Int {
        return RSSI!!
    }


    //Checks capabilities if one of the following security modes is present, and returns that security mode
    fun parseSecurityType(security: String):String{
        val securityModes = arrayOf<String>("WEP", "WPA", "WPA2", "WPA_EAP", "IEEE8021X")
        for (i in securityModes.indices.reversed()) {
            if (security.contains(securityModes[i])) {
                return securityModes[i]
            }
        }
        return "Open" //if none of the security modes are present, the access point is open
    }
}
