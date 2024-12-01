package Service

import org.scalatest.funsuite.AsyncFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.mockito.MockitoSugar
import org.mockito.Mockito._
import org.mockito.ArgumentMatchers._
import textGen.llm_service.{GenerateTextRequest, ResponseStatus}
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelRequest
import software.amazon.awssdk.core.SdkBytes
import com.typesafe.config.Config
import scala.concurrent.ExecutionContext

class TextGenerationServiceTest extends AsyncFunSuite with Matchers with MockitoSugar {
  implicit val ec: ExecutionContext = ExecutionContext.global

  private val mockConfig = mock[Config]
  private val mockBedrockClient = mock[BedrockRuntimeClient]

  private def setupMockConfig(): Unit = {
    when(mockConfig.getString("bedrock.region")).thenReturn("us-east-1")
    when(mockConfig.getString("bedrock.model-id")).thenReturn("amazon.titan-text-express-v1")
    when(mockConfig.getInt("bedrock.max-tokens")).thenReturn(256)
    when(mockConfig.getDouble("bedrock.temperature")).thenReturn(0.7)
  }

  setupMockConfig()

  test("generateText should use default values when request fields are empty") {
    val service = new TextGenerationService(mockConfig) {
      override protected val bedrockClient: BedrockRuntimeClient = mockBedrockClient
    }

    val request = GenerateTextRequest(prompt = "Test prompt", maxLength = 0, temperature = 0.0f, modelId = "")
    val mockResponsePayload =
      """
        |{
        |  "results": [{"outputText": "Generated response"}],
        |  "inputTextTokenCount": 10
        |}
      """.stripMargin

    when(mockBedrockClient.invokeModel(any[InvokeModelRequest]))
      .thenReturn(software.amazon.awssdk.services.bedrockruntime.model.InvokeModelResponse.builder()
        .body(SdkBytes.fromUtf8String(mockResponsePayload))
        .build())

    service.generateText(request).map { response =>
      response.generatedText shouldBe "Generated response"
      response.tokensUsed shouldBe 10
      response.modelId shouldBe "amazon.titan-text-express-v1"
    }
  }

  test("generateText should handle explicit request values correctly") {
    val service = new TextGenerationService(mockConfig) {
      override protected val bedrockClient: BedrockRuntimeClient = mockBedrockClient
    }

    val request = GenerateTextRequest(prompt = "Another prompt", maxLength = 50, temperature = 0.5f, modelId = "custom-model")
    val mockResponsePayload =
      """
        |{
        |  "results": [{"outputText": "Custom response"}],
        |  "inputTextTokenCount": 20
        |}
      """.stripMargin

    when(mockBedrockClient.invokeModel(any[InvokeModelRequest]))
      .thenReturn(software.amazon.awssdk.services.bedrockruntime.model.InvokeModelResponse.builder()
        .body(SdkBytes.fromUtf8String(mockResponsePayload))
        .build())

    service.generateText(request).map { response =>
      response.generatedText shouldBe "Custom response"
      response.tokensUsed shouldBe 20
      response.modelId shouldBe "custom-model"
    }
  }

  test("generateText should handle missing outputText gracefully") {
    val service = new TextGenerationService(mockConfig) {
      override protected val bedrockClient: BedrockRuntimeClient = mockBedrockClient
    }

    val request = GenerateTextRequest(prompt = "Test with missing output", maxLength = 0, temperature = 0.0f, modelId = "")
    val mockResponsePayload =
      """
        |{
        |  "results": [{}],
        |  "inputTextTokenCount": 5
        |}
      """.stripMargin

    when(mockBedrockClient.invokeModel(any[InvokeModelRequest]))
      .thenReturn(software.amazon.awssdk.services.bedrockruntime.model.InvokeModelResponse.builder()
        .body(SdkBytes.fromUtf8String(mockResponsePayload))
        .build())

    service.generateText(request).map { response =>
      response.generatedText shouldBe "No generated text found."
      response.tokensUsed shouldBe 5
    }
  }


  test("generateText should handle Bedrock API errors") {
    val service = new TextGenerationService(mockConfig) {
      override protected val bedrockClient: BedrockRuntimeClient = mockBedrockClient
    }

    val request = GenerateTextRequest(prompt = "Test error handling", maxLength = 0, temperature = 0.0f, modelId = "")
    when(mockBedrockClient.invokeModel(any[InvokeModelRequest])).thenThrow(new RuntimeException("API Error"))

    service.generateText(request).map { response =>
      response.status.get.code shouldBe ResponseStatus.StatusCode.ERROR
      response.status.get.message shouldBe "API Error"
    }
  }

//  test("generateText should process a valid Bedrock API response correctly") {
//    val service = new TextGenerationService(mockConfig) {
//      override protected val bedrockClient: BedrockRuntimeClient = mockBedrockClient
//    }
//
//    val request = GenerateTextRequest(
//      prompt = "how cats express love?",
//      maxLength = 256,
//      temperature = 0.7f,
//      modelId = "amazon.titan-text-express-v1"
//    )
//
//    val validResponseJson =
//      """
//      {
//        "results": [
//          { "outputText": "Cats show affection in many ways, including purring, kneading, and rubbing against their humans. Cats also mark their humans by rubbing their faces and bodies on them." }
//        ],
//        "inputTextTokenCount": 30
//      }
//    """
//
//    when(mockBedrockClient.invokeModel(any[InvokeModelRequest]))
//      .thenReturn(
//        software.amazon.awssdk.services.bedrockruntime.model.InvokeModelResponse.builder()
//          .body(SdkBytes.fromUtf8String(validResponseJson))
//          .build()
//      )
//
//    service.generateText(request).map { response =>
//      response.generatedText shouldBe "Cats show affection in many ways, including purring, kneading, and rubbing against their humans. Cats also mark their humans by rubbing their faces and bodies on them."
//      response.tokensUsed shouldBe 30
//      response.modelId shouldBe "amazon.titan-text-express-v1"
//      response.status.get.code shouldBe ResponseStatus.StatusCode.SUCCESS
//      response.status.get.message shouldBe "Text generated successfully."
//    }
//  }
}
