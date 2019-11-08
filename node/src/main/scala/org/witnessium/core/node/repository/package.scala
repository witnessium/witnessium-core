package org.witnessium.core
package node

import model.Transaction

package object repository {
  type TransactionRepository[F[_]] = HashStore[F, Transaction.Verifiable]
}
