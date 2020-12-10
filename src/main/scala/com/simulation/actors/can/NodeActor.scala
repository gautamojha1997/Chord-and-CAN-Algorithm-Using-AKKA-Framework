package com.simulation.actors.can

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.cluster.sharding.ShardRegion.{ExtractEntityId, ExtractShardId}
import akka.cluster.sharding.{ClusterSharding, ClusterShardingSettings}
import com.simulation.actors.can.NodeActor.{addNeighbour, fetchDHT, getNeighbours, loadDataNode, removeNeighbour, updateCoordinatesNode, updatePredecessor}
import com.simulation.beans.{Coordinates, EntityDefinition}
import com.typesafe.config.ConfigFactory

import scala.collection.mutable

class NodeActor extends Actor{

  var dht: mutable.Map[Int, String] = mutable.HashMap[Int, String]()
  val neighbours: mutable.Map[Int, Coordinates] = mutable.HashMap[Int, Coordinates]()
  var predecessor :Int = -1

  override def receive: Receive = {
    case fetchDHT(id: Int) =>
      if (dht.keySet.contains(id)) {
        sender() ! dht(id)
      }
      sender() ! ""

    case loadDataNode(data: EntityDefinition) => {
      dht(data.id) = data.name
    }

    case addNeighbour(coordinates: Coordinates) => {
      neighbours(coordinates.nodeIndex) = coordinates
    }

    case updatePredecessor(server: Int) =>{
      predecessor = server
    }

    case getNeighbours() => {
      sender() ! neighbours
    }

    case removeNeighbour(server: Int) =>{
      neighbours.remove(server)
    }

    case updateCoordinatesNode(coordinates: Coordinates) =>
      if(neighbours.contains(coordinates.nodeIndex)){
        neighbours(coordinates.nodeIndex) = coordinates
      }

  }
}

object NodeActor{

  private val conf = ConfigFactory.load("application.conf")
  def props(): Props = Props(new NodeActor())
  sealed trait Command
  case class fetchDHT(id: Int)
  case class loadDataNode(data: EntityDefinition)
  case class addNeighbour(coordinates: Coordinates)
  case class updatePredecessor(server: Int)
  case class getNeighbours()
  case class removeNeighbour(server: Int)
  case class updateCoordinatesNode(coordinates: Coordinates)

  case class Envelope(nodeIndex : Int, command: Command) extends Serializable

  val entityIdExtractor: ExtractEntityId ={
    case Envelope(nodeIndex, command) => (nodeIndex.toString,command)
  }

  val num_of_shards = conf.getInt("num_shards")

  val shardIdExtractor: ExtractShardId ={
    case Envelope(nodeIndex, _) => Math.abs(nodeIndex.toString.hashCode % num_of_shards).toString
  }

  //private val id = context.self.path.name


  def startMerchantSharding(system: ActorSystem): ActorRef = {
    ClusterSharding(system).start(
      typeName = "Server",
      entityProps = NodeActor.props(),
      settings = ClusterShardingSettings(system),
      extractEntityId = entityIdExtractor,
      extractShardId = shardIdExtractor
    )
  }
}
