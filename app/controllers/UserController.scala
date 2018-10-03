package controllers

import cats.data.EitherT
import cats.effect.IO
import play.api.libs.json._
import play.api.mvc._
import users.AppErrors.{MyEffect, MyEffectWithError}
import users.{User, UserService}

class UserController(
    userService: UserService[MyEffect],
    controllerComponents: ControllerComponents
) extends AbstractController(controllerComponents) {

  private def jsResultToEff[A](
      jsResult: JsResult[A]
  ): MyEffectWithError[Result, A] =
    EitherT
      .fromEither[IO](jsResult.asEither)
      .leftMap(e => BadRequest(JsError.toJson(e)))

  import libs.http._

  def createUser(): Action[JsValue] = Action.asyncEitherT(parse.json) { req =>
    import User._

    for {
      toCreate <- jsResultToEff(req.body.validate[User])
      created <- userService
                  .createUser(toCreate)
                  .leftMap(e => BadRequest(Json.obj("error" -> e)))
    } yield Ok(Json.toJson(created))
  }

  def updateUser(id: String): Action[JsValue] =
    Action.asyncEitherT(parse.json) { req =>
      import User._
      for {
        toUpdate <- jsResultToEff(req.body.validate[User])
        updated <- userService
                    .updateUser(id, toUpdate)
                    .leftMap(e => BadRequest(Json.obj("error" -> e)))
      } yield Ok(Json.toJson(updated))
    }

  def deleteUser(id: String): Action[AnyContent] = Action.asyncEitherT { _ =>
    userService
      .deleteUser(id)
      .map { _ =>
        NoContent
      }
      .leftMap(e => BadRequest(Json.obj("error" -> e)))
  }

  def getUser(id: String): Action[AnyContent] = Action.asyncEitherT { _ =>
    import User._
    userService
      .get(id)
      .map {
        case Some(user) => Ok(Json.toJson(user))
        case None       => NotFound(Json.obj())
      }
      .leftMap(e => BadRequest(Json.obj("error" -> e)))
  }

  def listUser(): Action[AnyContent] = Action.asyncEitherT { _ =>
    userService
      .list()
      .map { users =>
        Ok(Json.toJson(users))
      }
      .leftMap(e => BadRequest(Json.obj("error" -> e)))

  }

}
