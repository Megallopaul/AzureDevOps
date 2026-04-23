package azuredevops.util

import azuredevops.model.PullRequestStatus
import azuredevops.model.ThreadStatus
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonPrimitive
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import java.lang.reflect.Type

/**
 * Gson configuration for handling sealed classes and other custom types.
 */
object GsonFactory {

    /**
     * Creates a Gson instance configured with custom type adapters.
     */
    fun createGson(): Gson =
        GsonBuilder()
            .registerTypeAdapter(PullRequestStatus::class.java, PullRequestStatusDeserializer)
            .registerTypeAdapter(ThreadStatus::class.java, ThreadStatusDeserializer)
            .create()

    /**
     * Deserializer for PullRequestStatus sealed class.
     * Converts string values from JSON to the appropriate object.
     */
    private object PullRequestStatusDeserializer : JsonDeserializer<PullRequestStatus> {
        override fun deserialize(
            json: JsonElement,
            typeOfT: Type,
            context: JsonDeserializationContext,
        ): PullRequestStatus {
            val value = json.asString.lowercase()
            return PullRequestStatus.fromValue(value)
        }
    }

    /**
     * Deserializer for ThreadStatus sealed class.
     * Converts string values from JSON to the appropriate object.
     */
    private object ThreadStatusDeserializer : JsonDeserializer<ThreadStatus> {
        override fun deserialize(
            json: JsonElement,
            typeOfT: Type,
            context: JsonDeserializationContext,
        ): ThreadStatus {
            val value = json.asString.lowercase()
            return ThreadStatus.fromValue(value)
        }
    }

    /**
     * Serializer for PullRequestStatus sealed class.
     * Converts object to its string value for JSON.
     */
    private object PullRequestStatusSerializer : JsonSerializer<PullRequestStatus> {
        override fun serialize(
            src: PullRequestStatus,
            typeOfSrc: Type,
            context: JsonSerializationContext,
        ): JsonElement = JsonPrimitive(src.value)
    }

    /**
     * Serializer for ThreadStatus sealed class.
     * Converts object to its string value for JSON.
     */
    private object ThreadStatusSerializer : JsonSerializer<ThreadStatus> {
        override fun serialize(
            src: ThreadStatus,
            typeOfSrc: Type,
            context: JsonSerializationContext,
        ): JsonElement = JsonPrimitive(src.value)
    }
}
