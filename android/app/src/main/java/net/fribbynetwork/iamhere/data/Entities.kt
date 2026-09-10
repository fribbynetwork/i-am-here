package net.fribbynetwork.iamhere.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Punto di arrivo salvato. Il raggio puo sovrascrivere quello globale. */
@Entity(tableName = "poi")
data class Poi(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val lat: Double,
    val lon: Double,
    /** null = usa il raggio globale delle impostazioni */
    val radius: Int? = null
)

/** Un viaggio. L'id e il timestamp unix (secondi) di inizio condivisione. */
@Entity(tableName = "trip")
data class Trip(
    @PrimaryKey val id: Long,
    val startedAt: Long,
    val endedAt: Long? = null,
    val destName: String? = null,
    val destLat: Double? = null,
    val destLon: Double? = null,
    val radius: Int? = null,
    val endReason: String? = null
)

/**
 * Una rilevazione in attesa di invio o gia inviata.
 * Conserviamo i valori grezzi, non l'URL gia composto: cosi se cambi
 * il template mentre la coda e piena, i punti arretrati partono
 * comunque con il formato nuovo.
 */
@Entity(tableName = "outbox")
data class Sample(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tripId: Long,
    /** "start" | "track" | "end" */
    val event: String,
    /** unix secondi del fix GPS */
    val fixTime: Long,
    val lat: Double,
    val lon: Double,
    val alt: Double? = null,
    val acc: Float? = null,
    val vacc: Float? = null,
    val speed: Float? = null,
    val bearing: Float? = null,
    val provider: String? = null,
    val satTotal: Int? = null,
    val satUsed: Int? = null,
    val battery: Int? = null,
    val charging: Boolean? = null,
    val network: String? = null,
    val pressure: Float? = null,
    val distToDest: Double? = null,
    val destName: String? = null,
    val attempts: Int = 0,
    val lastError: String? = null,
    val sentAt: Long? = null
)
