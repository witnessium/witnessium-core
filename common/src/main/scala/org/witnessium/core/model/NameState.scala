package org.witnessium.core.model

final case class NameState(
  guardian: Option[Account],
  addresses: MultiSig,
)
