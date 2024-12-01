package App

import Service.ConversationJsonProtocol.conversationFormat
import textGen.llm_service.GenerateTextRequest
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{ExceptionHandler, Route}
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpResponse, StatusCodes}
import com.typesafe.config.ConfigFactory
import org.slf4j.LoggerFactory
import Service.{OllamaService, TextGenerationService}
import scalapb.json4s.JsonFormat
import spray.json.DefaultJsonProtocol.listFormat

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContextExecutor}
import scala.io.StdIn
import java.util.concurrent.TimeoutException
import spray.json._

object AkkaHttpServer {
  private val logger = LoggerFactory.getLogger(getClass)

  def main(args: Array[String]): Unit = {
    val config = ConfigFactory.load()
    val host = config.getString("server.host")
    val port = config.getInt("server.port")

    implicit val system: ActorSystem = ActorSystem("AkkaHttpServerSystem")
    implicit val executionContext: ExecutionContextExecutor = system.dispatcher

    val textGenerationService = new TextGenerationService(config)
    val ollamaService = new OllamaService(config)

    val route: Route =
      handleExceptions(exceptionHandler) {
        pathPrefix("api") {
          concat(
            path("hello") {
              get {
                complete("Hello from Akka HTTP Server!")
              }
            },
            path("health") {
              get {
                complete("Server is up and running!")
              }
            },
            path("generate-text") {
              post {
                entity(as[String]) { jsonPayload => // Expect JSON as a string
                  try {
                    val request = JsonFormat.fromJsonString[GenerateTextRequest](jsonPayload) // Convert JSON to Protobuf object
                    logger.info(s"Received request with prompt: ${request.prompt}")

                    val responseFuture = textGenerationService.generateText(request)

                    onSuccess(responseFuture) { responseMessage =>
                      complete(HttpResponse(
                        status = StatusCodes.OK,
                        entity = HttpEntity(ContentTypes.`text/plain(UTF-8)`, responseMessage.generatedText)
                      ))
                    }
                  } catch {
                    case ex: Exception =>
                      logger.error("Failed to process the request", ex)
                      complete(HttpResponse(StatusCodes.BadRequest, entity = s"Invalid request: ${ex.getMessage}"))
                  }
                }
              }
            },
            path("generate-ollama") {
              post {
                entity(as[String]) { jsonPayload =>
                  try {
                    val request = JsonFormat.fromJsonString[GenerateTextRequest](jsonPayload)
                    logger.info(s"Received Ollama generation request with prompt: ${request.prompt}")

                    val responseFuture = ollamaService.generateConversationalResponse(request.prompt)
                      .map { responseMessage =>
                        HttpResponse(
                          status = StatusCodes.OK,
                          entity = HttpEntity(ContentTypes.`application/json`, responseMessage.toJson.prettyPrint)
                        )
                      }
                      .recover {
                        case ex: TimeoutException =>
                          logger.error("Request timed out", ex)
                          HttpResponse(
                            status = StatusCodes.GatewayTimeout,
                            entity = s"Request timed out: ${ex.getMessage}"
                          )
                        case ex: Exception =>
                          logger.error("Error processing Ollama generation", ex)
                          HttpResponse(
                            status = StatusCodes.InternalServerError,
                            entity = s"Internal server error: ${ex.getMessage}"
                          )
                      }

                    complete(responseFuture)
                  } catch {
                    case ex: Exception =>
                      logger.error("Failed to parse request", ex)
                      complete(HttpResponse(
                        StatusCodes.BadRequest,
                        entity = s"Invalid request: ${ex.getMessage}"
                      ))
                  }
                }
              }
            }
          )
        }
      }

    val bindingFuture = Http().newServerAt(host, port).bind(route)

    println(s"Server online at http://$host:$port/")

    try {
      // Keep the JVM running
      Await.result(system.whenTerminated, Duration.Inf)
    } finally {
      bindingFuture
        .flatMap(_.unbind())
        .onComplete(_ => system.terminate())
    }
  }

  def exceptionHandler: ExceptionHandler = ExceptionHandler {
    case ex: Exception =>
      extractUri { uri =>
        logger.error(s"Request to $uri failed with exception: ${ex.getMessage}", ex)
        complete(StatusCodes.InternalServerError -> s"Internal server error while processing request to $uri")
      }
  }
}
