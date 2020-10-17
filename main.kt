import com.squareup.moshi.*
import com.squareup.moshi.Types.newParameterizedType
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okio.Okio
import java.nio.file.Paths
import kotlin.math.*

class Mountain(
        @Json(name = "name") val name: String,
        @Json(name = "country") val country: String,
        @Json(name = "mountain_range") val mountainRange: String,
        @Json(name = "latitude") val latitude: Double,
        @Json(name = "longitude") val longitude: Double,
        @Json(name = "elevation") var elevation: Int
)

// Example of using custom adapter in Moshi
//
// val moshi = Moshi.Builder()
//        .add(MountainAdapter())
//        .addLast(KotlinJsonAdapterFactory())
//        .build()
//
//class MountainJson(
//        @Json(name = "name") val name: String,
//        @Json(name = "country") val country: String,
//        @Json(name = "mountain_range") val mountainRange: String,
//        @Json(name = "latitude") val latitude: String,
//        @Json(name = "longitude") val longitude: String,
//        @Json(name = "elevation") var elevation: Int
//)
//
//class MountainAdapter{
//    @FromJson fun fromJson(mountainJson: MountainJson): Mountain{
//        var lat: String = mountainJson.latitude.replace(',', '.').trim()
//        var lon: String = mountainJson.longitude.replace(',', '.').trim()
//
//        if(lat.isEmpty()){
//            lat = "0.0"
//        }
//        if(lon.isEmpty()){
//            lon = "0.0"
//        }
//
//        return Mountain(
//                name = mountainJson.name,
//                country = mountainJson.country,
//                mountainRange = mountainJson.mountainRange,
//                latitude = lat.toDouble(),
//                longitude = lon.toDouble(),
//                elevation = mountainJson.elevation
//        )
//    }
//}

class GpsPoint(
        val lon: Double,
        val lat: Double
)

const val earthRadiusKm: Double = 6371.0;  // earth's mean radius, km

// Haversine - "as the crow flies" algorithm to determine distance between 2 gps points
// https://www.movable-type.co.uk/scripts/latlong.html
fun haversine(currentPoint: GpsPoint, targetPoint: GpsPoint): Double {
    val lat1 = Math.toRadians(currentPoint.lat)
    val lat2 = Math.toRadians(targetPoint.lat)

    val dLat = Math.toRadians(targetPoint.lat - currentPoint.lat)
    val dLon = Math.toRadians(targetPoint.lon - currentPoint.lon)

    val a = sin(dLat/2) * sin(dLat/2) +
            cos(lat1) * cos(lat2) * sin(dLon/2) * sin(dLon/2)
    val c = 2 * kotlin.math.atan2(sqrt(a), sqrt(1-a))

    return round(earthRadiusKm * c * 1000 ) /  1000.0
}

class MountainWithDistance(
        val mountain: Mountain,
        val distance: Double
)

fun main(){
    // read json file
    val moshi = Moshi.Builder()
            .addLast(KotlinJsonAdapterFactory())
            .build()

    // option 1
    val type = newParameterizedType(List::class.java, Mountain::class.java)
    val jsonAdapter: JsonAdapter<List<Mountain>> = moshi.adapter(type)
    val reader = JsonReader.of(Okio.buffer(Okio.source(Paths.get("src", "main", "kotlin", "data", "slovenia_peaks.json").toAbsolutePath().toFile())))
    val mountains: List<Mountain> = jsonAdapter.fromJson(reader)!!
    reader.close()

    // options 2
    //    val jsonAdapter: JsonAdapter<Mountain> = moshi.adapter<Mountain>(Mountain::class.java)
    //    val reader = JsonReader.of(Okio.buffer(Okio.source(Paths.get("src","main", "kotlin", "data", "slovenia_peaks.json").toAbsolutePath().toFile())))
    //    reader.beginArray()
    //    while (reader.hasNext()) {
    //        val entry: Mountain = jsonAdapter.fromJson(reader)!!
    //        println("${entry.name}")
    //    }
    //    reader.endArray()
    //    reader.close()

    //    Output
    //    for(mountain in mountains){
    //        println(mountain.name)
    //    }


    // have 2 random geo points to simulate current position
    val currLat: Double = 46.2194828
    val currLon: Double = 15.2719759
    val currentPoint: GpsPoint = GpsPoint(lat = currLat, lon = currLon)

    // find entries within 10km radius
    val targetRadius: Double = 10.0 // radius of bounding circle in kilometers

    val rad2radiusRatio = targetRadius / earthRadiusKm

    val maxLat = currLat + Math.toDegrees(rad2radiusRatio)
    val minLat = currLat - Math.toDegrees(rad2radiusRatio)
    val maxLon = currLon + Math.toDegrees(asin(rad2radiusRatio) / cos(Math.toRadians(currLat)))
    val minLon = currLon - Math.toDegrees(asin(rad2radiusRatio) / cos(Math.toRadians(currLat)))

    val mountainsWithinRange: List<Mountain> = mountains.filter { mountain ->
        mountain.latitude in minLat..maxLat && mountain.longitude in minLon..maxLon
    }

    //    Output
    //    for(mountain in mountainsWithinRange){
    //        println(mountain.name)
    //    }

    // sort by distance with haversine formula - "as the crow flies"
    val sortedMountainWithDistance = mountainsWithinRange.map { mountain ->
        val distance: Double = haversine(
                currentPoint = currentPoint,
                targetPoint = GpsPoint(lon = mountain.longitude, lat = mountain.latitude)
        )

        MountainWithDistance(mountain,distance)
    }.sortedBy { mountainWithDistance -> mountainWithDistance.distance }

    // output results
    for (sortedMountain in sortedMountainWithDistance) {
        println("${sortedMountain.mountain.name} je oddaljen ${sortedMountain.distance}km")
    }
}
