package org.witnessium.core.node

trait GenesisBlockSetupService[F[_]] {
  def apply(): F[Unit]
}
