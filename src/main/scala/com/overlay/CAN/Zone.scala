package com.overlay.CAN

import akka.actor.typed.ActorRef
import com.overlay.Movie
import com.overlay.CAN.Neighbor

final case class Zone(
    intervals: Array[Interval],
    lastSplit: Int,
    movies: IndexedSeq[Movie],
    routingTable: Array[Option[Neighbor]],
    ref: Option[ActorRef[com.overlay.CAN.Node.NodeCommand]]
)
