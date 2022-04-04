package com.overlay.CAN

import akka.actor.typed.ActorRef
import com.overlay.CAN.Node.NodeCommand
import com.overlay.CAN.Interval

case class Neighbor(intervals: Array[com.overlay.CAN.Interval], ref: ActorRef[NodeCommand])