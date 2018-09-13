package wiring

import play.api.ApplicationLoader.Context
import play.api._
import com.softwaremill.macwire._
import _root_.controllers.UserController
import router.Routes
import users.{InMemoryUserRepository, UserService}

class AppComponents(context: Context) extends BuiltInComponentsFromContext(context) with NoHttpFiltersComponents {

  private implicit def as = actorSystem

  private lazy val userRepository = wire[InMemoryUserRepository]

  private lazy val userService = wire[UserService]

  // Controllers
  private lazy val userController = wire[UserController]

  // Router
  lazy val router = {
    val routePrefix: String = "/"
    wire[Routes]
  }

}
