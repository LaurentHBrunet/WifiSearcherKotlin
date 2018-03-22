package com.brunet.henault.laurent.wi_fisearcher

import com.google.android.gms.maps.model.Marker

/**
 * Created by laurent on 2018-02-22.
 */

//Simple object that is a marker and a boolean to see if the marker was used in last update cycle
class AccessPointMarker(private val marker: Marker) {

    private var used = true

    fun isUsed(): Boolean{
        return used
    }

    fun setUsed(boolean: Boolean){
        used = boolean
    }

    fun getMarker(): Marker{
        return marker
    }
}