package com.socialfirstaidkit.feature.socialcopy.di

import android.content.Context
import com.socialfirstaidkit.feature.socialcopy.facedetection.FaceDetectionService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.scopes.ViewModelScoped

@Module
@InstallIn(ViewModelComponent::class)
object SocialCopyModule {

    @Provides
    @ViewModelScoped
    fun provideFaceDetectionService(
        @ApplicationContext context: Context
    ): FaceDetectionService {
        return FaceDetectionService(context)
    }
}
