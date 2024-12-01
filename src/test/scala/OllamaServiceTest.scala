package Service

import org.scalatest.flatspec.AsyncFlatSpec
import org.scalatest.matchers.should.Matchers
import org.mockito.Mockito._
import org.mockito.ArgumentMatchers.any
import org.scalatestplus.mockito.MockitoSugar
import akka.actor.ActorSystem
import com.typesafe.config.ConfigFactory
import io.github.ollama4j.OllamaAPI
import io.github.ollama4j.models.OllamaResult

import scala.concurrent.ExecutionContextExecutor

class OllamaServiceTest extends AsyncFlatSpec with Matchers with MockitoSugar {

  implicit val system: ActorSystem = ActorSystem("TestSystem")
  implicit val ec: ExecutionContextExecutor = system.dispatcher

  "OllamaService" should "generate a conversational response" in {
    // Mock configuration
    val mockConfig = ConfigFactory.parseString(
      """
        |ollama {
        |  host = "localhost"
        |  model = "test-model"
        |  request-timeout-seconds = 10
        |  max-responses = 3
        |}
        |""".stripMargin
    )

    // Mock OllamaAPI
    val mockOllamaAPI = mock[OllamaAPI]
    when(mockOllamaAPI.generate(any(), any(), any(), any()))
      .thenReturn(new OllamaResult("Test Response", 60, 200))

    // Create the service with mocked dependencies
    val ollamaService = new OllamaService(mockConfig) {
      override protected val ollamaAPI: OllamaAPI = mockOllamaAPI
    }

    // Test initial prompt
    val initialPrompt = "Hello, how are you?"
    ollamaService.generateConversationalResponse(initialPrompt).map { conversationHistory =>
      conversationHistory should not be empty
      conversationHistory.head.prompt shouldEqual initialPrompt
      conversationHistory.head.response shouldEqual "Test Response"
    }
  }

  it should "limit the conversation history to max-responses" in {
    val mockConfig = ConfigFactory.parseString(
      """
        |ollama {
        |  host = "localhost"
        |  model = "test-model"
        |  request-timeout-seconds = 10
        |  max-responses = 2
        |}
        |""".stripMargin
    )

    val mockOllamaAPI = mock[OllamaAPI]
    when(mockOllamaAPI.generate(any(), any(), any(), any()))
      .thenReturn(new OllamaResult("Mock Response", 60, 200))

    val ollamaService = new OllamaService(mockConfig) {
      override protected val ollamaAPI: OllamaAPI = mockOllamaAPI
    }

    ollamaService.generateConversationalResponse("Start").map { conversationHistory =>
      conversationHistory should have size 2
      all(conversationHistory.map(_.response)) shouldEqual "Mock Response"
    }
  }
}
