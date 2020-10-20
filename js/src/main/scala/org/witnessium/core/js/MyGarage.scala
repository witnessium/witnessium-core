package org.witnessium.core
package js

import java.time.Instant
import scalajs.js.Dictionary
import scalajs.js.JSConverters._
import scalajs.js.annotation.{JSExport, JSExportTopLevel}
import scalajs.js.typedarray.Uint8Array
import eu.timepit.refined.refineMV
import eu.timepit.refined.numeric.NonNegative
import codec.byte.ByteEncoder
import model.{MyGarageData, Transaction}

@JSExportTopLevel("MyGarage")
object MyGarage {

  val networkId: datatype.BigNat = refineMV[NonNegative](BigInt(202))

  @SuppressWarnings(Array("org.wartremover.warts.Null"))
  @JSExport
  def vehicle(v: Dictionary[String]): Uint8Array = (for {
    vin <- v.get("vin")
    carNo <- v.get("carNo")
    manufacturer <- v.get("manufacturer")
    model <- v.get("model")
    owner <- v.get("owner")
    v1 = MyGarageData.Vehicle(vin, carNo, manufacturer, model, owner)
    tx = Transaction(networkId, Set.empty, Set.empty, Some(v1))
    bytes = ByteEncoder[Transaction].encode(tx)
  } yield new Uint8Array(bytes.toArray.toJSArray)).getOrElse(null)

  @SuppressWarnings(Array("org.wartremover.warts.Null"))
  @JSExport
  def part(v: Dictionary[String]): Uint8Array = (for {
    name <- v.get("name")
    partNo <- v.get("partNo")
    manufacturer <- v.get("manufacturer")
    date <- v.get("date").map(Instant.parse)
    warrenty <- v.get("warrenty")
    supplier <- v.get("supplier")
    importer <- v.get("importer")
    seller <- v.get("seller")
    holder <- v.get("holder")
    updatedAt <- v.get("updatedAt").map(Instant.parse)
    p1 = MyGarageData.Part(name, partNo, manufacturer, date, warrenty, supplier, importer, seller, holder, updatedAt)
    tx = Transaction(networkId, Set.empty, Set.empty, Some(p1))
    bytes = ByteEncoder[Transaction].encode(tx)
  } yield new Uint8Array(bytes.toArray.toJSArray)).getOrElse(null)
}
