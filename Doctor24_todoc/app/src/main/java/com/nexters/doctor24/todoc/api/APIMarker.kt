package com.nexters.doctor24.todoc.api

import com.nexters.doctor24.todoc.data.marker.response.ResMapMarker
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

internal interface  APIMarker {
    @GET("medicals/{type}/latitudes/{latitude}/longitudes/{longitude}/facilities")
    suspend fun getMarkers(
        @Path("type") type: String,
        @Path("latitude") latitude: String,
        @Path("longitude") longitude: String
    ): List<ResMapMarker>

    @GET("medicals/{type}/xlatitudes/{xlatitude}/xlongitudes/{xlongitude}/zlatitudes/{zlatitude}/zlongitudes/{zlongitude}/facilities")
    suspend fun getBounds(
        @Path("type") type: String,
        @Path("xlatitude") xlat: String,
        @Path("xlongitude") xlong: String,
        @Path("zlatitude") zlat: String,
        @Path("zlongitude") zlong: String
    ): List<ResMapMarker>
}