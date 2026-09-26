package com.multi0819.meetingbrief

import android.app.Application
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName="meetings") data class Meeting(@PrimaryKey(autoGenerate=true)val id:Long=0,val topic:String,val startedAt:Long=System.currentTimeMillis(),val endedAt:Long?=null,val summaryText:String="",val recommendationsText:String="",val rawTranscript:String="",val correctedTranscript:String="",val audioPath:String="",val transcriptStatus:String="NONE",val transcriptCheckpointFrame:Long=0,val updatedAt:Long=System.currentTimeMillis())
@Entity(tableName="messages",indices=[Index("meetingId")],foreignKeys=[ForeignKey(entity=Meeting::class,parentColumns=["id"],childColumns=["meetingId"],onDelete=ForeignKey.CASCADE)]) data class MeetingMessage(@PrimaryKey(autoGenerate=true)val id:Long=0,val meetingId:Long,val text:String,val createdAt:Long=System.currentTimeMillis())
data class MeetingWithMessages(@Embedded val meeting:Meeting,@Relation(parentColumn="id",entityColumn="meetingId")val messages:List<MeetingMessage>)

@Dao interface MeetingDao {
 @Query("SELECT * FROM meetings ORDER BY updatedAt DESC") fun all():Flow<List<Meeting>>
 @Transaction @Query("SELECT * FROM meetings WHERE id=:id") fun detail(id:Long):Flow<MeetingWithMessages?>
 @Insert suspend fun insert(v:Meeting):Long
 @Insert suspend fun add(v:MeetingMessage):Long
 @Update suspend fun updateMessage(v:MeetingMessage)
 @Delete suspend fun deleteMessage(v:MeetingMessage)
 @Update suspend fun update(v:Meeting)
 @Delete suspend fun delete(v:Meeting)
 @Query("SELECT DISTINCT m.* FROM meetings m LEFT JOIN messages x ON x.meetingId=m.id WHERE m.topic LIKE '%'||:q||'%' OR x.text LIKE '%'||:q||'%' OR m.rawTranscript LIKE '%'||:q||'%' OR m.correctedTranscript LIKE '%'||:q||'%' OR m.summaryText LIKE '%'||:q||'%' OR m.recommendationsText LIKE '%'||:q||'%' OR strftime('%Y-%m-%d',m.startedAt/1000,'unixepoch','localtime') LIKE '%'||:q||'%' ORDER BY m.updatedAt DESC") fun search(q:String):Flow<List<Meeting>>
 @Query("SELECT * FROM meetings WHERE id=:id") suspend fun transcript(id:Long):Meeting?
 @Query("SELECT * FROM meetings WHERE transcriptStatus IN ('READY','TRANSCRIBING','FAILED') AND audioPath != '' ORDER BY updatedAt DESC LIMIT 1") suspend fun unfinishedTranscript():Meeting?
 @Query("UPDATE meetings SET rawTranscript=:raw, correctedTranscript=:corrected, audioPath=:audioPath, transcriptStatus=:status, transcriptCheckpointFrame=:checkpoint, updatedAt=:updatedAt WHERE id=:id") suspend fun updateTranscript(id:Long,raw:String,corrected:String,audioPath:String,status:String,checkpoint:Long,updatedAt:Long)
 @Query("UPDATE meetings SET updatedAt=:at WHERE id=:id") suspend fun touch(id:Long,at:Long)
 @Transaction @Query("SELECT * FROM meetings ORDER BY startedAt") suspend fun snapshot():List<MeetingWithMessages>
 @Query("DELETE FROM meetings") suspend fun clearAll()
}

@Database(entities=[Meeting::class,MeetingMessage::class],version=2,exportSchema=false) abstract class MeetingDb:RoomDatabase(){abstract fun dao():MeetingDao}
val MIGRATION_1_2=object:Migration(1,2){override fun migrate(db:SupportSQLiteDatabase){
 db.execSQL("ALTER TABLE meetings ADD COLUMN rawTranscript TEXT NOT NULL DEFAULT ''")
 db.execSQL("ALTER TABLE meetings ADD COLUMN correctedTranscript TEXT NOT NULL DEFAULT ''")
 db.execSQL("ALTER TABLE meetings ADD COLUMN audioPath TEXT NOT NULL DEFAULT ''")
 db.execSQL("ALTER TABLE meetings ADD COLUMN transcriptStatus TEXT NOT NULL DEFAULT 'NONE'")
 db.execSQL("ALTER TABLE meetings ADD COLUMN transcriptCheckpointFrame INTEGER NOT NULL DEFAULT 0")
}}
class MeetingBriefApp:Application(){lateinit var db:MeetingDb;override fun onCreate(){super.onCreate();db=Room.databaseBuilder(this,MeetingDb::class.java,"meeting-brief.db").addMigrations(MIGRATION_1_2).build()}}
