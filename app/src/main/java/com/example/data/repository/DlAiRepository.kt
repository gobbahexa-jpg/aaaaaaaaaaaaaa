package com.example.data.repository

import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

class DlAiRepository {
    private val chatModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-2.5-flash",
            systemInstruction = content {
                text(
                    "You are DL Helper, the helpful assistant built into DL Chat. " +
                        "Write like a thoughtful, down-to-earth person—clear, direct, and easy to read. " +
                        "Match the length and depth of your reply to what the person actually needs: give short answers to quick questions and clear steps for complex ones. " +
                        "Skip filler intros like 'Certainly!' or 'As a helper', avoid repeating yourself, and explain any technical idea in everyday language. " +
                        "If the user writes in Sinhala, Tamil, or another language, reply naturally in that same language."
                )
            }
        )
    }

    private val smartReplyModel by lazy {
        val schema = Schema.obj(
            properties = mapOf(
                "replies" to Schema.array(
                    items = Schema.string(description = "A natural, everyday reply under 10 words"),
                    description = "Exactly 3 suggested replies"
                )
            )
        )
        Firebase.ai.generativeModel(
            modelName = "gemini-2.5-flash-lite",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = schema
            }
        )
    }

    fun streamAssistantReply(prompt: String, historyPairs: List<Pair<Boolean, String>>): Flow<String> = flow {
        val chat = chatModel.startChat(
            history = historyPairs.takeLast(12).map { (fromUser, text) ->
                content(if (fromUser) "user" else "model") { text(text) }
            }
        )
        val buffer = StringBuilder()
        try {
            withTimeout(60_000L) {
                chat.sendMessageStream(prompt).collect { chunk ->
                    val piece = chunk.text.orEmpty()
                    if (piece.isNotEmpty()) {
                        buffer.append(piece)
                        emit(buffer.toString())
                    }
                }
            }
            if (buffer.isBlank()) {
                throw IllegalStateException("Nothing came back. Try asking again.")
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "AI assistant stream timed out", e)
            throw IllegalStateException("That took longer than expected. Please try again in a moment.", e)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "AI assistant stream failed", e)
            throw e
        }
    }

    suspend fun summarizeConversation(chatTitle: String, transcript: String): Result<String> = try {
        val response = withTimeout(60_000L) {
            chatModel.generateContent(
                "Give a plain, easy-to-scan recap of the conversation \"$chatTitle\" in 3 to 5 short bullet points. " +
                    "Focus on what was decided, open questions, and next steps:\n\n$transcript"
            )
        }
        val text = response.text?.trim()
        if (text.isNullOrBlank()) Result.failure(IllegalStateException("Couldn't create a summary right now."))
        else Result.success(text)
    } catch (e: TimeoutCancellationException) {
        Log.e(TAG, "Summary timed out", e)
        Result.failure(IllegalStateException("Creating the summary took too long. Try again."))
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.e(TAG, "Summary failed", e)
        Result.failure(e)
    }

    suspend fun translateMessage(messageText: String, targetLanguage: String): Result<String> = try {
        val response = withTimeout(45_000L) {
            chatModel.generateContent(
                "Translate this message naturally into $targetLanguage so it sounds like a real person wrote it. " +
                    "Reply with only the translation:\n\n$messageText"
            )
        }
        val text = response.text?.trim()
        if (text.isNullOrBlank()) Result.failure(IllegalStateException("Couldn't translate this message right now."))
        else Result.success(text)
    } catch (e: TimeoutCancellationException) {
        Log.e(TAG, "Translation timed out", e)
        Result.failure(IllegalStateException("Translation took too long. Try again."))
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.e(TAG, "Translation failed", e)
        Result.failure(e)
    }

    suspend fun explainMessage(messageText: String): Result<String> = try {
        val response = withTimeout(45_000L) {
            chatModel.generateContent(
                "Explain what this message means in plain, everyday language without jargon:\n\n$messageText"
            )
        }
        val text = response.text?.trim()
        if (text.isNullOrBlank()) Result.failure(IllegalStateException("Couldn't explain this message right now."))
        else Result.success(text)
    } catch (e: TimeoutCancellationException) {
        Log.e(TAG, "Explain timed out", e)
        Result.failure(IllegalStateException("Explanation took too long. Try again."))
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.e(TAG, "Explain failed", e)
        Result.failure(e)
    }

    suspend fun rewriteDraft(draft: String, tone: String): Result<String> = try {
        val response = withTimeout(45_000L) {
            chatModel.generateContent(
                "Rewrite this message in a natural, $tone tone while keeping the exact meaning and fixing any typos. " +
                    "Reply with only the rewritten message:\n\n$draft"
            )
        }
        val text = response.text?.trim()
        if (text.isNullOrBlank()) Result.failure(IllegalStateException("Couldn't rewrite your draft right now."))
        else Result.success(text)
    } catch (e: TimeoutCancellationException) {
        Log.e(TAG, "Rewrite timed out", e)
        Result.failure(IllegalStateException("Rewriting took too long. Try again."))
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.e(TAG, "Rewrite failed", e)
        Result.failure(e)
    }

    suspend fun aiSearchAssist(query: String, chatContext: String): Result<String> = try {
        val response = withTimeout(45_000L) {
            chatModel.generateContent(
                "Look ONLY at these non-encrypted chat snippets and answer \"$query\" in plain language. " +
                    "Mention which chat matched, or simply say nothing matched:\n\n$chatContext"
            )
        }
        val text = response.text?.trim()
        if (text.isNullOrBlank()) Result.failure(IllegalStateException("Couldn't finish the search right now."))
        else Result.success(text)
    } catch (e: TimeoutCancellationException) {
        Log.e(TAG, "AI search timed out", e)
        Result.failure(IllegalStateException("Search took too long. Try again."))
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.e(TAG, "AI search failed", e)
        Result.failure(e)
    }

    suspend fun generateSmartReplies(recentContext: String): Result<List<String>> = try {
        val response = withTimeout(30_000L) {
            smartReplyModel.generateContent(
                "Suggest 3 short, natural replies someone might actually send in chat for this context:\n$recentContext"
            )
        }
        val rawJson = response.text ?: throw IllegalStateException("No suggestions returned.")
        val json = JSONObject(rawJson)
        val arr = json.getJSONArray("replies")
        val list = (0 until arr.length()).mapNotNull { arr.optString(it).takeIf { s -> s.isNotBlank() } }
        Result.success(list.take(3))
    } catch (e: TimeoutCancellationException) {
        Log.e(TAG, "Smart replies timed out", e)
        Result.failure(IllegalStateException("Suggestions took too long. Try again."))
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.e(TAG, "Smart replies failed", e)
        Result.failure(e)
    }

    suspend fun summarizeVoiceTranscript(transcript: String): Result<String> = try {
        val response = withTimeout(45_000L) {
            chatModel.generateContent(
                "Summarize this voice note in 1 or 2 plain sentences and list any next steps:\n\n$transcript"
            )
        }
        val text = response.text?.trim()
        if (text.isNullOrBlank()) Result.failure(IllegalStateException("Couldn't summarize that voice note."))
        else Result.success(text)
    } catch (e: TimeoutCancellationException) {
        Result.failure(IllegalStateException("Voice summary took too long. Try again."))
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.e(TAG, "Voice summary failed", e)
        Result.failure(e)
    }

    suspend fun categorizeChatSuggestion(chatTitle: String, recentMessage: String): Result<String> = try {
        val response = withTimeout(30_000L) {
            chatModel.generateContent(
                "Pick one simple 1-2 word folder name (such as Friends, Work, Updates, Support, or Priority) " +
                    "for the chat \"$chatTitle\" with recent message: \"$recentMessage\". Reply with only the folder name."
            )
        }
        val text = response.text?.trim()
        if (text.isNullOrBlank()) Result.failure(IllegalStateException("Couldn't suggest a folder name."))
        else Result.success(text.take(24))
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    private companion object {
        const val TAG = "DlAiRepository"
    }
}
