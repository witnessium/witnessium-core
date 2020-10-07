package org.witnessium.core
package node
package endpoint

import cats.effect.Async
import cats.implicits._
import io.finch._

import model.MyGarageData.Part
import repository.TransactionRepository
import service.MyGarageService

object MyGaragePartEndpoint {

  def Index[F[_]:Async:TransactionRepository](implicit
    finch: EndpointModule[F]
  ): Endpoint[F, List[Part]] = {

    import finch._

    get("part") {
      scribe.info(s"Index part request")
      MyGarageService.listPart[F].value.map {
        case Right(parts) => Ok(parts)
        case Left(errorMsg) =>
          scribe.info(s"Index part error response: $errorMsg")
          InternalServerError(new Exception(errorMsg))
      }
    }
  }

  def Get[F[_]:Async:TransactionRepository](implicit
    finch: EndpointModule[F]
  ): Endpoint[F, List[Part]] = {
    import finch._

    get("part" :: path[String].withToString("{part-no}")){ (partNo: String) =>
      scribe.info(s"Get part request: $partNo")
      MyGarageService.getPart[F](partNo).value.map {
        case Right(Nil) => NotFound(new Exception(s"Not found: $partNo"))
        case Right(parts) => Ok(parts)
        case Left(errorMsg) =>
          scribe.info(s"Get part $partNo error response: $errorMsg")
          InternalServerError(new Exception(errorMsg))
      }
    }
  }

}
