package com.socialfirstaidkit.core.data.personalexpression

import kotlinx.serialization.Serializable

@Serializable
enum class LearningThreshold(val label: String, val description: String, val sampleCount: Int) {
    CONSERVATIVE("保守", "积累更多样本再分析，更稳定", 30),
    BALANCED("适中", "推荐：平衡学习速度和准确性", 20),
    AGGRESSIVE("积极", "更快学习，适合频繁使用的用户", 10)
}

@Serializable
data class PersonalExpressionProfile(
    val enabled: Boolean = false,
    val autoLearningEnabled: Boolean = true,
    val learningThreshold: LearningThreshold = LearningThreshold.BALANCED,
    val questionnaireCompleted: Boolean = false,
    val coreStyle: ExpressionCoreStyle = ExpressionCoreStyle(),
    val languageMarkers: ExpressionLanguageMarkers = ExpressionLanguageMarkers(),
    val sceneStyles: ExpressionSceneStyles = ExpressionSceneStyles(),
    val relationshipOffsets: Map<String, ExpressionRelationshipOffset> = defaultRelationshipOffsets(),
    val preferredExpressions: List<String> = emptyList(),
    val avoidedExpressions: List<String> = emptyList(),
    val sampleReplies: Map<String, String> = emptyMap(),
    val version: Int = 1,
    val lastUpdatedAt: Long = System.currentTimeMillis()
) {
    fun summaryTags(): List<String> {
        return listOf(
            sentenceLengthLabel(coreStyle.sentenceLength),
            directnessLabel(coreStyle.directness),
            emojiLabel(languageMarkers.emojiUsage),
            sceneStyles.refusalStyle.label
        ).distinct()
    }

    fun readableSummary(): String {
        return listOf(
            "整体语气偏${warmthLabel(coreStyle.warmth)}，${directnessLabel(coreStyle.directness)}。",
            "通常使用${sentenceLengthLabel(coreStyle.sentenceLength)}，${emojiLabel(languageMarkers.emojiUsage)}。",
            "安慰时偏向${sceneStyles.comfortStyle.label}，拒绝时偏向${sceneStyles.refusalStyle.label}。"
        ).joinToString("")
    }
}

@Serializable
data class ExpressionCoreStyle(
    val directness: Float = 3f,
    val warmth: Float = 3f,
    val formality: Float = 3f,
    val sentenceLength: Float = 3f,
    val humor: Float = 2.5f
)

@Serializable
data class ExpressionLanguageMarkers(
    val emojiUsage: EmojiUsage = EmojiUsage.LOW,
    val fillerWords: Float = 2.5f,
    val networkSlang: Float = 1.5f
)

@Serializable
data class ExpressionSceneStyles(
    val comfortStyle: ComfortStyle = ComfortStyle.EMOTIONAL,
    val refusalStyle: RefusalStyle = RefusalStyle.REASON,
    val apologyStyle: ApologyStyle = ApologyStyle.COMPENSATION,
    val conflictStyle: ConflictStyle = ConflictStyle.COLLABORATING
)

@Serializable
data class ExpressionRelationshipOffset(
    val formalityOffset: Float = 0f,
    val directnessOffset: Float = 0f,
    val warmthOffset: Float = 0f
)

@Serializable
enum class EmojiUsage(val label: String) {
    NONE("不用 emoji"),
    LOW("少量 emoji"),
    MEDIUM("适量 emoji"),
    HIGH("较多 emoji")
}

@Serializable
enum class ComfortStyle(val label: String) {
    EMOTIONAL("先共情安慰"),
    PROBLEM_SOLVING("帮对方分析解决"),
    DISTRACTION("轻轻转移情绪"),
    LISTENING("陪伴倾听")
}

@Serializable
enum class RefusalStyle(val label: String) {
    DIRECT("直接说明"),
    REASON("说明原因"),
    ALTERNATIVE("给替代方案"),
    INDIRECT("委婉留白")
}

@Serializable
enum class ApologyStyle(val label: String) {
    DIRECT("直接认错"),
    EXPLANATION("解释原因"),
    COMPENSATION("补偿修复"),
    HUMOROUS("轻松化解"),
    AVOIDANT("转移话题")
}

@Serializable
enum class ConflictStyle(val label: String) {
    AVOIDING("先冷静暂停"),
    ACCOMMODATING("先缓和让步"),
    COLLABORATING("一起找方案"),
    ASSERTIVE_SOFT("温和坚持立场"),
    COMPETING("明确坚持原则")
}

@Serializable
data class PersonalExpressionQuestion(
    val id: String,
    val title: String,
    val description: String,
    val maxSelections: Int = 1,
    val options: List<PersonalExpressionOption>
)

@Serializable
data class PersonalExpressionOption(
    val id: String,
    val text: String,
    val hint: String
)

@Serializable
data class PersonalExpressionDraft(
    val answers: Map<String, List<String>> = emptyMap(),
    val currentPage: Int = 0,
    val updatedAt: Long = System.currentTimeMillis()
)

@Serializable
data class PersonalExpressionUpdateSuggestion(
    val observations: List<String> = emptyList(),
    val changes: List<String> = emptyList(),
    val previewBefore: String = "",
    val previewAfter: String = "",
    val updatedProfile: PersonalExpressionProfile
)

@Serializable
data class PersonalExpressionPromptContext(
    val profile: PersonalExpressionProfile,
    val relationship: String,
    val summaryTags: List<String>
)

object PersonalExpressionQuestionnaire {
    const val QUESTION_COUNT = 13
    const val QUESTIONS_PER_PAGE = 4

    val questions: List<PersonalExpressionQuestion> = listOf(
        PersonalExpressionQuestion(
            id = "q1_comfort",
            title = "朋友说今天被领导骂了，很委屈",
            description = "你更可能怎么回？",
            options = listOf(
                PersonalExpressionOption("problem", "怎么回事？跟我说说，我帮你分析下怎么避免下次再发生。", "偏问题解决"),
                PersonalExpressionOption("emotional", "抱抱，太不容易了，换我我也委屈。先别想了，晚上吃点好的？", "偏共情安慰"),
                PersonalExpressionOption("distraction", "害，这真的挺烦的。先别往心里放，晚点做点开心的缓缓。", "偏转移情绪"),
                PersonalExpressionOption("listening", "哎，听起来真的不好受。你想聊我就在，不想说也没关系。", "偏陪伴倾听")
            )
        ),
        PersonalExpressionQuestion(
            id = "q2_refusal",
            title = "不太熟的同事找你做一份不在职责内的 PPT",
            description = "你会怎么拒绝？",
            options = listOf(
                PersonalExpressionOption("direct", "不好意思，我手头事太多了，这个做不了。", "直接明确"),
                PersonalExpressionOption("reason", "我最近项目赶进度实在抽不开身，而且这个我也不太擅长，怕耽误你。", "解释原因"),
                PersonalExpressionOption("alternative", "我这边真没空，不过我知道一个模板挺好用，可以发你参考。", "给替代方案"),
                PersonalExpressionOption("indirect", "啊这个我得看看时间安排哈，可能不一定顾得上。", "委婉留白")
            )
        ),
        PersonalExpressionQuestion(
            id = "q3_apology",
            title = "你和朋友约饭迟到了半小时",
            description = "见到对方时第一句更像哪种？",
            options = listOf(
                PersonalExpressionOption("direct", "对不起对不起，我迟到了，是我没算好时间。", "直接认错"),
                PersonalExpressionOption("explanation", "抱歉啊，刚才临时有事耽搁了，让你等这么久。", "解释原因"),
                PersonalExpressionOption("compensation", "真不好意思，让你等这么久。这顿我请，随便点。", "补偿修复"),
                PersonalExpressionOption("humorous", "我来了我来了，迟到的人先自罚点菜。真不好意思，今天我请。", "轻松化解")
            )
        ),
        PersonalExpressionQuestion(
            id = "q4_conflict",
            title = "同事在群里语气很冲地催你交方案",
            description = "但实际上是他没及时给你数据，你会怎么回？",
            options = listOf(
                PersonalExpressionOption("assertive_soft", "昨天可能你太忙漏看了，我拿到数据后已经赶完，刚发群里了。", "温和澄清"),
                PersonalExpressionOption("competing", "数据是昨天下午才给到的，我已经按最快速度处理，方案见附件。", "事实反击"),
                PersonalExpressionOption("humorous", "催单收到，数据一到我就在赶了，热乎的方案已经发上来啦。", "幽默缓和"),
                PersonalExpressionOption("avoiding", "先别在群里争这个，我把方案发上来，细节我们私下对一下。", "降温处理")
            )
        ),
        PersonalExpressionQuestion(
            id = "q5_casual",
            title = "朋友发消息说“今天天气真好啊”",
            description = "你通常怎么接话？",
            options = listOf(
                PersonalExpressionOption("short", "嗯，是不错。", "短句克制"),
                PersonalExpressionOption("extend", "对啊，特别适合出去走走。你今天有安排吗？", "主动延展"),
                PersonalExpressionOption("share", "是啊，前几天一直下雨都快闷坏了，终于放晴了。", "分享感受"),
                PersonalExpressionOption("practical", "哈哈确实，不过下午好像又要下雨，出门记得带伞。", "实用提醒")
            )
        ),
        PersonalExpressionQuestion(
            id = "q6_length",
            title = "别人问你一个需要稍微解释的问题",
            description = "比如“这款手机值得买吗”，你更习惯怎么打字？",
            options = listOf(
                PersonalExpressionOption("fragmented", "连发几条短消息，每条都不长。", "碎片短句"),
                PersonalExpressionOption("standard", "组织成一段 40-80 字左右的话。", "标准长度"),
                PersonalExpressionOption("long", "会把理由、前提和结论都说清楚。", "详细说明"),
                PersonalExpressionOption("depends", "看对方是谁，熟人短一点，不熟的人完整一点。", "看关系调整")
            )
        ),
        PersonalExpressionQuestion(
            id = "q7_fillers",
            title = "“哈、啦、呀、呢、嗷”这类语气词",
            description = "你的使用习惯更接近哪种？",
            options = listOf(
                PersonalExpressionOption("rare", "很少用，基本就是纯文字。", "少语气词"),
                PersonalExpressionOption("moderate", "偶尔用，比如“好的哈”“行啦”。", "适中"),
                PersonalExpressionOption("frequent", "经常用，不然感觉语气太冷。", "常用"),
                PersonalExpressionOption("rich", "用得很多，语气词是我聊天的一部分。", "高频")
            )
        ),
        PersonalExpressionQuestion(
            id = "q8_emoji",
            title = "你平时使用 emoji 或微信表情的习惯",
            description = "更接近哪种？",
            options = listOf(
                PersonalExpressionOption("none", "基本不用。", "无 emoji"),
                PersonalExpressionOption("low", "偶尔用一两个经典表情。", "少量"),
                PersonalExpressionOption("medium", "开心、感谢、缓和语气时会用。", "适量"),
                PersonalExpressionOption("high", "经常用，不然觉得干巴巴。", "较多")
            )
        ),
        PersonalExpressionQuestion(
            id = "q9_avoid",
            title = "哪些表达你绝对不想让 AI 替你说？",
            description = "最多选 3 个，会作为生成时的避雷项。",
            maxSelections = 3,
            options = listOf(
                PersonalExpressionOption("official", "太官方太客套，像客服。", "避开官方腔"),
                PersonalExpressionOption("preachy", "太鸡汤、太说教。", "避开说教"),
                PersonalExpressionOption("greasy", "太油腻、太假熟。", "避开油腻"),
                PersonalExpressionOption("cold", "太冷淡太生硬，像机器人。", "避开冷淡"),
                PersonalExpressionOption("slang", "网络梗太多。", "避开梗太密"),
                PersonalExpressionOption("pleasing", "太卑微，总是委屈自己。", "避开讨好")
            )
        ),
        PersonalExpressionQuestion(
            id = "q10_distance",
            title = "同样是拒绝请求，对好朋友和不太熟的人",
            description = "你的说话方式差别大吗？",
            options = listOf(
                PersonalExpressionOption("same", "差不多，我对谁都那样。", "差异小"),
                PersonalExpressionOption("slight", "有点差别，不熟的人会客气一点。", "轻微调整"),
                PersonalExpressionOption("large", "差别挺大，熟人直接，不熟的人委婉很多。", "明显调整"),
                PersonalExpressionOption("very_large", "差别非常大，几乎两种说话方式。", "强关系差异")
            )
        ),
        PersonalExpressionQuestion(
            id = "q11_professional",
            title = "领导周日晚上让你明早前改完报告",
            description = "你虽然不爽，但必须处理。你倾向于怎么回？",
            options = listOf(
                PersonalExpressionOption("formal", "好的，我今晚处理完，明早会前发您最新版，不影响会议。", "正式配合"),
                PersonalExpressionOption("concise", "收到。今晚会改完，明天会前可以确认。", "简洁专业"),
                PersonalExpressionOption("warm", "收到，没问题，我这就改，改好发您哈。", "亲和配合"),
                PersonalExpressionOption("boundary", "收到，我会优先处理关键修改点，明早会前给到可用版本。", "保留边界")
            )
        ),
        PersonalExpressionQuestion(
            id = "q12_humor",
            title = "朋友吐槽一件倒霉事时",
            description = "你会用玩笑缓和气氛吗？",
            options = listOf(
                PersonalExpressionOption("never", "不会，对方都倒霉了还开玩笑不太好。", "少幽默"),
                PersonalExpressionOption("sometimes", "偶尔会，看关系和场合。", "适度幽默"),
                PersonalExpressionOption("often", "经常会，笑一笑事情就过去了。", "常用幽默"),
                PersonalExpressionOption("always", "会，我习惯用轻松方式化解尴尬。", "高幽默")
            )
        ),
        PersonalExpressionQuestion(
            id = "q13_boundary",
            title = "有人反复找你帮小忙，已经影响到你",
            description = "你通常会怎么处理？",
            options = listOf(
                PersonalExpressionOption("direct", "直接说清楚我没空，明确边界。", "边界明确"),
                PersonalExpressionOption("soft", "找个理由推掉，但不把话说太死。", "委婉边界"),
                PersonalExpressionOption("maintain", "虽然不情愿，但还是会帮，不想伤和气。", "优先关系"),
                PersonalExpressionOption("distance", "先帮几次，后面慢慢疏远。", "回避疏远")
            )
        )
    )

    fun buildProfileFromAnswers(
        answers: Map<String, List<String>>,
        enabled: Boolean = false,
        autoLearningEnabled: Boolean = true
    ): PersonalExpressionProfile {
        val q1 = answers.first("q1_comfort")
        val q2 = answers.first("q2_refusal")
        val q3 = answers.first("q3_apology")
        val q4 = answers.first("q4_conflict")
        val q5 = answers.first("q5_casual")
        val q6 = answers.first("q6_length")
        val q7 = answers.first("q7_fillers")
        val q8 = answers.first("q8_emoji")
        val q9 = answers["q9_avoid"].orEmpty()
        val q10 = answers.first("q10_distance")
        val q11 = answers.first("q11_professional")
        val q12 = answers.first("q12_humor")
        val q13 = answers.first("q13_boundary")

        val directness = average(
            when (q2) {
                "direct" -> 4.6f
                "reason" -> 3.3f
                "alternative" -> 3.1f
                else -> 1.8f
            },
            when (q4) {
                "competing" -> 4.5f
                "assertive_soft" -> 3.2f
                "humorous" -> 2.4f
                else -> 2.2f
            },
            when (q13) {
                "direct" -> 4.6f
                "soft" -> 2.7f
                "maintain" -> 1.8f
                else -> 2.1f
            }
        )
        val warmth = average(
            when (q1) {
                "emotional" -> 4.7f
                "listening" -> 4.0f
                "distraction" -> 3.3f
                else -> 2.5f
            },
            when (q5) {
                "extend" -> 4.2f
                "share" -> 3.8f
                "practical" -> 3.2f
                else -> 2.2f
            }
        )
        val formality = average(
            when (q7) {
                "rare" -> 3.8f
                "moderate" -> 2.8f
                "frequent" -> 2.1f
                else -> 1.6f
            },
            when (q11) {
                "formal" -> 4.6f
                "concise" -> 3.8f
                "boundary" -> 3.7f
                else -> 2.5f
            }
        )
        val sentenceLength = average(
            when (q5) {
                "short" -> 1.7f
                "extend" -> 3.1f
                "share" -> 3.8f
                else -> 3.0f
            },
            when (q6) {
                "fragmented" -> 2.0f
                "standard" -> 3.0f
                "long" -> 4.5f
                else -> 3.3f
            }
        )
        val humor = when (q12) {
            "never" -> 1.2f
            "sometimes" -> 2.6f
            "often" -> 3.9f
            else -> 4.7f
        }
        val emojiUsage = when (q8) {
            "none" -> EmojiUsage.NONE
            "low" -> EmojiUsage.LOW
            "medium" -> EmojiUsage.MEDIUM
            else -> EmojiUsage.HIGH
        }
        val fillerWords = when (q7) {
            "rare" -> 1.3f
            "moderate" -> 2.7f
            "frequent" -> 4.0f
            else -> 4.7f
        }
        val sceneStyles = ExpressionSceneStyles(
            comfortStyle = when (q1) {
                "problem" -> ComfortStyle.PROBLEM_SOLVING
                "distraction" -> ComfortStyle.DISTRACTION
                "listening" -> ComfortStyle.LISTENING
                else -> ComfortStyle.EMOTIONAL
            },
            refusalStyle = when (q2) {
                "direct" -> RefusalStyle.DIRECT
                "alternative" -> RefusalStyle.ALTERNATIVE
                "indirect" -> RefusalStyle.INDIRECT
                else -> RefusalStyle.REASON
            },
            apologyStyle = when (q3) {
                "direct" -> ApologyStyle.DIRECT
                "explanation" -> ApologyStyle.EXPLANATION
                "humorous" -> ApologyStyle.HUMOROUS
                else -> ApologyStyle.COMPENSATION
            },
            conflictStyle = when (q4) {
                "competing" -> ConflictStyle.COMPETING
                "assertive_soft" -> ConflictStyle.ASSERTIVE_SOFT
                "avoiding" -> ConflictStyle.AVOIDING
                else -> ConflictStyle.COLLABORATING
            }
        )
        val distanceMultiplier = when (q10) {
            "same" -> 0.25f
            "slight" -> 0.55f
            "large" -> 0.85f
            else -> 1.1f
        }
        val professionalOffset = when (q11) {
            "formal" -> ExpressionRelationshipOffset(formalityOffset = 1.0f, directnessOffset = -0.5f, warmthOffset = -0.1f)
            "concise" -> ExpressionRelationshipOffset(formalityOffset = 0.7f, directnessOffset = 0.1f, warmthOffset = -0.2f)
            "warm" -> ExpressionRelationshipOffset(formalityOffset = 0.3f, directnessOffset = -0.1f, warmthOffset = 0.4f)
            else -> ExpressionRelationshipOffset(formalityOffset = 0.6f, directnessOffset = 0.3f, warmthOffset = -0.1f)
        }

        val avoided = q9.mapNotNull { avoidedExpressionLabel(it) }
        return PersonalExpressionProfile(
            enabled = enabled,
            autoLearningEnabled = autoLearningEnabled,
            questionnaireCompleted = true,
            coreStyle = ExpressionCoreStyle(
                directness = directness,
                warmth = warmth,
                formality = formality,
                sentenceLength = sentenceLength,
                humor = humor
            ),
            languageMarkers = ExpressionLanguageMarkers(
                emojiUsage = emojiUsage,
                fillerWords = fillerWords,
                networkSlang = if ("slang" in q9) 0.8f else 1.8f
            ),
            sceneStyles = sceneStyles,
            relationshipOffsets = mapOf(
                "friend" to ExpressionRelationshipOffset(),
                "family" to ExpressionRelationshipOffset(
                    formalityOffset = -0.6f * distanceMultiplier,
                    directnessOffset = 0.5f * distanceMultiplier,
                    warmthOffset = 0.4f * distanceMultiplier
                ),
                "romantic" to ExpressionRelationshipOffset(
                    formalityOffset = -0.7f * distanceMultiplier,
                    directnessOffset = 0.35f * distanceMultiplier,
                    warmthOffset = 0.6f * distanceMultiplier
                ),
                "colleague" to ExpressionRelationshipOffset(
                    formalityOffset = 0.35f * distanceMultiplier,
                    directnessOffset = -0.2f * distanceMultiplier,
                    warmthOffset = -0.1f * distanceMultiplier
                ),
                "leader" to professionalOffset,
                "stranger" to ExpressionRelationshipOffset(
                    formalityOffset = 0.75f * distanceMultiplier,
                    directnessOffset = -0.55f * distanceMultiplier,
                    warmthOffset = -0.2f * distanceMultiplier
                )
            ),
            preferredExpressions = preferredExpressionsFor(q2, q7, q13),
            avoidedExpressions = avoided,
            sampleReplies = sampleRepliesFor(sceneStyles),
            lastUpdatedAt = System.currentTimeMillis()
        )
    }

    fun defaultProfile(enabled: Boolean = false): PersonalExpressionProfile {
        return PersonalExpressionProfile(
            enabled = enabled,
            autoLearningEnabled = true,
            questionnaireCompleted = true,
            coreStyle = ExpressionCoreStyle(
                directness = 3f,
                warmth = 3.5f,
                formality = 2.8f,
                sentenceLength = 2.8f,
                humor = 2.3f
            ),
            languageMarkers = ExpressionLanguageMarkers(
                emojiUsage = EmojiUsage.LOW,
                fillerWords = 2.5f,
                networkSlang = 1.2f
            ),
            sceneStyles = ExpressionSceneStyles(),
            avoidedExpressions = listOf("太官方", "太鸡汤", "太油腻"),
            sampleReplies = sampleRepliesFor(ExpressionSceneStyles()),
            lastUpdatedAt = System.currentTimeMillis()
        )
    }

    private fun Map<String, List<String>>.first(key: String): String {
        return this[key]?.firstOrNull().orEmpty()
    }
}

fun defaultRelationshipOffsets(): Map<String, ExpressionRelationshipOffset> = mapOf(
    "friend" to ExpressionRelationshipOffset(),
    "family" to ExpressionRelationshipOffset(formalityOffset = -0.5f, directnessOffset = 0.4f, warmthOffset = 0.4f),
    "romantic" to ExpressionRelationshipOffset(formalityOffset = -0.6f, directnessOffset = 0.3f, warmthOffset = 0.5f),
    "colleague" to ExpressionRelationshipOffset(formalityOffset = 0.3f, directnessOffset = -0.2f, warmthOffset = -0.1f),
    "leader" to ExpressionRelationshipOffset(formalityOffset = 0.8f, directnessOffset = -0.4f, warmthOffset = -0.1f),
    "stranger" to ExpressionRelationshipOffset(formalityOffset = 0.7f, directnessOffset = -0.5f, warmthOffset = -0.2f)
)

fun clampStyleValue(value: Float): Float = value.coerceIn(1f, 5f)

fun sentenceLengthLabel(value: Float): String = when {
    value < 2.2f -> "短句"
    value < 3.5f -> "中短句"
    value < 4.3f -> "较详细"
    else -> "详细解释"
}

fun directnessLabel(value: Float): String = when {
    value < 2.2f -> "委婉留白"
    value < 3.4f -> "委婉但清楚"
    value < 4.3f -> "直接明确"
    else -> "边界很明确"
}

fun warmthLabel(value: Float): String = when {
    value < 2.2f -> "理性克制"
    value < 3.5f -> "自然温和"
    value < 4.4f -> "温暖体贴"
    else -> "热情共情"
}

fun emojiLabel(value: EmojiUsage): String = value.label

private fun average(vararg values: Float): Float = values.average().toFloat().let(::clampStyleValue)

private fun avoidedExpressionLabel(id: String): String? = when (id) {
    "official" -> "太官方太客套，像客服说话"
    "preachy" -> "太鸡汤、太说教"
    "greasy" -> "太油腻、太假熟"
    "cold" -> "太冷淡太生硬，像机器人"
    "slang" -> "网络梗太多"
    "pleasing" -> "太卑微，总是委屈自己"
    else -> null
}

private fun preferredExpressionsFor(refusal: String, fillers: String, boundary: String): List<String> {
    val items = mutableListOf<String>()
    when (refusal) {
        "reason" -> items += listOf("我这边最近有点排不开", "怕耽误你")
        "alternative" -> items += listOf("我可以发你参考", "要不试试这个")
        "indirect" -> items += listOf("我看看安排", "可能不一定方便")
    }
    when (fillers) {
        "moderate" -> items += listOf("好的哈", "可以呀")
        "frequent", "rich" -> items += listOf("好滴", "没问题啦")
    }
    when (boundary) {
        "soft" -> items += "这次可能不太方便"
        "direct" -> items += "我这边做不了"
    }
    return items.distinct().take(8)
}

private fun sampleRepliesFor(sceneStyles: ExpressionSceneStyles): Map<String, String> {
    val comfort = when (sceneStyles.comfortStyle) {
        ComfortStyle.PROBLEM_SOLVING -> "听起来确实挺卡的。要不你先说说最麻烦的是哪一步，我帮你一起捋一下。"
        ComfortStyle.DISTRACTION -> "这事真的烦，先别一直想了，等缓过来再处理也可以。"
        ComfortStyle.LISTENING -> "我在，你想说就慢慢说，不想说也没关系。"
        ComfortStyle.EMOTIONAL -> "这真的挺委屈的，换我也会难受。先别太怪自己。"
    }
    val refusal = when (sceneStyles.refusalStyle) {
        RefusalStyle.DIRECT -> "不好意思，这个我这边做不了。"
        RefusalStyle.ALTERNATIVE -> "我这边没法直接帮你做，不过可以给你一个参考模板。"
        RefusalStyle.INDIRECT -> "这个我可能不太方便答应，怕后面顾不上。"
        RefusalStyle.REASON -> "不好意思，我最近事情排得比较满，这次可能帮不上。"
    }
    val apology = when (sceneStyles.apologyStyle) {
        ApologyStyle.DIRECT -> "对不起，这次是我没处理好。"
        ApologyStyle.EXPLANATION -> "不好意思，刚才临时有事耽搁了，让你久等了。"
        ApologyStyle.HUMOROUS -> "我来晚了，先认错，今天听你安排。"
        ApologyStyle.AVOIDANT -> "久等了，我们先进去吧。"
        ApologyStyle.COMPENSATION -> "真不好意思，让你等这么久。这次我来安排。"
    }
    val conflict = when (sceneStyles.conflictStyle) {
        ConflictStyle.COMPETING -> "这个点我需要说明一下，情况并不是这样。"
        ConflictStyle.ASSERTIVE_SOFT -> "我理解你着急，不过这里我也补充一下实际情况。"
        ConflictStyle.AVOIDING -> "我们先别在这里争，晚点冷静一点再说。"
        ConflictStyle.ACCOMMODATING -> "我先调整一下，别因为这个影响我们沟通。"
        ConflictStyle.COLLABORATING -> "我们先把各自的难处说清楚，再一起找个方案。"
    }
    return mapOf(
        "comfort" to comfort,
        "refusal" to refusal,
        "apology" to apology,
        "conflict" to conflict
    )
}
