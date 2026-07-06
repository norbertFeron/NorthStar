package com.example.northstar.data

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * Live lean-angle / g-force / barometric-altitude readings for the ride currently being
 * recorded. Accelerometer only — the phone is rigidly cradle-mounted aligned with the bike's
 * centerline, so a gravity-isolating low-pass filter gives a stable roll estimate without
 * needing gyroscope fusion for drift correction.
 *
 * Not thread-safe beyond what SensorManager itself guarantees (callbacks land on the calling
 * thread's looper) — [leanDeg]/[gForce]/[pressureAltitudeM] are plain `@Volatile` reads, driven
 * from [RideRecorder.add] on the same cadence as GPS fixes.
 */
class MotionSampler(context: Context) : SensorEventListener {
    private val sm = context.applicationContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val pressure = sm.getDefaultSensor(Sensor.TYPE_PRESSURE)

    val hasBarometer: Boolean get() = pressure != null

    @Volatile var leanDeg: Double = 0.0
        private set
    @Volatile var gForce: Double = 1.0
        private set
    /** Uncalibrated (standard-atmosphere) altitude — only useful as a short-term SMOOTHING
     *  signal blended against GPS altitude, not as an absolute value (real sea-level pressure
     *  varies with weather). See [RideRecorder]'s complementary filter. */
    @Volatile var pressureAltitudeM: Double? = null
        private set

    private val gravity = FloatArray(3)
    private var gravityInit = false

    fun start() {
        accelerometer?.let { sm.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
        pressure?.let { sm.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
    }

    fun stop() {
        sm.unregisterListener(this)
        gravityInit = false
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                val alpha = 0.8f
                if (!gravityInit) {
                    event.values.copyInto(gravity)
                    gravityInit = true
                } else {
                    for (i in 0..2) gravity[i] = alpha * gravity[i] + (1 - alpha) * event.values[i]
                }
                // Roll about the bike's forward axis: lateral tilt (X) vs. the vertical/lengthwise
                // plane (Y, Z) of the gravity vector. 0° = upright, +/- = leaned left/right.
                val gx = gravity[0]; val gy = gravity[1]; val gz = gravity[2]
                leanDeg = Math.toDegrees(atan2(gx.toDouble(), sqrt((gy * gy + gz * gz).toDouble())))
                val mag = sqrt(
                    (event.values[0] * event.values[0] +
                     event.values[1] * event.values[1] +
                     event.values[2] * event.values[2]).toDouble()
                )
                gForce = mag / SensorManager.GRAVITY_EARTH
            }
            Sensor.TYPE_PRESSURE -> {
                pressureAltitudeM = SensorManager.getAltitude(
                    SensorManager.PRESSURE_STANDARD_ATMOSPHERE, event.values[0],
                ).toDouble()
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
