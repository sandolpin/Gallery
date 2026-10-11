package com.sandolpin.gallery.di

import android.content.Context
import androidx.room.Room
import com.sandolpin.gallery.data.local.db.AnalysisDao
import com.sandolpin.gallery.data.local.db.FavoriteDao
import com.sandolpin.gallery.data.local.db.GalleryDatabase
import com.sandolpin.gallery.data.local.db.GalleryMigrations
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** アプリ全体で1つだけ作る、Roomのデータベース。 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): GalleryDatabase =
        Room.databaseBuilder(context, GalleryDatabase::class.java, "gallery.db")
            // 作り直し(fallbackToDestructiveMigration)は使わない。お気に入りが消えてしまうため
            .addMigrations(GalleryMigrations.Migration1To2, GalleryMigrations.Migration2To3)
            .build()

    @Provides
    fun provideFavoriteDao(database: GalleryDatabase): FavoriteDao = database.favoriteDao()

    @Provides
    fun provideAnalysisDao(database: GalleryDatabase): AnalysisDao = database.analysisDao()
}
