package users
import java.time.{LocalDate, Period}

import akka.actor.ActorSystem
import play.api.libs.json.{JsPath, Json, JsonValidationError}
import users.AppErrors.AppErrors
import users.Event.{UserCreated, UserDeleted, UserUpdated}

import scala.collection.concurrent.TrieMap
import cats._
import cats.data.EitherT
import cats.effect.{Effect, IO}
import cats.implicits._

case class User(email: String, name: String, birthDate: LocalDate, drivingLicenceDate: Option[LocalDate])

object User {
  implicit val format = Json.format[User]
}

sealed trait Event
object Event {
  case class UserCreated(user: User)             extends Event
  case class UserUpdated(id: String, user: User) extends Event
  case class UserDeleted(id: String)             extends Event
}

object AppErrors {

  type AppErrors = String

  type MyEffectWithError[E, A] = EitherT[IO, E, A]

  type MyEffect[A] = MyEffectWithError[AppErrors, A]
}

trait UserRepository[F[_]] {

  def get(id: String): F[Option[User]]

  def set(id: String, user: User): F[Unit]

  def delete(id: String): F[Unit]

  def list(): F[Seq[User]]

}

trait EventStore[F[_]] {

  def publish(event: Event): F[Unit]

}

class InMemoryUserRepository[F[_]: Applicative] extends UserRepository[F] {
  private val datas                             = TrieMap.empty[String, User]
  override def get(id: String): F[Option[User]] = datas.get(id).pure[F]
  override def set(id: String, user: User): F[Unit] =
    datas.update(id, user).pure[F]
  override def delete(id: String): F[Unit] =
    datas.remove(id).fold(())(_ => ()).pure[F]
  override def list(): F[Seq[User]] = datas.values.toSeq.pure[F]
}

class AkkaEventStore[F[_]: Applicative](implicit system: ActorSystem) extends EventStore[F] {
  override def publish(event: Event): F[Unit] = {
    system.eventStream.publish(event)
    ().pure[F]
  }
}

class UserService[F[_]](userRepository: UserRepository[F], eventStore: EventStore[F])(
    implicit ME: MonadError[F, AppErrors]
) {

  def createUser(user: User): F[User] =
    for {
      _         <- ME.fromEither(validateDrivingLicence(user))
      mayBeUser <- userRepository.get(user.email)
      _         <- mayBeUser.fold(user.pure[F])(_ => ME.raiseError("User already exist"))
      _         <- userRepository.set(user.email, user)
      _         <- eventStore.publish(UserCreated(user))
    } yield user

  def updateUser(id: String, user: User): F[User] =
    for {
      _         <- ME.fromEither(validateDrivingLicence(user))
      mayBeUser <- userRepository.get(user.email)
      _         <- mayBeUser.fold(ME.raiseError[User]("User not exist, can't be updated"))(_.pure[F])
      _         <- userRepository.set(user.email, user)
      _         <- eventStore.publish(UserUpdated(id, user))
    } yield user

  def deleteUser(id: String): F[Unit] =
    userRepository
      .delete(id)
      .flatMap(_ => eventStore.publish(UserDeleted(id)))

  def get(id: String): F[Option[User]] =
    userRepository.get(id)

  def list(): F[Seq[User]] = userRepository.list()

  private def validateDrivingLicence(user: User): Either[AppErrors, User] = {
    val licenceMinimumAge = user.birthDate.plusYears(18)
    (user.drivingLicenceDate, user.birthDate) match {
      case (Some(licenceDate), birthDate) if age(birthDate) >= 18 && licenceDate.isAfter(licenceMinimumAge) =>
        Right(user)
      case (Some(_), _) =>
        Left("Too young to get a licence")
      case (None, _) =>
        Right(user)
    }
  }

  private def age(date: LocalDate): Int =
    Period.between(date, LocalDate.now()).getYears

}
