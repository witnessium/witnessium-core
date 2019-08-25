package org.witnessium.core
package node.service

import datatype.UInt256Bytes
import model.State

trait StateService[F[_]] {
  def hash(newState: State): UInt256Bytes
  def put(state: State): F[UInt256Bytes]
}
