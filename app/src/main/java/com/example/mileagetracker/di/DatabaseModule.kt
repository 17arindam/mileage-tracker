package com.example.mileagetracker.di

import android.content.Context
import androidx.room.Room
import com.example.mileagetracker.data.dao.CurrentTrackDao
import com.example.mileagetracker.data.dao.TrackPointDao
import com.example.mileagetracker.data.database.MileageDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): MileageDatabase {
        return Room.databaseBuilder(
            context,
            MileageDatabase::class.java,
            "mileage_database"
        ).build()
    }

    @Provides
    fun provideTrackPointDao(database: MileageDatabase): TrackPointDao {
        return database.trackPointDao()
    }

    @Provides
    fun provideCurrentTrackDao(database: MileageDatabase): CurrentTrackDao {
        return database.currentTrackDao()
    }
}