package com.example.mawaqiti

import org.json.JSONObject
import java.util.Calendar
import java.util.Locale
import kotlin.math.*

/** Offline fallback calculator: Egyptian General Authority of Survey style angles. */
object PrayerCalculator {
    private const val FAJR_ANGLE = 19.5
    private const val ISHA_ANGLE = 17.5
    private const val SUN_ANGLE = 90.833

    fun calculate(date: Calendar, latitude: Double, longitude: Double): JSONObject {
        val n = dayOfYear(date)
        val decl = 23.45 * sin(Math.toRadians(360.0 * (284 + n) / 365.0))
        val b = Math.toRadians(360.0 * (n - 81) / 364.0)
        val eot = 9.87 * sin(2*b) - 7.53 * cos(b) - 1.5 * sin(b)
        val timezoneHours = date.timeZone.getOffset(date.timeInMillis) / 3_600_000.0
        val noon = 720.0 + timezoneHours * 60.0 - 4.0 * longitude - eot

        fun angleTime(zenith: Double, morning: Boolean): Double {
            val cosH = (cos(Math.toRadians(zenith)) - sin(Math.toRadians(latitude)) * sin(Math.toRadians(decl))) /
                    (cos(Math.toRadians(latitude)) * cos(Math.toRadians(decl)))
            val h = Math.toDegrees(acos(cosH.coerceIn(-1.0, 1.0)))
            return noon + if (morning) -4.0 * h else 4.0 * h
        }

        // Solar noon is used as the local Dhuhr time. Asr = shadow ratio 1.
        val asrAltitude = Math.toDegrees(atan(1.0 / (1.0 + tan(abs(Math.toRadians(latitude - decl))))))
        val cosAsr = (sin(Math.toRadians(asrAltitude)) - sin(Math.toRadians(latitude)) * sin(Math.toRadians(decl))) /
                (cos(Math.toRadians(latitude)) * cos(Math.toRadians(decl)))
        val asrHour = Math.toDegrees(acos(cosAsr.coerceIn(-1.0, 1.0)))
        val asr = noon + 4.0 * asrHour

        fun format(minutes: Double): String {
            var m = round(minutes).toInt()
            m = ((m % 1440) + 1440) % 1440
            return String.format(Locale.US, "%02d:%02d", m / 60, m % 60)
        }

        val sunrise = angleTime(SUN_ANGLE, true)
        val sunset = angleTime(SUN_ANGLE, false)
        val fajr = angleTime(FAJR_ANGLE, true)
        val isha = angleTime(ISHA_ANGLE, false)
        val maghrib = sunset

        return JSONObject().apply {
            put("Fajr", format(fajr))
            put("Sunrise", format(sunrise))
            put("Dhuhr", format(noon))
            put("Asr", format(asr))
            put("Sunset", format(sunset))
            put("Maghrib", format(maghrib))
            put("Isha", format(isha))
        }
    }

    private fun dayOfYear(c: Calendar): Int = c.get(Calendar.DAY_OF_YEAR)
}
