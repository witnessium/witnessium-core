package org.witnessium.core
package node
package endpoint

import cats.effect.Async
import cats.implicits._
import io.finch._

import model.MyGarageData.Vehicle
import repository.TransactionRepository
import service.MyGarageService

object MyGarageVehicleEndpoint {

  def Index[F[_]:Async:TransactionRepository](implicit
    finch: EndpointModule[F]
  ): Endpoint[F, List[Vehicle]] = {

    import finch._

    get("vehicle") {
      scribe.info(s"Index vehicle request")
      MyGarageService.listVehicle[F].value.map {
        case Right(vehicles) => Ok(vehicles)
        case Left(errorMsg) =>
          scribe.info(s"Index vehicle error response: $errorMsg")
          InternalServerError(new Exception(errorMsg))
      }
    }
  }

  def Get[F[_]:Async:TransactionRepository](implicit
    finch: EndpointModule[F]
  ): Endpoint[F, List[Vehicle]] = {
    import finch._

    get("vehicle" :: path[String].withToString("{vin}")){ (vin: String) =>
      scribe.info(s"Get vehicle request: $vin")
      MyGarageService.getVehicle[F](vin).value.map {
        case Right(Nil) => NotFound(new Exception(s"Not found: $vin"))
        case Right(vehicles) => Ok(vehicles)
        case Left(errorMsg) =>
          scribe.info(s"Get vehicle $vin error response: $errorMsg")
          InternalServerError(new Exception(errorMsg))
      }
    }
  }

}
