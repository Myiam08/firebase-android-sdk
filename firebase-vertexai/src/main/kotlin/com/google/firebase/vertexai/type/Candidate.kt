/*
 * Copyright 2023 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.firebase.vertexai.type

import com.google.firebase.vertexai.common.util.FirstOrdinalSerializer
import java.util.Calendar
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames

/**
 * A `Candidate` represents a single response generated by the model for a given request.
 *
 * @property content The actual content generated by the model.
 * @property safetyRatings A list of [SafetyRating]s describing the generated content.
 * @property citationMetadata Metadata about the sources used to generate this content.
 * @property finishReason The reason the model stopped generating content, if it exist.
 */
public class Candidate
internal constructor(
  public val content: Content,
  public val safetyRatings: List<SafetyRating>,
  public val citationMetadata: CitationMetadata?,
  public val finishReason: FinishReason?
) {

  @Serializable
  internal data class Internal(
    val content: Content.Internal? = null,
    val finishReason: FinishReason.Internal? = null,
    val safetyRatings: List<SafetyRating.Internal>? = null,
    val citationMetadata: CitationMetadata.Internal? = null,
    val groundingMetadata: GroundingMetadata? = null,
  ) {
    internal fun toPublic(): Candidate {
      val safetyRatings = safetyRatings?.map { it.toPublic() }.orEmpty()
      val citations = citationMetadata?.toPublic()
      val finishReason = finishReason?.toPublic()

      return Candidate(
        this.content?.toPublic() ?: content("model") {},
        safetyRatings,
        citations,
        finishReason
      )
    }

    @Serializable
    internal data class GroundingMetadata(
      @SerialName("web_search_queries") val webSearchQueries: List<String>?,
      @SerialName("search_entry_point") val searchEntryPoint: SearchEntryPoint?,
      @SerialName("retrieval_queries") val retrievalQueries: List<String>?,
      @SerialName("grounding_attribution") val groundingAttribution: List<GroundingAttribution>?,
    ) {

      @Serializable
      internal data class SearchEntryPoint(
        @SerialName("rendered_content") val renderedContent: String?,
        @SerialName("sdk_blob") val sdkBlob: String?,
      )

      @Serializable
      internal data class GroundingAttribution(
        val segment: Segment,
        @SerialName("confidence_score") val confidenceScore: Float?,
      ) {

        @Serializable
        internal data class Segment(
          @SerialName("start_index") val startIndex: Int,
          @SerialName("end_index") val endIndex: Int,
        )
      }
    }
  }
}

/**
 * An assessment of the potential harm of some generated content.
 *
 * The rating will be restricted to a particular [category].
 *
 * @property category The category of harm being assessed (e.g., Hate speech).
 * @property probability The likelihood of the content causing harm.
 * @property probabilityScore A numerical score representing the probability of harm, between 0 and
 * 1.
 * @property blocked Indicates whether the content was blocked due to safety concerns.
 * @property severity The severity of the potential harm.
 * @property severityScore A numerical score representing the severity of harm.
 */
public class SafetyRating
internal constructor(
  public val category: HarmCategory,
  public val probability: HarmProbability,
  public val probabilityScore: Float = 0f,
  public val blocked: Boolean? = null,
  public val severity: HarmSeverity? = null,
  public val severityScore: Float? = null
) {

  @Serializable
  internal data class Internal
  @JvmOverloads
  constructor(
    val category: HarmCategory.Internal,
    val probability: HarmProbability.Internal,
    val blocked: Boolean? = null, // TODO(): any reason not to default to false?
    val probabilityScore: Float? = null,
    val severity: HarmSeverity.Internal? = null,
    val severityScore: Float? = null,
  ) {

    internal fun toPublic() =
      SafetyRating(
        category = category.toPublic(),
        probability = probability.toPublic(),
        probabilityScore = probabilityScore ?: 0f,
        blocked = blocked,
        severity = severity?.toPublic(),
        severityScore = severityScore
      )
  }
}

/**
 * A collection of source attributions for a piece of content.
 *
 * @property citations A list of individual cited sources and the parts of the content to which they
 * apply.
 */
public class CitationMetadata internal constructor(public val citations: List<Citation>) {

  @Serializable
  internal data class Internal
  @OptIn(ExperimentalSerializationApi::class)
  internal constructor(@JsonNames("citations") val citationSources: List<Citation.Internal>) {

    internal fun toPublic() = CitationMetadata(citationSources.map { it.toPublic() })
  }
}

/**
 * Represents a citation of content from an external source within the model's output.
 *
 * When the language model generates text that includes content from another source, it should
 * provide a citation to properly attribute the original source. This class encapsulates the
 * metadata associated with that citation.
 *
 * @property title The title of the cited source, if available.
 * @property startIndex The (inclusive) starting index within the model output where the cited
 * content begins.
 * @property endIndex The (exclusive) ending index within the model output where the cited content
 * ends.
 * @property uri The URI of the cited source, if available.
 * @property license The license under which the cited content is distributed under, if available.
 * @property publicationDate The date of publication of the cited source, if available.
 */
public class Citation
internal constructor(
  public val title: String? = null,
  public val startIndex: Int = 0,
  public val endIndex: Int,
  public val uri: String? = null,
  public val license: String? = null,
  public val publicationDate: Calendar? = null
) {

  @Serializable
  internal data class Internal(
    val title: String? = null,
    val startIndex: Int = 0,
    val endIndex: Int,
    val uri: String? = null,
    val license: String? = null,
    val publicationDate: Date? = null,
  ) {

    internal fun toPublic(): Citation {
      val publicationDateAsCalendar =
        publicationDate?.let {
          val calendar = Calendar.getInstance()
          // Internal `Date.year` uses 0 to represent not specified. We use 1 as default.
          val year = if (it.year == null || it.year < 1) 1 else it.year
          // Internal `Date.month` uses 0 to represent not specified, or is 1-12 as months. The
          // month as
          // expected by [Calendar] is 0-based, so we subtract 1 or use 0 as default.
          val month = if (it.month == null || it.month < 1) 0 else it.month - 1
          // Internal `Date.day` uses 0 to represent not specified. We use 1 as default.
          val day = if (it.day == null || it.day < 1) 1 else it.day
          calendar.set(year, month, day)
          calendar
        }
      return Citation(
        title = title,
        startIndex = startIndex,
        endIndex = endIndex,
        uri = uri,
        license = license,
        publicationDate = publicationDateAsCalendar
      )
    }

    @Serializable
    internal data class Date(
      /** Year of the date. Must be between 1 and 9999, or 0 for no year. */
      val year: Int? = null,
      /** 1-based index for month. Must be from 1 to 12, or 0 to specify a year without a month. */
      val month: Int? = null,
      /**
       * Day of a month. Must be from 1 to 31 and valid for the year and month, or 0 to specify a
       * year by itself or a year and month where the day isn't significant.
       */
      val day: Int? = null,
    )
  }
}

/**
 * Represents the reason why the model stopped generating content.
 *
 * @property name The name of the finish reason.
 * @property ordinal The ordinal value of the finish reason.
 */
public class FinishReason private constructor(public val name: String, public val ordinal: Int) {

  @Serializable(Internal.Serializer::class)
  internal enum class Internal {
    UNKNOWN,
    @SerialName("FINISH_REASON_UNSPECIFIED") UNSPECIFIED,
    STOP,
    MAX_TOKENS,
    SAFETY,
    RECITATION,
    OTHER;

    internal object Serializer : KSerializer<Internal> by FirstOrdinalSerializer(Internal::class)

    internal fun toPublic() =
      when (this) {
        MAX_TOKENS -> FinishReason.MAX_TOKENS
        RECITATION -> FinishReason.RECITATION
        SAFETY -> FinishReason.SAFETY
        STOP -> FinishReason.STOP
        OTHER -> FinishReason.OTHER
        else -> FinishReason.UNKNOWN
      }
  }
  public companion object {
    /** A new and not yet supported value. */
    @JvmField public val UNKNOWN: FinishReason = FinishReason("UNKNOWN", 0)

    /** Model finished successfully and stopped. */
    @JvmField public val STOP: FinishReason = FinishReason("STOP", 1)

    /** Model hit the token limit. */
    @JvmField public val MAX_TOKENS: FinishReason = FinishReason("MAX_TOKENS", 2)

    /** [SafetySetting] prevented the model from outputting content. */
    @JvmField public val SAFETY: FinishReason = FinishReason("SAFETY", 3)

    /**
     * The token generation was stopped because the response was flagged for unauthorized citations.
     */
    @JvmField public val RECITATION: FinishReason = FinishReason("RECITATION", 4)

    /** Model stopped for another reason. */
    @JvmField public val OTHER: FinishReason = FinishReason("OTHER", 5)
  }
}
