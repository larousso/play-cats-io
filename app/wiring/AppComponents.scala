package wiring

import play.api.ApplicationLoader.Context
import play.api._
import com.softwaremill.macwire._
import _root_.controllers.UserController
import router.Routes
import users.AppErrors.{MyEffect}
import users.{AkkaEventStore, InMemoryUserRepository, UserService}
import cats._
import cats.implicits._
import cats.effect._
import cats.effect.implicits._

class AppComponents(context: Context) extends BuiltInComponentsFromContext(context) with NoHttpFiltersComponents {

  private implicit val as = actorSystem
  import as.dispatcher

  private lazy val eventStore     = wire[AkkaEventStore[MyEffect]]
  private lazy val userRepository = wire[InMemoryUserRepository[MyEffect]]

  private lazy val userService = wire[UserService[MyEffect]]

  // Controllers
  private lazy val userController = wire[UserController]

  // Router
  lazy val router = {
    val routePrefix: String = "/"
    wire[Routes]
  }

}
