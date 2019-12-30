package org.witnessium.core
package node
package service

import java.nio.ByteBuffer
import java.nio.channels.{AsynchronousFileChannel, CompletionHandler}
import java.nio.file.{Path, StandardOpenOption}
import java.time.Instant
import scala.concurrent.duration._
import scala.util.Try
import cats.Monad
import cats.data.{EitherT, OptionT}
import cats.effect.{Async, Concurrent, Resource, Sync, Timer}
import cats.effect.concurrent.Semaphore
import cats.implicits._
import com.twitter.finagle.http.exp.Multipart
import com.twitter.io.Buf
import eu.timepit.refined.refineV
import eu.timepit.refined.numeric.NonNegative
import scodec.bits.ByteVector

import datatype.{UInt256Bytes, UInt256Refine}
import model.{Block, BlockHeader, NetworkId, Signed, TicketData, Transaction}
import model.api.{LicenseInfo, TicketBrief}
import crypto.KeyPair
import crypto.Hash.ops._
import repository.{BlockRepository, TransactionRepository}

object TicketService {

  def findByLicense[F[_]: Monad: BlockRepository: TransactionRepository](
    license: String, offset: Int, limit: Int
  ): EitherT[F, String, LicenseInfo] = for {
    txHashes <- implicitly[TransactionRepository[F]].listByLicense(license, offset, limit)
    tickets <- txHashes.traverse[EitherT[F, String, *], TicketBrief](transactionHashToTicketBrief)
  } yield {

    def sum(tickets: List[TicketBrief]): BigInt = tickets.flatMap(_.amount.toList).map(_.value).sum
    val total = sum(tickets)
    val paid = sum(tickets.filter(_.payedAt.nonEmpty))

    LicenseInfo(
      summary = LicenseInfo.Summary(total, total - paid),
      tickets = tickets.sortBy(-_.occuredAt.fold(0L)(_.getEpochSecond())),
    )
  }

  def transactionHashToTicketBrief[F[_]: Monad: BlockRepository: TransactionRepository](
    txHash: UInt256Bytes
  ): EitherT[F, String, TicketBrief] = for {
    txOption <- implicitly[TransactionRepository[F]].get(txHash)
    tx <- EitherT.fromOption[F](txOption, s"Transacion $txHash not found")
    ticket <- EitherT.fromOption[F](tx.value.ticketData, s"Transacion $txHash has no ticket data")
  } yield TicketBrief(
    tranHash = txHash,
    violation = ticket.violation,
    occuredAt = ticket.occuredAt,
    amount = ticket.amount,
    payedAt = ticket.payedAt,
  )

  def ticketData[F[_]: Concurrent](
    fileUpload        : Option[Multipart.FileUpload],
    owner             : Option[String],
    license           : Option[String],
    car               : Option[String],
    phone             : Option[String],
    violation         : Option[String],
    location          : Option[String],
    datetime          : Option[String],
    amount            : Option[String],
    ticketTxHash      : Option[String],
    payedAt           : Option[String],
    paymentDescription: Option[String],
  ): EitherT[F, String, TicketData] = fileUploadToPhoto[F](fileUpload).flatMap{photo =>
    EitherT.fromEither[F](for {
      instant <- datetime.traverse{ dt =>  Try(Instant.parse(dt)).toEither.left.map(_.getMessage) }
      amountBigIntOp <- amount.traverse{ a => Try(BigInt(a)).toEither.left.map(_.getMessage) }
      amountRefined <- amountBigIntOp.traverse{ abi => refineV[NonNegative](abi) }
      ticketTxHashRefined <- ticketTxHash.traverse{ txHash =>
        ByteVector.fromHexDescriptive(txHash).flatMap(UInt256Refine.from[ByteVector])
      }
      payedAtInstant <- payedAt.traverse{ pa =>  Try(Instant.parse(pa)).toEither.left.map(_.getMessage) }
    } yield TicketData(
      nonce = None,
      photo = photo,
      owner = owner,
      license = license,
      car = car,
      phone = phone,
      violation = violation,
      occuredAt = instant,
      location = location,
      amount = amountRefined,
      ticketTxHash = ticketTxHashRefined,
      payedAt = payedAtInstant,
      paymentDescription = paymentDescription,
    ))
  }

  def fileUploadToPhoto[F[_]: Concurrent](fileUploadOp: Option[Multipart.FileUpload]): EitherT[F, String, Option[TicketData.Photo]] = {
    scribe.info(s"File uploaded: $fileUploadOp")
    for {
      fileUpload <- OptionT.fromOption[EitherT[F, String, *]](fileUploadOp)
      content <- OptionT.liftF(fileUpload match {
        case d: Multipart.OnDiskFileUpload => fileToBytes[F](d.content.toPath)
        case m: Multipart.InMemoryFileUpload => EitherT.pure[F, String](m.content match {
          case Buf.ByteArray.Owned(array, begin, end) => ByteVector.view(array, begin, end - begin)
          case Buf.ByteBuffer.Owned(buffer) => ByteVector.view(buffer)
        })
      })
    } yield {
      val photo = TicketData.Photo(
        filename = fileUpload.fileName,
        contentType = fileUpload.contentType,
        content = content,
      )
      scribe.info(s"photo received: $photo")
      photo
    }
  }.value

  @SuppressWarnings(Array("org.wartremover.warts.Null"))
  def fileToBytes[F[_]: Concurrent](path: Path): EitherT[F, String, ByteVector] = {
    scribe.info(s"Converting file $path to bytes")
    for {
      guard <- EitherT.right[String](Semaphore[F](1))
      bytes <- asyncChannel(path, guard).use(channel => for {
        _ <- if (channel.size.isValidInt) EitherT.pure[F, String](()) else {
          EitherT.leftT[F, Unit](s"Too big file size to load to memory: ${channel.size}")
        }
        buffer <- EitherT.right(Sync[F].delay(ByteBuffer.allocate(channel.size.toInt)))
        _ <- EitherT.right(Async[F].async[Unit]{ cb =>
          channel.read(buffer, 0L, null, new CompletionHandler[Integer, Null]() {
            def completed(result: Integer, attachment: Null): Unit = cb(Right(()))
            def failed(exc: Throwable, attachment: Null): Unit = cb(Left(exc))
          })
        })
      } yield ByteVector.view(buffer.rewind))(Sync[EitherT[F, String, *]])
    } yield bytes
  }

  def asyncChannel[F[_]: Sync](path: Path, guard: Semaphore[F]): Resource[EitherT[F, String, *], AsynchronousFileChannel] = {
    Resource.make(Sync[EitherT[F, String, *]].delay(AsynchronousFileChannel.open(path, StandardOpenOption.READ))){
      channel => EitherT.right[String](guard.withPermit{
        Sync[F].delay(channel.close()).handleErrorWith(_ => Sync[F].unit)
      })
    }
  }

  def submit[F[_]: Timer: Sync: BlockRepository: TransactionRepository](
    ticketData: TicketData,
    networkId: NetworkId,
    localKeyPair: KeyPair
  ): EitherT[F, String, UInt256Bytes] = for {
    bestBlockHeaderOption <- implicitly[BlockRepository[F]].bestHeader
    _ <- EitherT.right(Sync[F].pure(scribe.info(s"Best block header: $bestBlockHeaderOption")))
    bestBlockHeader <- EitherT.fromOption[F](bestBlockHeaderOption, "No best block header")
    number <- EitherT.fromEither[F](refineV[NonNegative](bestBlockHeader.number.value + 1))
    ticketDataWithNonce = ticketData.copy(nonce = Some(number))
    transaction <- makeTransaction[F](ticketDataWithNonce, networkId, localKeyPair)
    txHash = transaction.toHash
    _ <- EitherT.right(Sync[F].pure(scribe.info(s"generating new block with transactions: $txHash")))
    now <- EitherT.right[String](Timer[F].clock.realTime(MILLISECONDS))
    fromAddress = ticketData.license
    _ <- EitherT.right(Sync[F].pure(scribe.info(s"From address: $fromAddress")))

    newBlockHeader = BlockHeader(
      number = number,
      parentHash = bestBlockHeader.toHash,
      stateRoot = bestBlockHeader.stateRoot,
      transactionsRoot = crypto.hash(List(txHash)),
      timestamp = Instant.ofEpochMilli(now),
    )
    newBlockHash = newBlockHeader.toHash
    _ <- EitherT.right(Sync[F].pure(scribe.info(s"next block header: $newBlockHeader")))
    signature <- EitherT.fromEither[F](localKeyPair.sign(newBlockHash.toArray))
    newBlock = Block(
      header = newBlockHeader,
      transactionHashes = Set(txHash),
      votes = Set(signature),
    )

    _ <- implicitly[BlockRepository[F]].put(newBlock)
    _ <- implicitly[TransactionRepository[F]].put(transaction)
  } yield txHash

  def makeTransaction[F[_]: Sync](
    ticketData: TicketData,
    networkId: NetworkId,
    localKeyPair: KeyPair
  ): EitherT[F, String, Transaction.Signed] = {
    val tx = Transaction(
      networkId = networkId,
      inputs = Set.empty,
      outputs = Set.empty,
      ticketData = Some(ticketData),
    )
    EitherT(Sync[F].delay(localKeyPair.sign(tx.toHash.toArray))).map{ Signed(_, tx) }
  }
}
