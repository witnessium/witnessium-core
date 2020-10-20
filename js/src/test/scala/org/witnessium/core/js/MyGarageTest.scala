package org.witnessium.core
package js

import scalajs.js.Dictionary

import utest._

object MyGarageTest extends TestSuite{

  val tests = Tests {

    test("vehicle"){
      val v = Dictionary(
        "vin" -> "KMHDL41BP8A000001",
        "carNo" -> "23사5678",
        "manufacturer" -> "Hyundai",
        "model" -> "Sonata",
        "owner" -> "Alice",
      )

      val result = MyGarage.vehicle(v).toList

      val expected: List[Short] = List(129, 202, 0, 0, 1, 0, 17, 75, 77, 72, 68, 76, 52, 49, 66, 80, 56, 65, 48, 48, 48, 48, 48, 49, 9, 50, 51, 236, 130, 172, 53, 54, 55, 56, 7, 72, 121, 117, 110, 100, 97, 105, 6, 83, 111, 110, 97, 116, 97, 5, 65, 108, 105, 99, 101)

      assert(expected == result)
    }

    test("part"){
      val p = Dictionary(
        "name" -> "패드-글로브 박스 스톱퍼",
        "partNo" -> "8451922000",
        "manufacturer" -> "Hyundai",
        "date" -> "2007-12-03T10:15:30.00Z",
        "warrenty" -> "1 year",
        "supplier" -> "Hyundai",
        "importer" -> "Hyunddi",
        "seller" -> "Hyundai",
        "holder" -> "Hyundai",
        "updatedAt" -> "2007-12-03T10:15:30.00Z",
      )

      val result = MyGarage.part(p).toList

      val expected: List[Short] = List(129, 202, 0, 0, 1, 1, 33, 237, 140, 168, 235, 147, 156, 45, 234, 184, 128, 235, 161, 156, 235, 184, 140, 32, 235, 176, 149, 236, 138, 164, 32, 236, 138, 164, 237, 134, 177, 237, 141, 188, 10, 56, 52, 53, 49, 57, 50, 50, 48, 48, 48, 7, 72, 121, 117, 110, 100, 97, 105, 0, 0, 1, 22, 159, 128, 217, 208, 6, 49, 32, 121, 101, 97, 114, 7, 72, 121, 117, 110, 100, 97, 105, 7, 72, 121, 117, 110, 100, 100, 105, 7, 72, 121, 117, 110, 100, 97, 105, 7, 72, 121, 117, 110, 100, 97, 105, 0, 0, 1, 22, 159, 128, 217, 208)

      assert(expected == result)
    }
  }
}
