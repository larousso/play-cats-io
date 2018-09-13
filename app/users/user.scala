package users
import play.api.libs.json.Json
import users.AppErrors.AppErrors

import scala.collection.concurrent.TrieMap
import scala.concurrent.{ExecutionContext, Future}

case class User(email: String, name: String)

object User {
  implicit val format = Json.format[User]
}

object AppErrors {

  type AppErrors = String
}

trait UserRepository {

  def get(id: String): Future[Option[User]]

  def set(id: String, user: User): Future[Unit]

  def list(): Future[Seq[User]]

}

class InMemoryUserRepository(implicit ec: ExecutionContext) extends UserRepository {
  private val datas                                      = TrieMap.empty[String, User]
  override def get(id: String): Future[Option[User]]     = Future(datas.get(id))
  override def set(id: String, user: User): Future[Unit] = Future(datas.update(id, user))
  override def list(): Future[Seq[User]]                 = Future(datas.values.toSeq)
}

class UserService(userRepository: UserRepository) {

  def createUser(user: User)(implicit ec: ExecutionContext): Future[Either[AppErrors, User]] =
    userRepository.get(user.email).flatMap {
      case Some(_) => concurrent.Future.successful(Left("User already exist"))
      case None    => userRepository.set(user.email, user).map(_ => Right(user))
    }

  def get(id: String): Future[Option[User]] =
    userRepository.get(id)

  def list(): Future[Seq[User]] = userRepository.list()
}
