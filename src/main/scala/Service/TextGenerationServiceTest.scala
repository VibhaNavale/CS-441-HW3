package Service

import akka.http.scaladsl.testkit.{ScalatestRouteTest, RouteTestTimeout}
import org.mockito.Mockito._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelRequest
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient
import scala.concurrent.Future
import textGen.llm_service.{GenerateTextRequest, GenerateTextResponse, ResponseStatus}

class TextGenerationServiceTest extends AnyWordSpec with Matchers with ScalaFutures with ScalatestRouteTest {

  // Mocking the BedrockRuntimeClient
  val mockBedrockClient: BedrockRuntimeClient = mock[BedrockRuntimeClient]

  // Sample config (mocked)
  val config = new {
    val modelId: String = "amazon.titan-text-express-v1"
    val temperature: Float = 0.7f
    val maxLength: Int = 256
  }

  // Creating the service instance with the mocked client
  val textGenerationService = new TextGenerationService(config) {
    override val bedrockClient: BedrockRuntimeClient = mockBedrockClient
  }

  // Test Case 1: Simple successful text generation
  "TextGenerationService" should {
    "generate text successfully when a valid request is sent" in {
      val request = GenerateTextRequest(prompt = "how cats express love?", maxLength = 256, temperature = 0.7f, modelId = "")

      val expectedResponse = GenerateTextResponse(
        generatedText = "Cats express love by spending time with their owners, purring, rubbing against them, and grooming them. They also bring their owners dead animals as a sign of affection.",
        confidence = 0.7f,
        tokensUsed = 20,
        modelId = "amazon.titan-text-express-v1",
        status = Some(ResponseStatus(code = ResponseStatus.StatusCode.SUCCESS, message = "Text generated successfully."))
      )

      // Mocking the Bedrock invocation result
      when(mockBedrockClient.invokeModel(any[InvokeModelRequest])).thenReturn(Future.successful(expectedResponse))

      // Make the request to the service
      val response = textGenerationService.generateText(request).futureValue

      // Verifying the response matches the expected response
      response.generatedText shouldEqual expectedResponse.generatedText
      response.confidence shouldEqual expectedResponse.confidence
      response.tokensUsed shouldEqual expectedResponse.tokensUsed
      response.status.get.code shouldEqual ResponseStatus.StatusCode.SUCCESS
      response.status.get.message shouldEqual "Text generated successfully."
    }

    // Test Case 2: Error handling when API fails
    "handle errors gracefully if an exception is thrown during text generation" in {
      val request = GenerateTextRequest(prompt = "how cats express love?", maxLength = 256, temperature = 0.7f, modelId = "")

      // Simulating an exception during the Bedrock invocation
      when(mockBedrockClient.invokeModel(any[InvokeModelRequest])).thenThrow(new RuntimeException("API call failed"))

      // Make the request to the service
      val response = textGenerationService.generateText(request).futureValue

      // Checking that the service returns an error response
      response.status.get.code shouldEqual ResponseStatus.StatusCode.ERROR
      response.status.get.message shouldEqual "API call failed"
    }

    // Test Case 3: Check empty prompt scenario
    "handle empty prompt gracefully" in {
      val request = GenerateTextRequest(prompt = "", maxLength = 256, temperature = 0.7f, modelId = "")

      val expectedResponse = GenerateTextResponse(
        generatedText = "Please provide a valid prompt to generate text.",
        confidence = 0.0f,
        tokensUsed = 0,
        modelId = "amazon.titan-text-express-v1",
        status = Some(ResponseStatus(code = ResponseStatus.StatusCode.ERROR, message = "Prompt cannot be empty"))
      )

      // Mocking response for empty prompt
      when(mockBedrockClient.invokeModel(any[InvokeModelRequest])).thenReturn(Future.successful(expectedResponse))

      // Make the request to the service
      val response = textGenerationService.generateText(request).futureValue

      // Verifying the response for empty prompt
      response.status.get.code shouldEqual ResponseStatus.StatusCode.ERROR
      response.status.get.message shouldEqual "Prompt cannot be empty"
      response.generatedText shouldEqual "Please provide a valid prompt to generate text."
    }

    // Test Case 4: Large prompt scenario
    "handle a large prompt gracefully" in {
      val largePrompt = "a" * 1000 // Simulating a large prompt
      val request = GenerateTextRequest(prompt = largePrompt, maxLength = 256, temperature = 0.7f, modelId = "")

      val expectedResponse = GenerateTextResponse(
        generatedText = "Due to the size of the input, we have truncated the generated text.",
        confidence = 0.8f,
        tokensUsed = 100,
        modelId = "amazon.titan-text-express-v1",
        status = Some(ResponseStatus(code = ResponseStatus.StatusCode.SUCCESS, message = "Text generated successfully."))
      )

      // Mocking the Bedrock invocation for a large prompt
      when(mockBedrockClient.invokeModel(any[InvokeModelRequest])).thenReturn(Future.successful(expectedResponse))

      // Make the request to the service
      val response = textGenerationService.generateText(request).futureValue

      // Verifying the response for large prompt
      response.generatedText shouldEqual "Due to the size of the input, we have truncated the generated text."
      response.status.get.code shouldEqual ResponseStatus.StatusCode.SUCCESS
    }

    // Test Case 5: Test for a prompt with no modelId
    "use default modelId when modelId is not provided" in {
      val request = GenerateTextRequest(prompt = "What is AI?", maxLength = 256, temperature = 0.7f, modelId = "")

      val expectedResponse = GenerateTextResponse(
        generatedText = "Artificial Intelligence (AI) refers to the simulation of human intelligence in machines.",
        confidence = 0.75f,
        tokensUsed = 15,
        modelId = "amazon.titan-text-express-v1", // default modelId used
        status = Some(ResponseStatus(code = ResponseStatus.StatusCode.SUCCESS, message = "Text generated successfully."))
      )

      // Mocking the Bedrock invocation response
      when(mockBedrockClient.invokeModel(any[InvokeModelRequest])).thenReturn(Future.successful(expectedResponse))

      // Make the request to the service
      val response = textGenerationService.generateText(request).futureValue

      // Verifying the response
      response.generatedText shouldEqual "Artificial Intelligence (AI) refers to the simulation of human intelligence in machines."
      response.modelId shouldEqual "amazon.titan-text-express-v1" // ensuring default modelId is used
      response.status.get.code shouldEqual ResponseStatus.StatusCode.SUCCESS
    }
  }
}
