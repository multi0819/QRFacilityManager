package com.multi0819.qrfacility

import android.app.Application
import androidx.room.*
import kotlinx.coroutines.flow.Flow
import java.util.UUID

@Entity(tableName="equipment") data class Equipment(@PrimaryKey val id:String=UUID.randomUUID().toString(), val name:String, val code:String="", val type:String="", val building:String="", val floor:String="", val room:String="", val maker:String="", val model:String="", val serial:String="", val installed:String="", val spec:String="", val memo:String="", val createdAt:Long=System.currentTimeMillis())
@Entity(tableName="check_items", foreignKeys=[ForeignKey(entity=Equipment::class,parentColumns=["id"],childColumns=["equipmentId"],onDelete=ForeignKey.CASCADE)], indices=[Index("equipmentId")]) data class CheckItem(@PrimaryKey(autoGenerate=true) val id:Long=0,val equipmentId:String,val label:String,val inputType:String="OK_NG",val min:Double?=null,val max:Double?=null,val options:String="",val sort:Int=0)
@Entity(tableName="inspections", indices=[Index("equipmentId")]) data class Inspection(@PrimaryKey(autoGenerate=true) val id:Long=0,val equipmentId:String,val itemId:Long,val value:String,val status:String,val memo:String="",val at:Long=System.currentTimeMillis())
@Entity(tableName="maintenance", indices=[Index("equipmentId")]) data class Maintenance(@PrimaryKey(autoGenerate=true) val id:Long=0,val equipmentId:String,val work:String,val worker:String="",val beforePhoto:String="",val afterPhoto:String="",val memo:String="",val at:Long=System.currentTimeMillis())

@Dao interface FacilityDao {
 @Query("SELECT * FROM equipment ORDER BY building,floor,room,name") fun equipment():Flow<List<Equipment>>
 @Query("SELECT * FROM equipment WHERE id=:id LIMIT 1") suspend fun equipment(id:String):Equipment?
 @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun saveEquipment(v:Equipment)
 @Delete suspend fun deleteEquipment(v:Equipment)
 @Query("SELECT * FROM check_items WHERE equipmentId=:id ORDER BY sort,id") fun checkItems(id:String):Flow<List<CheckItem>>
 @Insert suspend fun addCheckItem(v:CheckItem):Long
 @Delete suspend fun deleteCheckItem(v:CheckItem)
 @Query("SELECT * FROM inspections WHERE equipmentId=:id ORDER BY at DESC") fun inspections(id:String):Flow<List<Inspection>>
 @Insert suspend fun addInspection(v:Inspection)
 @Query("SELECT * FROM maintenance WHERE equipmentId=:id ORDER BY at DESC") fun maintenance(id:String):Flow<List<Maintenance>>
 @Insert suspend fun addMaintenance(v:Maintenance)
}

@Database(entities=[Equipment::class,CheckItem::class,Inspection::class,Maintenance::class],version=1,exportSchema=false)
abstract class FacilityDb:RoomDatabase(){ abstract fun dao():FacilityDao }

class FacilityApp:Application(){ lateinit var db:FacilityDb; override fun onCreate(){super.onCreate(); db=Room.databaseBuilder(this,FacilityDb::class.java,"facility.db").build()} }

enum class ResultStatus { NORMAL, ABNORMAL, UNCHECKED }
object InspectionEvaluator { fun evaluate(value:Double?, min:Double?, max:Double?):ResultStatus { if(value==null)return ResultStatus.UNCHECKED; if(min!=null&&value<min)return ResultStatus.ABNORMAL; if(max!=null&&value>max)return ResultStatus.ABNORMAL; return ResultStatus.NORMAL } }
object QrCodec { private const val P="facilityqr://equipment/"; fun encode(id:String)=P+id; fun parse(raw:String):String? { if(!raw.startsWith(P))return null; return raw.removePrefix(P).takeIf{runCatching{UUID.fromString(it)}.isSuccess} } }
