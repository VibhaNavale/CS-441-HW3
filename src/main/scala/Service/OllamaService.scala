package Service

import akka.actor.ActorSystem
import scala.concurrent.{ExecutionContext, Future}
import io.github.ollama4j.OllamaAPI
import io.github.ollama4j.models.OllamaResult
import io.github.ollama4j.utils.Options
import java.nio.file.{Files, Paths}
import java.nio.charset.StandardCharsets
import spray.json._
import org.slf4j.LoggerFactory

case class Conversation(prompt: String, response: String)

object ConversationJsonProtocol extends DefaultJsonProtocol {
  implicit val conversationFormat: RootJsonFormat[Conversation] = jsonFormat2(Conversation)
}

import ConversationJsonProtocol._

object ConversationTemplates {
  // Various template responses
  val continueConversation: String = "Can you explain that further?"
  val askClarification: String = "Could you clarify what you mean by that?"
  val followUpQuestion: String = "Tell me more."
  val askForExample: String = "Can you give me an example of that?"
  val askForMoreDetails: String = "Could you provide more details?"
  val askForAlternatives: String = "What other options are available?"
  val askForOpinion: String = "What do you think about this?"
  val expressAgreement: String = "I see, that makes sense."
  val askForSummary: String = "Could you summarize what you've said so far?"
  val askForSolution: String = "How would you solve this problem?"
  val askForNextStep: String = "What's the next step in this process?"
  val askForProsAndCons: String = "What are the pros and cons of this?"
  val casualStarter: String = "That's interesting. Can you tell me more?"
  val askForFeedback: String = "What do you think could be improved?"
  val askForAnotherPerspective: String = "Can you see this from another perspective?"
  val encourageElaboration: String = "That sounds interesting. Can you elaborate on that?"
}

class OllamaService(config: com.typesafe.config.Config)(implicit system: ActorSystem, executionContext: ExecutionContext) {

  private val logger = LoggerFactory.getLogger(getClass)

  private val host = config.getString("ollama.host")
  private val model = config.getString("ollama.model")
  private val requestTimeoutSeconds = config.getInt("ollama.request-timeout-seconds")
  private val maxResponses = config.getInt("ollama.max-responses")

  private val ollamaAPI = new OllamaAPI(host)
  private var conversationHistory: List[Conversation] = List()

  private def selectTemplate(history: List[Conversation], lastResponse: String): String = {
    // Choose different templates based on history size or keywords in the last response

    if (history.size % 3 == 0) {
      // After every third message, ask for a solution or advice
      ConversationTemplates.askForSolution
    } else if (history.size % 5 == 0) {
      // After every fifth message, ask for a summary
      ConversationTemplates.askForSummary
    } else if (lastResponse.contains("example") || lastResponse.contains("details")) {
      // If the last response mentions example or details, ask for more examples or details
      ConversationTemplates.askForExample
    } else if (lastResponse.contains("next") || lastResponse.contains("step")) {
      // If the last response asks for next steps, ask for the next step in the process
      ConversationTemplates.askForNextStep
    } else if (lastResponse.contains("pros") || lastResponse.contains("cons")) {
      // If the last response mentions pros and cons, ask for pros and cons
      ConversationTemplates.askForProsAndCons
    } else if (lastResponse.contains("what") || lastResponse.contains("how")) {
      // If the last response contains question words like "what" or "how", ask for alternatives or opinions
      ConversationTemplates.askForAlternatives
    } else if (history.size % 2 == 0) {
      // If the number of messages is even, continue the conversation
      ConversationTemplates.continueConversation
    } else if (lastResponse.contains("clarify") || lastResponse.contains("explain")) {
      // If the last response contains "clarify" or "explain", ask for clarification
      ConversationTemplates.askClarification
    } else {
      // Default case: ask for what happened next
      ConversationTemplates.followUpQuestion
    }
  }

  def generateConversationalResponse(initialPrompt: String): Future[List[Conversation]] = {
    // Initialize the conversation history with the first prompt
    val initialConversation = Conversation(initialPrompt, "")  // The first prompt has no response initially
    conversationHistory = List(initialConversation)

    def generateNextResponse(history: List[Conversation]): Future[List[Conversation]] = {
      if (history.count(_.response.nonEmpty) >= maxResponses) {
        // End the conversation if max responses are reached
        Future.successful(history)
      } else {
        val lastResponse = history.lastOption.map(_.response).getOrElse("")
        val nextPrompt = selectTemplate(history, lastResponse)  // Select next prompt based on history and last response

        val conversationText = history.map {
          case Conversation(prompt, response) => s"Prompt: $prompt\nResponse: $response"
        }.mkString("\n") + s"\nPrompt: $nextPrompt\nResponse:"

        val ollamaResponseFuture: Future[OllamaResult] = Future {
          ollamaAPI.setRequestTimeoutSeconds(requestTimeoutSeconds)
          try {
            ollamaAPI.generate(model, conversationText, false, new Options(new java.util.HashMap()))
          } catch {
            case ex: Exception =>
              logger.error(s"Error calling Ollama API: ${ex.getMessage}", ex)
              throw ex
          }
        }

        ollamaResponseFuture.flatMap { result =>
          val ollamaResponse = result.getResponse
          val updatedHistory = history.init :+ Conversation(nextPrompt, ollamaResponse)
          val nextConversation = updatedHistory :+ Conversation(ollamaResponse, "")

          // Ensure the first conversation is correctly included in the history
          val finalHistory = if (history == List(initialConversation)) {
            List(initialConversation.copy(response = ollamaResponse)) ++ nextConversation
          } else {
            nextConversation
          }

          generateNextResponse(finalHistory)
        }
      }
    }

    // Start generating responses with the initial prompt
    generateNextResponse(conversationHistory).map { finalHistory =>
      conversationHistory = finalHistory.filter(_.response.nonEmpty).takeRight(maxResponses)
      writeToFile(conversationHistory)
      conversationHistory
    }
  }

  private def writeToFile(conversation: List[Conversation]): Unit = {
    try {
      // Create the structured JSON response array with prompt-response pairs
      val responsePrompts = conversation.map {
        case Conversation(prompt, response) =>
          Map("prompt" -> prompt, "response" -> response)
      }

      // Convert the response to JSON format
      val jsonResponse = responsePrompts.toJson.prettyPrint

      // Write to file
      Files.write(Paths.get("output/ollama.json"), jsonResponse.getBytes(StandardCharsets.UTF_8))
      logger.info("Conversation history saved to output/ollama.json")
    } catch {
      case ex: Exception =>
        logger.error(s"Error writing conversation history to file: ${ex.getMessage}", ex)
    }
  }
}
