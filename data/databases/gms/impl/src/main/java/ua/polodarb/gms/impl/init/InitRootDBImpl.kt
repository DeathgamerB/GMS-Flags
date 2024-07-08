package ua.polodarb.gms.impl.init

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import com.topjohnwu.superuser.ipc.RootService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import ua.polodarb.common.Constants
import ua.polodarb.gms.IRootDatabase
import ua.polodarb.gms.impl.RootDatabase
import ua.polodarb.gms.init.DatabaseInitializationState
import ua.polodarb.gms.init.InitRootDB

class InitRootDBImpl(
    private val context: Context
): InitRootDB {

    private val _databaseInitializationStateFlow =
        MutableStateFlow(DatabaseInitializationState(false))
    override val databaseInitializationStateFlow: Flow<DatabaseInitializationState> =
        _databaseInitializationStateFlow

    override fun setDatabaseInitialized(isInitialized: Boolean) {
        _databaseInitializationStateFlow.value = DatabaseInitializationState(isInitialized)
    }

    override var isRootDatabaseInitialized = false
    private lateinit var rootDatabase: IRootDatabase

    override fun initDB() {
        val intent = Intent(context, RootDatabase::class.java)
        val service = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                try {
                    rootDatabase = IRootDatabase.Stub.asInterface(service)
                    isRootDatabaseInitialized = true
                    setDatabaseInitialized(true)
                } catch (e: Exception) {
                    Log.e("InitRootDBImpl", "Error connecting to database", e)
                    throw e
                }
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                isRootDatabaseInitialized = false
                setDatabaseInitialized(false)
            }
        }
        RootService.bind(intent, service)
    }

    override fun getRootDatabase(): IRootDatabase {
        check(isRootDatabaseInitialized) { Constants.GMS_DB_CRASH_MSG }
        return rootDatabase
    }
}