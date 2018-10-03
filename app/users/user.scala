package users
import java.time.{LocalDate, Period}

import akka.actor.ActorSystem
import play.api.libs.json.Json
import users.AppErrors.AppErrors
import users.Event.{UserCreated, UserDeleted, UserUpdated}

import scala.collection.concurrent.TrieMap
import scala.concurrent.{ExecutionContext, Future}

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
}

trait UserRepository {

  def get(id: String): Future[Option[User]]

  def set(id: String, user: User): Future[Unit]

  def delete(id: String): Future[Unit]

  def list(): Future[Seq[User]]

}

trait EventStore {

  def publish(event: Event): Future[Unit]

}

class InMemoryUserRepository(implicit ec: ExecutionContext) extends UserRepository {
  private val datas                                      = TrieMap.empty[String, User]
  override def get(id: String): Future[Option[User]]     = Future(datas.get(id))
  override def set(id: String, user: User): Future[Unit] = Future(datas.update(id, user))
  override def delete(id: String): Future[Unit]          = Future(datas.remove(id).fold(())(_ => ()))
  override def list(): Future[Seq[User]]                 = Future(datas.values.toSeq)
}

class AkkaEventStore(implicit system: ActorSystem) extends EventStore {
  override def publish(event: Event): Future[Unit] = {
    system.eventStream.publish(event)
    Future.successful(())
  }
}

class UserService(userRepository: UserRepository, eventStore: EventStore) {

  def createUser(user: User)(implicit ec: ExecutionContext): Future[Either[AppErrors, User]] =
    validateDrivingLicence(user) match {
      case Right(_) =>
        userRepository.get(user.email).flatMap {
          case Some(_) => Future.successful(Left("User already exist"))
          case None =>
            userRepository
              .set(user.email, user)
              .flatMap(_ => eventStore.publish(UserCreated(user)))
              .map(_ => Right(user))
        }
      case e =>
        Future.successful(e)
    }

  def updateUser(id: String, user: User)(implicit ec: ExecutionContext): Future[Either[AppErrors, User]] =
    validateDrivingLicence(user) match {
      case Right(_) =>
        userRepository.get(user.email).flatMap {
          case Some(u) =>
            userRepository
              .set(user.email, user)
              .flatMap(_ => eventStore.publish(UserUpdated(id, user)))
              .map(_ => Right(user))
          case None => Future.successful(Left("User already not exist, can't be updated"))
        }
      case e =>
        Future.successful(e)
    }

  def deleteUser(id: String)(implicit ec: ExecutionContext): Future[Either[AppErrors, Unit]] =
    userRepository.delete(id).flatMap(_ => eventStore.publish(UserDeleted(id))).map(_ => Right(()))

  def get(id: String): Future[Option[User]] =
    userRepository.get(id)

  def list(): Future[Seq[User]] = userRepository.list()

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
    Period.between(date, LocalDate.now()).getYears()

}
