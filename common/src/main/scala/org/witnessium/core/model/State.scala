package org.witnessium.core
package model

import scala.collection.SortedSet

import UInt256Refine.UInt256Bytes

final case class State(unused: SortedSet[(Address, UInt256Bytes)], transactionMap: Map[UInt256Bytes, Transaction])
