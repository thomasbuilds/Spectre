package dev.thomasbuilds.spectre.analysis

import dev.thomasbuilds.spectre.model.Constellation
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

object CelestialGeometry {
  private const val A = 6378137.0
  private const val F = 1.0 / 298.257223563
  private const val E2 = 2.0 * F - F * F

  data class SubSatellitePoint(
    val latDeg: Double,
    val lonDeg: Double,
    val slantRangeM: Double,
    val altitudeAboveEarthM: Double
  )

  // Computes a satellite's sub-satellite point (the spot on the ground directly below it):
  // solves the line-of-sight slant range to a sphere at the satellite's orbital radius in
  // WGS84 ECEF, then converts the resulting satellite position back to latitude/longitude.
  fun compute(
    phoneLatDeg: Double,
    phoneLonDeg: Double,
    phoneAltM: Double,
    satElevationDeg: Float,
    satAzimuthDeg: Float,
    satAltitudeAboveEarthM: Double
  ): SubSatellitePoint? {
    if (satElevationDeg <= 0f) return null

    val latRad = Math.toRadians(phoneLatDeg)
    val lonRad = Math.toRadians(phoneLonDeg)
    val elRad = Math.toRadians(satElevationDeg.toDouble())
    val azRad = Math.toRadians(satAzimuthDeg.toDouble())

    val sinLat = sin(latRad)
    val cosLat = cos(latRad)
    val sinLon = sin(lonRad)
    val cosLon = cos(lonRad)
    val sinEl = sin(elRad)
    val cosEl = cos(elRad)
    val sinAz = sin(azRad)
    val cosAz = cos(azRad)

    val nRadius = A / sqrt(1.0 - E2 * sinLat * sinLat)
    val px = (nRadius + phoneAltM) * cosLat * cosLon
    val py = (nRadius + phoneAltM) * cosLat * sinLon
    val pz = (nRadius * (1.0 - E2) + phoneAltM) * sinLat
    val rObs = sqrt(px * px + py * py + pz * pz)
    val rSat = rObs + satAltitudeAboveEarthM

    val disc = rSat * rSat - rObs * rObs * cosEl * cosEl
    if (disc < 0.0) return null
    val slant = -rObs * sinEl + sqrt(disc)
    if (slant <= 0.0) return null

    val east = sinAz * cosEl
    val north = cosAz * cosEl
    val up = sinEl

    val dx = -sinLon * east - sinLat * cosLon * north + cosLat * cosLon * up
    val dy = cosLon * east - sinLat * sinLon * north + cosLat * sinLon * up
    val dz = cosLat * north + sinLat * up

    val sx = px + slant * dx
    val sy = py + slant * dy
    val sz = pz + slant * dz

    val (latDeg, lonDeg) = ecefToGeodeticDeg(sx, sy, sz)
    val satCenterDist = sqrt(sx * sx + sy * sy + sz * sz)
    val altAbove = satCenterDist - rObs

    return SubSatellitePoint(latDeg, lonDeg, slant, altAbove)
  }

  private fun ecefToGeodeticDeg(
    x: Double,
    y: Double,
    z: Double
  ): Pair<Double, Double> {
    val lon = atan2(y, x)
    val p = sqrt(x * x + y * y)
    var lat = atan2(z, p * (1.0 - E2))
    for (i in 0..<4) {
      val sinLat = sin(lat)
      val n = A / sqrt(1.0 - E2 * sinLat * sinLat)
      lat = atan2(z + E2 * n * sinLat, p)
    }
    return Math.toDegrees(lat) to Math.toDegrees(lon)
  }

  fun orbitalAltitudeM(
    constellation: Constellation,
    svid: Int
  ): Double =
    when (constellation) {
      Constellation.GPS -> {
        20_200_000.0
      }

      Constellation.GLONASS -> {
        19_100_000.0
      }

      Constellation.GALILEO -> {
        23_222_000.0
      }

      Constellation.BEIDOU -> {
        when (svid) {
          in 1..10, in 38..46 -> 35_786_000.0
          else -> 21_500_000.0
        }
      }

      Constellation.QZSS -> {
        36_000_000.0
      }

      Constellation.SBAS -> {
        35_786_000.0
      }

      Constellation.IRNSS -> {
        35_786_000.0
      }

      Constellation.UNKNOWN -> {
        20_200_000.0
      }
    }
}
