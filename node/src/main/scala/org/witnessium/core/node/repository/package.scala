package org.witnessium.core
package node

import model.Transaction
import store.HashStore

package object repository {
  type TransactionRepository[F[_]] = HashStore[F, Transaction.Verifiable]
}
