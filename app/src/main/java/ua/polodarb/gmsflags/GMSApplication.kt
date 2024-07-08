package ua.polodarb.gmsflags

import android.app.Application
import com.google.android.material.color.DynamicColors
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.androidx.workmanager.koin.workManagerFactory
import org.koin.core.context.startKoin
import org.koin.core.logger.Level
import ua.polodarb.domain.di.useCaseModule
import ua.polodarb.gms.impl.di.initRootDBModule
import ua.polodarb.gmsflags.di.appModule
import ua.polodarb.gmsflags.di.viewModelsModule
import ua.polodarb.gmsflags.errors.gms.phixit.di.workerPhixitModule
import ua.polodarb.local.impl.di.localDBBindsModule
import ua.polodarb.local.impl.di.localDatabaseModule
import ua.polodarb.network.impl.di.networkBindsModule
import ua.polodarb.platform.di.platformModule
import ua.polodarb.preferences.impl.di.prefsModule
import ua.polodarb.byteUtils.di.byteUtilsModule
import ua.polodarb.gmsflags.errors.general.CrashActivity
import ua.polodarb.gmsflags.errors.general.ExceptionHandler
import ua.polodarb.repository.impl.di.repositoryBindsModule
import ua.polodarb.updates.di.workerModule

class GMSApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        if (!BuildConfig.DEBUG)
            ExceptionHandler.initialize(this, CrashActivity::class.java)

        DynamicColors.applyToActivitiesIfAvailable(this)

        startKoin {
            androidLogger(if (BuildConfig.DEBUG) Level.DEBUG else Level.NONE)
            androidContext(this@GMSApplication)
            workManagerFactory()
            modules(
                listOf(
                    appModule,
                    initRootDBModule,
                    viewModelsModule,
                    localDatabaseModule,
                    localDBBindsModule,
                    useCaseModule,
                    platformModule,
                    workerModule,
                    workerPhixitModule,
                    prefsModule,
                    byteUtilsModule,
                    networkBindsModule,
                    repositoryBindsModule
                )
            )
        }
    }

}
