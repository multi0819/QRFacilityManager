package com.multi0819.meetingbrief

import android.app.Application
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName="meetings") data class Meeting(@PrimaryKey(autoGenerate=true)val id:Long=0,val topic:String,val startedAt:Long=System.currentTimeMillis(),val endedAt:Long?=null,val summaryText:String="",val recommendationsText:String="",val updatedAt:Long=System.currentTimeMillis())
@Entity(tableName="messages",indices=[Index("meetingId")],foreignKeys=[ForeignKey(entity=Meeting::class,parentColumns=["id"],childColumns=["meetingId"],onDelete=ForeignKey.CASCADE)]) data class MeetingMessage(@PrimaryKey(autoGenerate=true)val id:Long=0,val meetingId:Long,val text:String,val createdAt:Long=System.currentTimeMillis())
data class MeetingWithMessages(@Embedded val meeting:Meeting,@Relation(parentColumn="id",entityColumn="meetingId")val messages:List<MeetingMessage>)

@Dao interface MeetingDao {
 @Query("SELECT * FROM meetings ORDER BY updatedAt DESC") fun all():Flow<List<Meeting>>
 @Transaction @Query("SELECT * FROM meetings WHERE id=:id") fun detail(id:Long):Flow<MeetingWithMessages?>
 @Insert suspend fun insert(v:Meeting):Long
 @Insert suspend fun add(v:MeetingMessage):Long
 @Update suspend fun update(v:Meeting)
 @Delete suspend fun delete(v:Meeting)
 @Query("SELECT DISTINCT m.* FROM meetings m LEFT JOIN messages x ON x.meetingId=m.id WHERE m.topic LIKE '%'||:q||'%' OR x.text LIKE '%'||:q||'%' OR m.summaryText LIKE '%'||:q||'%' OR m.recommendationsText LIKE '%'||:q||'%' OR strftime('%Y-%m-%d',m.startedAt/1000,'unixepoch','localtime') LIKE '%'||:q||'%' ORDER BY m.updatedAt DESC") fun search(q:String):Flow<List<Meeting>>
}

@Database(entities=[Meeting::class,MeetingMessage::class],version=1,exportSchema=false) abstract class MeetingDb:RoomDatabase(){abstract fun dao():MeetingDao}
class MeetingBriefApp:Application(){lateinit var db:MeetingDb;override fun onCreate(){super.onCreate();db=Room.databaseBuilder(this,MeetingDb::class.java,"meeting-brief.db").build()}}
