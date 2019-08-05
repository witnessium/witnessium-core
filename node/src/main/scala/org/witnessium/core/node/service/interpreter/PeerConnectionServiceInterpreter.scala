package org.witnessium.core
package node
package service
package interpreter

import scala.util.Random
import cats.data.EitherT
import cats.implicits._
import com.twitter.util.Future
import io.catbird.util._
import datatype.UInt256Bytes
import client.GossipClient
import model.{Block, GossipMessage, NodeStatus, State}
import p2p.BloomFilter

class PeerConnectionServiceInterpreter(
  clients: List[GossipClient[Future]]
) extends PeerConnectionService[Future] {

  @SuppressWarnings(Array("org.wartremover.warts.TraversableOps"))
  def bestStateAndBlock(localStatus: NodeStatus): Future[Either[String, Option[(State, Block)]]] = {
    Future.collect(clients.map(_.status)).map { (statusResponses: Seq[Either[String, NodeStatus]]) =>
      for {
        (Right(status), client) <- statusResponses.toList zip clients if status.number.value > localStatus.number.value
      } yield (status, client)
    }.flatMap {
      case Nil => EitherT.pure[Future, String](None).value
      case nonEmptyList =>
        val (bestStatus, client) = nonEmptyList.maxBy(_._1.number.value)
        (for {
          blockOption <- EitherT(client.block(bestStatus.bestHash))
          block <- EitherT.fromOption[Future](blockOption, s"Fail to find block ${bestStatus.bestHash} from $client")
          stateOption <- EitherT(client.state(block.header.stateRoot))
          state <- EitherT.fromOption[Future](stateOption, s"Fail to find state ${block.header.stateRoot} from $client")
        } yield Some((state, block))).value
    }
  }

  def block(blockHash: UInt256Bytes): Future[Option[Block]] = Random.shuffle(clients).traverse(_.block(blockHash)).map(
    _ collectFirst { case Right(Some(block)) => block }
  )

  def gossip(bloomFilter: BloomFilter): Future[Either[String, GossipMessage]] = {
    val clientSelected: GossipClient[Future] = clients.toVector(Random.nextInt(clients.size))
    clientSelected.bloomfilter(bloomFilter)
  }
}
