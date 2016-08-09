package com.tomogle.stocks

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Sink, Source}
import com.typesafe.config.{Config, ConfigFactory}
import spray.json.DefaultJsonProtocol

import scala.concurrent.Future

final case class ApiResponse(ok: Boolean, error: String)

trait ApiResponseJsonProtocolSupport extends SprayJsonSupport with DefaultJsonProtocol {
  implicit val apiResponseFormat = jsonFormat2(ApiResponse)
}

object App extends ApiResponseJsonProtocolSupport {

  def main(args: Array[String]): Unit = {
    val appContext = ApplicationContext()

    implicit val system = ActorSystem()
    implicit val materializer = ActorMaterializer()
    implicit val executionContext = system.dispatcher

    val heartbeatUri = "/ob/api/heartbeat"

    val connectionFlow: Flow[HttpRequest, HttpResponse, Future[Http.OutgoingConnection]] =
      Http().outgoingConnectionHttps(appContext.apiRootUrl)

    val responseFuture: Future[HttpResponse] = Source.single(HttpRequest(uri = heartbeatUri))
      .via(connectionFlow)
      .runWith(Sink.head)

    responseFuture map { (response: HttpResponse) =>
      import akka.http.scaladsl.model.StatusCodes._
      response.status match {
        case OK => Unmarshal(response.entity).to[ApiResponse].foreach { apiResponse =>
          println(s"API Response was: $apiResponse")
        }
        case _ =>  Unmarshal(response.entity).to[String].foreach { body =>
          println(s"Response status was ${response.status} and body was $body")
        }

      }
    }
  }
}

class ApplicationContext(config: Config) {

  def this() = this(ConfigFactory.load())

  import ApplicationContext._

  val apiKey = config.getString(s"$ConfigRoot.$ApiKey")
  val apiRootUrl = config.getString(s"$ConfigRoot.$ApiRootUrl")
}
object ApplicationContext {
  val ConfigRoot = "stocks"
  val ApiKey = "api.key"
  val ApiRootUrl = "api.rooturl"

  def apply(): ApplicationContext = new ApplicationContext()
}
