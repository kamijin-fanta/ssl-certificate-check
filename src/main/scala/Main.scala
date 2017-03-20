import java.net.URL
import java.security.cert.X509Certificate
import javax.net.ssl.HttpsURLConnection

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{RejectionHandler, Route}
import akka.stream.ActorMaterializer
import org.json4s.DefaultFormats
import org.json4s.native.Serialization.write

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Future, blocking}
import scala.io.StdIn
import scala.util.{Failure, Success, Try}

object Main {
  implicit val defaultFormats = DefaultFormats
  def main(args: Array[String]): Unit = {
    implicit val system = ActorSystem("my-system")
    implicit val materializer = ActorMaterializer()
    implicit val executionContext = system.dispatcher


    val errorResponse = (res: HttpResponse, message: String) => {
      val errJson = write(Map('error -> message))
      res.copy(entity = HttpEntity(ContentTypes.`application/json`, errJson))
    }
    implicit def rejectionHandler =
      RejectionHandler.default
        .mapRejectionResponse {
          case res @ HttpResponse(_, _, ent: HttpEntity.Strict, _) =>
            errorResponse(res, ent.data.utf8String)
        }

    val route = Route.seal(
      pathPrefix("api" / "v1") {
        path("certificate" / Remaining) { str =>
          get {
            certificate(str)
          }
        }
      }
    )

    val bindingFuture = Http().bindAndHandle(route, "localhost", 8080)

    println(s"Server online at http://localhost:8080/\nPress RETURN to stop...")
    if (StdIn.readLine() != null)
      bindingFuture
        .flatMap(_.unbind())
        .onComplete(_ => system.terminate())

  }

  def certificate(url: String) = {
    val certificates = CertificateClient.getCertificateAsync(s"https://${url}/")
    println(certificates)
    onSuccess(certificates) {
      case Success(cert) =>
        complete(HttpEntity(ContentTypes.`application/json`, write(cert)))
      case Failure(ex) =>
        val entity = HttpEntity(ContentTypes.`application/json`, write(Map('error -> ex.toString)))
        complete(StatusCodes.BadRequest, entity)
    }
  }
}

case class ServerCertificate(
                              subject: String,
                              issuer: String,
                              notBefore: String,
                              notAfter: String,
                              algorithm: String
                            )

object CertificateClient {
  def getCertificate(urlStr: String): Try[List[ServerCertificate]] = {
    Try {
      val url = new URL(urlStr)
      val connection = url.openConnection().asInstanceOf[HttpsURLConnection]
      connection.setConnectTimeout(3000)
      connection.connect()
      val certificate = connection.getServerCertificates
      connection.disconnect()
      certificate
        .toList
        .collect {
          case cert: X509Certificate => cert
        }
        .map(cert => ServerCertificate(
          issuer = cert.getIssuerDN.toString,
          subject = cert.getSubjectDN.toString,
          notBefore = DateTime(cert.getNotBefore.getTime).toIsoDateTimeString(),
          notAfter = DateTime(cert.getNotAfter.getTime).toIsoDateTimeString(),
          algorithm = cert.getSigAlgName
        ))
    }
  }
  def getCertificateAsync(url: String): Future[Try[List[ServerCertificate]]] = {
    Future {
      blocking(
        getCertificate(url)
      )
    }
  }
}
