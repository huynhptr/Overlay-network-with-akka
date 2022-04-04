package com.overlay

import com.overlay.Movie

final case class Configuration(
    isChord: Boolean,
    numberOfUsers: Int,
    numberOfComputers: Int,
    minRequestsPerMinute: Int,
    maxRequestsPerMinute: Int,
    durationOfSimulator: Int,
    nodeFailureChance: Float,
    timeMarkForPause: Int,
    listOfItems: Seq[Movie],
    readWriteRatio: Double
)
