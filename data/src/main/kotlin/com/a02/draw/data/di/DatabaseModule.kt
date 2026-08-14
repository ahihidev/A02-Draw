package com.a02.draw.data.di

import android.content.Context
import androidx.room.Room
import com.a02.draw.data.local.database.DrawDatabase
import com.a02.draw.data.local.database.dao.DrawingDao
import com.a02.draw.data.local.database.dao.LessonDao
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
    fun provideDatabase(@ApplicationContext context: Context): DrawDatabase =
        Room.databaseBuilder(context, DrawDatabase::class.java, DrawDatabase.DATABASE_NAME)
            .build()

    @Provides
    fun provideDrawingDao(database: DrawDatabase): DrawingDao = database.drawingDao()

    @Provides
    fun provideLessonDao(database: DrawDatabase): LessonDao = database.lessonDao()
}
