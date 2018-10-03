package controllers

import akka.actor.ActorSystem
import akka.http.scaladsl.util.FastFuture
import play.api.libs.json.{JsError, JsValue, Json}
import play.api.mvc.{AbstractController, Action, AnyContent, ControllerComponents}
import users.{User, UserService}

class UserController(userService: UserService, controllerComponents: ControllerComponents)(
    implicit actorSystem: ActorSystem
) extends AbstractController(controllerComponents) {

  import actorSystem.dispatcher

  def createUser(): Action[JsValue] = Action.async(parse.json) { req =>
    import User._
    req.body
      .validate[User]
      .fold(
        e => FastFuture.successful(BadRequest(JsError.toJson(e))),
        toCreate =>
          userService
            .createUser(toCreate)
            .map {
              case Right(created) => Ok(Json.toJson(created))
              case Left(value)    => BadRequest(Json.obj("error" -> value))
          }
      )
  }

  def updateUser(id: String): Action[JsValue] = Action.async(parse.json) { req =>
    import User._
    req.body
      .validate[User]
      .fold(
        e => FastFuture.successful(BadRequest(JsError.toJson(e))),
        toUpdate =>
          userService
            .updateUser(id, toUpdate)
            .map {
              case Right(created) => Ok(Json.toJson(created))
              case Left(value)    => BadRequest(Json.obj("error" -> value))
          }
      )
  }

  def deleteUser(id: String): Action[AnyContent] = Action.async {
    userService
      .deleteUser(id)
      .map {
        case Right(created) => NoContent
        case Left(value)    => BadRequest(Json.obj("error" -> value))
      }
  }

  def getUser(id: String): Action[AnyContent] = Action.async {
    userService.get(id).map {
      case Some(user) => Ok(Json.toJson(user))
      case None       => NotFound(Json.obj())
    }
  }

  def listUser(): Action[AnyContent] = Action.async {
    userService.list.map { users =>
      Ok(Json.toJson(users))
    }
  }

}
