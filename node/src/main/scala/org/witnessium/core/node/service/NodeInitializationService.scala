package org.witnessium.core
package node.service

trait NodeInitializationService[F[_]] {
  def initialize: F[Either[String, Unit]]
}
