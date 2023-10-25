package com.vapergift.app.map

import android.graphics.Bitmap
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.Marker
import com.vapergift.app.R

class MapInfoWindowAdapter(layoutInflater: LayoutInflater) : GoogleMap.InfoWindowAdapter {

    private val window: View = layoutInflater.inflate(R.layout.custom_info_window, null)
    private val contents: View = layoutInflater.inflate(R.layout.custom_info_contents, null)

    override fun getInfoWindow(marker: Marker): View? {
        render(marker, window)
        return window
    }

    override fun getInfoContents(marker: Marker): View? {
        render(marker, contents)
        return contents
    }

    private fun render(marker: Marker, view: View) {
        val badge = marker.tag ?: ""

        val iv  = view.findViewById<ImageView>(R.id.badge)
        val title: String? = marker.title
        val snippet: String? = marker.snippet
        val titleUi = view.findViewById<TextView>(R.id.title)
        val snippetUi = view.findViewById<TextView>(R.id.snippet)
        if(badge is Bitmap){
            iv.setImageBitmap(badge as Bitmap?)
        }


        titleUi.text = title
        snippetUi.text = snippet

    }
}