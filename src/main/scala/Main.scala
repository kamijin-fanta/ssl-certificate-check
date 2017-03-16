import java.net.URL
import java.security.cert.X509Certificate
import javax.net.ssl.HttpsURLConnection

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.stream.ActorMaterializer
import org.json4s.DefaultFormats
import org.json4s.native.Serialization.write

import scala.io.StdIn
import scala.util.{Failure, Success, Try}

object Main {
  def main(args: Array[String]): Unit = {
    implicit val system = ActorSystem("my-system")
    implicit val materializer = ActorMaterializer()
    implicit val executionContext = system.dispatcher
    implicit val defaultFormats = DefaultFormats

    val route =
      path("hello" / Remaining) { str =>
        get {
          val certificates = CertificateClient.getCertificate(s"https://${str}/")
          println(certificates)
          certificates match {
            case Success(cert) =>
              val json = write(cert)
              complete(HttpEntity(ContentTypes.`application/json`, json))
            case Failure(ex) =>
              failWith(ex)
          }
        }
      }

    val bindingFuture = Http().bindAndHandle(route, "localhost", 8080)

    println(s"Server online at http://localhost:8080/\nPress RETURN to stop...")
    if (StdIn.readLine() != null)
      bindingFuture
        .flatMap(_.unbind())
        .onComplete(_ => system.terminate())

  }
}

case class Certificate(issuer: String, subject: String)

object CertificateClient {
  def getCertificate(urlStr: String) = {
    Try {
      val url = new URL(urlStr)
      val connection = url.openConnection().asInstanceOf[HttpsURLConnection]
      connection.connect()
      val certificate = connection.getServerCertificates
      connection.disconnect()
      certificate
        .toList
        .collect {
          case cert: X509Certificate => cert
        }
        .map(cert => Certificate(cert.getIssuerDN.toString, cert.getSubjectDN.toString))
    }
  }
}
