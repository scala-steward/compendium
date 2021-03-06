/*
 * Copyright 2018-2020 47 Degrees, LLC. <http://www.47deg.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package higherkindness.compendium.core

import cats.effect.IO
import higherkindness.compendium.core.refinements.{ProtocolId, ProtocolVersion}
import higherkindness.compendium.models._

class CompendiumServiceStub(protocolOpt: Option[FullProtocol], exists: Boolean)
    extends CompendiumService[IO] {

  override def storeProtocol(
      id: ProtocolId,
      protocol: Protocol,
      idlName: IdlName
  ): IO[ProtocolVersion] =
    IO.pure(protocolOpt.map(_.metadata.version).getOrElse(ProtocolVersion(1)))

  override def retrieveProtocol(
      id: ProtocolId,
      version: Option[ProtocolVersion]
  ): IO[FullProtocol] =
    protocolOpt.fold(IO.raiseError[FullProtocol](ProtocolNotFound("Not found")))(fp => IO.pure(fp))

  override def existsProtocol(protocolId: ProtocolId): IO[Boolean] = IO.pure(exists)

  override def transformProtocol(fullProtocol: FullProtocol, target: IdlName): IO[FullProtocol] =
    protocolOpt.fold(IO.raiseError[FullProtocol](ProtocolNotFound("Not Found")))(fp => IO.pure(fp))
}

object CompendiumServiceStub {
  def apply(protocolOpt: Option[FullProtocol], exists: Boolean): CompendiumServiceStub =
    new CompendiumServiceStub(protocolOpt, exists)
}
