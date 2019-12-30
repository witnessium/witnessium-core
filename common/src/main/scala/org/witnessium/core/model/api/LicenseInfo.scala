package org.witnessium.core.model.api

final case class LicenseInfo(
  summary: LicenseInfo.Summary,
  tickets: List[TicketBrief],
)

object LicenseInfo {
  final case class Summary(
    total: BigInt,
    unpaid: BigInt,
  )
}
