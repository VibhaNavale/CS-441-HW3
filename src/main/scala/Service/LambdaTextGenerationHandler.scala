package Service

import textGen.llm_service.{GenerateTextRequest, GenerateTextResponse, ResponseStatus}
import com.amazonaws.services.lambda.runtime.{Context, RequestHandler}
import com.amazonaws.services.lambda.runtime.events.{APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent}
import com.typesafe.config.ConfigFactory
import org.slf4j.LoggerFactory
import play.api.libs.json.Json
import scala.concurrent.{Await, ExecutionContext}
import scala.concurrent.duration.Duration
import scala.jdk.CollectionConverters._
import java.util.Base64

class LambdaTextGenerationHandler() extends RequestHandler[APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent] {
  private val logger = LoggerFactory.getLogger(getClass)

  private val textGenerationService = new TextGenerationService(ConfigFactory.load())(ExecutionContext.global)

  override def handleRequest(input: APIGatewayProxyRequestEvent, context: Context): APIGatewayProxyResponseEvent = {
    try {
      // Check if the body is Base64 encoded and decode it if necessary
      val body = if (input.getIsBase64Encoded) {
        new String(Base64.getDecoder.decode(input.getBody))
      } else {
        input.getBody
      }

      logger.info(s"Decoded API Gateway request body: $body")

      if (body == null || body.isEmpty) {
        throw new IllegalArgumentException("Request body is empty or null")
      }

      val parsedJson = Json.parse(body)
      logger.info(s"Parsed JSON: $parsedJson")

      // Extract the "prompt" field
      val prompt = (parsedJson \ "prompt").asOpt[String].getOrElse(
        throw new IllegalArgumentException("Missing or invalid 'prompt' field in request body")
      )

      val request = GenerateTextRequest(prompt = prompt)

      logger.info(s"Received Lambda request with prompt: $prompt")

      // Generate response using the existing text generation service
      val responseFuture = textGenerationService.generateText(request)
      val response = Await.result(responseFuture, Duration.Inf)

      // Extract only the generated text
      val generatedText = response.generatedText

      // Create API Gateway response
      new APIGatewayProxyResponseEvent()
        .withStatusCode(200)
        .withHeaders(Map(
          "Content-Type" -> "text/plain",
          "Access-Control-Allow-Origin" -> "*"
        ).asJava)
        .withBody(generatedText)

    } catch {
      case ex: Exception =>
        val errorMessage = Option(ex.getMessage).getOrElse("An unknown error occurred")
        logger.error(s"Lambda processing error: $errorMessage", ex)

        val errorResponse = scalapb.json4s.JsonFormat.toJsonString(
          GenerateTextResponse(
            status = Some(ResponseStatus(
              code = ResponseStatus.StatusCode.ERROR,
              message = errorMessage
            ))
          )
        )

        new APIGatewayProxyResponseEvent()
          .withStatusCode(500)
          .withHeaders(Map(
            "Content-Type" -> "application/json",
            "Access-Control-Allow-Origin" -> "*"
          ).asJava)
          .withBody(errorResponse)
    }
  }
}
