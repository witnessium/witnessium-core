package org.witnessium.core
package model

import datatype.UInt256Bytes

final case class State(unused: Set[(Address, UInt256Bytes)], transactions: Set[Transaction])
