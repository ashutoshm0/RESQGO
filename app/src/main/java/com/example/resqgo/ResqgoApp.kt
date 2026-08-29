package com.example.resqgo

import android.app.Application
import com.example.resqgo.data.local.AppDatabase
import com.example.resqgo.data.repository.RideRepository

class ResqgoApp : Application() {
    // Lazy initialization of Database and Repository
    val database by lazy { AppDatabase.getDatabase(this) }
    val repository by lazy { RideRepository(database.rideEventDao()) }
}
