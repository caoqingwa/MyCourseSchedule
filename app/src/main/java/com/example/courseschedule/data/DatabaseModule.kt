package com.example.courseschedule.data

import android.content.Context
import androidx.room.Room
import com.example.courseschedule.data.db.AppDatabase
import com.example.courseschedule.data.db.dao.*
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
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(context, AppDatabase::class.java, "course_schedule.db")
            .fallbackToDestructiveMigration()
            .build()
    }
    @Provides fun provideSemesterDao(db: AppDatabase): SemesterDao = db.semesterDao()
    @Provides fun provideCourseDao(db: AppDatabase): CourseDao = db.courseDao()
    @Provides fun provideScheduleDao(db: AppDatabase): ScheduleDao = db.scheduleDao()
    @Provides fun provideRoomDao(db: AppDatabase): RoomDao = db.roomDao()
    @Provides fun provideExamDao(db: AppDatabase): ExamDao = db.examDao()
}
