package xebia.consul.example

import java.net.URL
import java.util.concurrent.TimeUnit

import akka.actor.{ActorRef, ActorSystem}
import akka.io.IO
import akka.pattern._
import akka.util.Timeout
import spray.can.Http
import xebia.consul.client._
import xebia.consul.client.loadbalancers.RoundRobinLoadBalancer

import scala.concurrent.Future
import scala.concurrent.duration._

object Boot extends App {
  implicit val system = ActorSystem("reactive-consul")
  implicit val executionContext = system.dispatcher

  val service = system.actorOf(ReactiveConsulHttpServiceActor.props(), "webservice")

  implicit val timeout = Timeout(5.seconds)

  IO(Http) ? Http.Bind(service, interface = "0.0.0.0", port = 8080)

  def cpf(k: String) = (host: String, port: Int) => new ConnectionProvider {
    val client = new SprayExampleServiceClient(new URL(s"http://$host:$port"))
    override def getConnection: Future[Any] = Future.successful(client)
  }
  val connectionStrategy1 = ConnectionStrategy("example-service-1", cpf("example-service-1"))
  val connectionStrategy2 = ConnectionStrategy("example-service-2", cpf("example-service-2"))
  val services = Set(connectionStrategy1, connectionStrategy2)
  val serviceBroker = ServiceBroker("consul-8500.service.consul", services)
  system.scheduler.schedule(FiniteDuration(5, TimeUnit.SECONDS), FiniteDuration(5, TimeUnit.SECONDS)) {
    serviceBroker.withService("example-service-1") { client: SprayExampleServiceClient =>
      client.identify
    }.foreach(println)
    serviceBroker.withService("example-service-2") { client: SprayExampleServiceClient =>
      client.identify
    }.foreach(println)
  }
}
