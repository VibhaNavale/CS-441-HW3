package Service

import textGen.llm_service.{GenerateTextRequest, GenerateTextResponse, ResponseStatus}
import org.slf4j.LoggerFactory
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelRequest
import software.amazon.awssdk.core.SdkBytes
import scala.concurrent.{ExecutionContext, Future}
import com.typesafe.config.Config
import play.api.libs.json.{Json, JsValue}

class TextGenerationService(config: Config)(implicit ec: ExecutionContext) {
  private val logger = LoggerFactory.getLogger(getClass)

  // Initialize Bedrock client
  private val bedrockClient = BedrockRuntimeClient.builder()
    .region(software.amazon.awssdk.regions.Region.of(config.getString("bedrock.region")))
    .build()

  // Read defaults from config
  private val defaultModelId = config.getString("bedrock.model-id")
  private val defaultMaxTokens = config.getInt("bedrock.max-tokens")
  private val defaultTemperature = config.getDouble("bedrock.temperature")

  // Method to generate text using Protobuf request and response
  def generateText(request: GenerateTextRequest): Future[GenerateTextResponse] = Future {
    try {
      // Use defaults if request values are unset
      val modelId = if (request.modelId.isEmpty) defaultModelId else request.modelId
      val maxTokens = if (request.maxLength == 0) defaultMaxTokens else request.maxLength
      val temperature = if (request.temperature == 0.0f) defaultTemperature else request.temperature

      val requestPayload = s"""{
        "inputText": "User: ${request.prompt}\\nBot:",
        "textGenerationConfig": {
          "temperature": $temperature,
          "maxTokenCount": $maxTokens,
          "stopSequences": []
        }
      }"""

      val invokeModelRequest = InvokeModelRequest.builder()
        .modelId(modelId)
        .contentType("application/json")
        .accept("application/json")
        .body(SdkBytes.fromUtf8String(requestPayload))
        .build()

      logger.info(s"Payload Sent: $requestPayload")

      val response = bedrockClient.invokeModel(invokeModelRequest)
      val responseJson = Json.parse(response.body().asUtf8String())

      val generatedText = (responseJson \ "results").asOpt[Seq[JsValue]]
        .flatMap(_.headOption)
        .flatMap(result => (result \ "outputText").asOpt[String])
        .map(_.trim)
        .getOrElse("No generated text found.")

      logger.info(s"Generated Text: $generatedText")

      GenerateTextResponse(
        generatedText = generatedText,
        confidence = 0.7f,
        tokensUsed = (responseJson \ "inputTextTokenCount").asOpt[Int].getOrElse(0),
        modelId = modelId,
        status = Some(ResponseStatus(
          code = ResponseStatus.StatusCode.SUCCESS,
          message = "Text generated successfully."
        ))
      )
    } catch {
      case ex: Exception =>
        logger.error(s"Error generating text: ${ex.getMessage}", ex)
        GenerateTextResponse(
          modelId = request.modelId,
          status = Some(ResponseStatus(
            code = ResponseStatus.StatusCode.ERROR,
            message = ex.getMessage
          ))
        )
    }
  }
}
