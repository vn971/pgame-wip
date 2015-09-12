package net.pointsgame.domain

import scalaz.concurrent.Task
import net.pointsgame.domain.api._
import net.pointsgame.domain.helpers.Tokenizer
import net.pointsgame.domain.managers.ConnectionManager

final class Oracle(services: Services, connectionManager: ConnectionManager) {
  val connectionId = Tokenizer.generate(Constants.connectionIdLength)
  private def withUserId(userId: Option[Int])(f: Int => Task[Answer]): Task[Answer] =
    userId.map(f).getOrElse(Task.fail(new DomainException("You have to be authorized for this action.")))
  def setCallback(callback: Delivery => Unit): Unit = {
    connectionManager.putCallback(connectionId, callback)
    callback(ConnectedDelivery(connectionId))
  }
  def close(): Unit =
    connectionManager.remove(connectionId)
  def stop(): Unit = //TODO: Stop tcp connection here.
    close()
  def answer(question: Question): Task[Answer] = {
    for {
      userIdOption <- question.token.map(services.tokenService.withToken(_) { token =>
        connectionManager.putId(connectionId, token.userId)
        Task.now(Some(token.userId))
      }).getOrElse(Task.now(connectionManager.getId(connectionId)))
      answer <- question match {
        case RegisterQuestion(qId, token, name, password) =>
          services.accountService.register(name, password) map { RegisterAnswer(qId, _: Int, _: String) }.tupled
        case LoginQuestion(qId, token, name, password) =>
          services.accountService.login(name, password) map { LoginAnswer(qId, _: Int, _: String) }.tupled
        case SendRoomMessageQuestion(qId, token, roomId, body) =>
          withUserId(userIdOption) { userId =>
            services.roomMessageService.send(userId, roomId, body) map { SendRoomMessageAnswer(qId, _) }
          }
      }
    } yield answer
  } handle {
    case e: DomainException =>
      ErrorAnswer(question.qId, e.getMessage)
  }
}
