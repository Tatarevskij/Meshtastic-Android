package com.geeksville.mta.database

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.geeksville.mta.MyNodeInfo
import com.geeksville.mta.NodeInfo
import com.geeksville.mta.database.dao.PacketDao
import com.geeksville.mta.database.dao.MeshLogDao
import com.geeksville.mta.database.dao.MyNodeInfoDao
import com.geeksville.mta.database.dao.NodeInfoDao
import com.geeksville.mta.database.dao.QuickChatActionDao
import com.geeksville.mta.database.entity.MeshLog
import com.geeksville.mta.database.entity.Packet
import com.geeksville.mta.database.entity.QuickChatAction

@Database(
    entities = [
        MyNodeInfo::class,
        NodeInfo::class,
        Packet::class,
        MeshLog::class,
        QuickChatAction::class
    ],
    autoMigrations = [
        AutoMigration (from = 3, to = 4),
    ],
    version = 4,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class MeshtasticDatabase : RoomDatabase() {
    abstract fun myNodeInfoDao(): MyNodeInfoDao
    abstract fun nodeInfoDao(): NodeInfoDao
    abstract fun packetDao(): PacketDao
    abstract fun meshLogDao(): MeshLogDao
    abstract fun quickChatActionDao(): QuickChatActionDao

    companion object {
        fun getDatabase(context: Context): MeshtasticDatabase {

            return Room.databaseBuilder(
                context.applicationContext,
                MeshtasticDatabase::class.java,
                "meshtastic_database"
            )
                .fallbackToDestructiveMigration()
                .build()
        }
    }
}
