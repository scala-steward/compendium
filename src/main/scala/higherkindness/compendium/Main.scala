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

package higherkindness.compendium

import cats.effect._
import cats.implicits._
import doobie.hikari.HikariTransactor
import doobie.util.ExecutionContexts
import doobie.util.transactor.Transactor
import fs2.Stream
import higherkindness.compendium.core._
import higherkindness.compendium.http._
import higherkindness.compendium.metadata._
import higherkindness.compendium.metadata.pg.PgMetadataStorage
import higherkindness.compendium.migrations.Migrations
import higherkindness.compendium.models.config._
import higherkindness.compendium.storage._
import higherkindness.compendium.storage.files.FileStorage
import higherkindness.compendium.storage.pg.PgStorage
import higherkindness.compendium.transformer.ProtocolTransformer
import higherkindness.compendium.transformer.skeuomorph.SkeuomorphProtocolTransformer
import org.http4s.server.Router
import pureconfig._
import pureconfig.generic.auto._
import pureconfig.module.catseffect.syntax._

object Main extends IOApp {
  override def run(args: List[String]): IO[ExitCode] =
    CompendiumStreamApp.stream[IO].compile.drain.as(ExitCode.Success)
}

object CompendiumStreamApp {

  def stream[F[_]: ConcurrentEffect: Timer: ContextShift]: Stream[F, ExitCode] =
    for {
      conf                           <- Stream.eval(ConfigSource.default.at("compendium").loadF[F, CompendiumServerConfig])
      migrations                     <- Stream.eval(Migrations.metadataLocation)
      _                              <- Stream.eval(Migrations.makeMigrations(conf.metadata.storage, List(migrations)))
      implicit0(storage: Storage[F]) <- Stream.resource(initStorage[F](conf.protocols.storage))
      metadataTransactor             <- Stream.resource(createTransactor(conf.metadata.storage))
      implicit0(metadataStore: MetadataStorage[F])   = PgMetadataStorage[F](metadataTransactor)
      implicit0(utils: ProtocolUtils[F])             = ProtocolUtils.impl[F]
      implicit0(transformer: ProtocolTransformer[F]) = SkeuomorphProtocolTransformer[F]
      implicit0(compendium: CompendiumService[F])    = CompendiumService.impl[F]
      rootService                                    = RootService.rootRouteService
      healthService                                  = HealthService.healthRouteService
      app                                            = Router("/" -> healthService, "/v0" -> rootService)
      code <- CompendiumServerStream.serverStream(conf.http, app)
    } yield code

  private def initStorage[F[_]: Async: ContextShift](
      storageConfig: StorageConfig
  ): Resource[F, Storage[F]] =
    storageConfig match {
      case fsc: FileStorageConfig      => Resource.pure[F, Storage[F]](FileStorage[F](fsc))
      case dbsc: DatabaseStorageConfig => createTransactor(dbsc).map(PgStorage[F])
    }

  private def createTransactor[F[_]: Async: ContextShift](
      conf: DatabaseStorageConfig
  ): Resource[F, Transactor[F]] =
    for {
      ce      <- ExecutionContexts.fixedThreadPool[F](10)
      blocker <- Blocker[F]
      xa <- HikariTransactor
        .fromHikariConfig[F](DatabaseStorageConfig.getHikariConfig(conf), ce, blocker)
    } yield xa
}