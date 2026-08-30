package com.socialfirstaidkit.feature.socialcopy.model

const val DEFAULT_SOCIAL_COPY_STYLE_ID = "natural"
const val DEFAULT_MOMENT_IMAGE_STYLE_ID = "hand_drawn"

data class SocialCopyStyleOption(
    val id: String,
    val name: String,
    val description: String,
    val order: Int = 0,
    val isSystemPreset: Boolean = false,
    val systemKey: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

data class SocialCopyStyleConfig(
    val styleName: String,
    val styleDescription: String
)

data class MomentImageStyleOption(
    val id: String,
    val name: String,
    val description: String,
    val prompt: String,
    val order: Int = 0,
    val isSystemPreset: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

fun defaultSocialCopyStyleOptions(): List<SocialCopyStyleOption> {
    return listOf(
        SocialCopyStyleOption(
            id = DEFAULT_SOCIAL_COPY_STYLE_ID,
            name = "自然版",
            description = "像本人随手记录，轻轻带过画面和心情，真实、不用力。",
            order = 0,
            isSystemPreset = true,
            systemKey = DEFAULT_SOCIAL_COPY_STYLE_ID
        ),
        SocialCopyStyleOption(
            id = "relaxed",
            name = "轻松版",
            description = "语气松弛，带一点生活里的小开心，适合日常分享。",
            order = 1,
            isSystemPreset = true,
            systemKey = "relaxed"
        ),
        SocialCopyStyleOption(
            id = "literary",
            name = "文艺版",
            description = "更有画面感和留白感，但不堆砌辞藻，适合氛围照片。",
            order = 2,
            isSystemPreset = true,
            systemKey = "literary"
        ),
        SocialCopyStyleOption(
            id = "formal",
            name = "正式版",
            description = "表达完整、稳妥、有分寸，适合工作学习、活动记录和重要场合。",
            order = 3,
            isSystemPreset = true,
            systemKey = "formal"
        ),
        SocialCopyStyleOption(
            id = "humorous",
            name = "幽默版",
            description = "轻微俏皮、有梗但不过火，像朋友间自然开个小玩笑。",
            order = 4,
            isSystemPreset = true,
            systemKey = "humorous"
        ),
        SocialCopyStyleOption(
            id = "daizhou_li",
            name = "大周礼",
            description = "古文感更强的雅正文风，用词克制、端庄、守礼，但不固定开头。",
            order = 5,
            isSystemPreset = true,
            systemKey = "daizhou_li"
        )
    )
}
fun defaultMomentImageStyleOptions(): List<MomentImageStyleOption> {
    return listOf(
        MomentImageStyleOption(
            id = "hand_drawn",
            name = "手账式手绘标注图",
            description = "保留原图真实质感，用少量白色手写线条、圈选和生活化短备注做轻加工。",
            prompt = """
                重要前提：输入中已经包含原图，请直接基于这张原图进行轻量再设计，不要要求用户重新上传图片，不要凭空重画一张无关图片。

                目标：把原图加工成适合朋友圈发布的“手账式手绘标注图”。效果应像用户在真实照片上用白色手写笔随手做了几处生活备注，而不是商业海报、菜谱、广告图或表情包。

                原图保留：必须保留原图主体、真实场景、构图关系、人物/食物/物品数量和位置关系；不要改变菜品、人物、地点、物体身份，不要新增不存在的人、动物、建筑、品牌标识或大面积背景。

                画面处理：只做轻微提亮、白平衡、清晰度增强和柔和色调统一，保持真实手机摄影质感。不要把照片变成插画、漫画、3D、海报或过度滤镜图。

                手绘元素：使用统一的白色或浅奶油色手写笔风格，添加 2-4 处小元素：手绘圈线、箭头、下划线、小波浪线、小星星、小笑脸、爱心或短横线。线条要细、自然、有手写的不规则感，不要粗黑涂鸦，不要像教学截图。

                标注策略：先识别画面中最明确的 2-4 个真实主体，例如主菜、饮品、人物动作、桌面小物、风景重点或生活物件；每个标注必须指向真实存在的画面内容，不能编造地点、品牌、价格、营养结论、人物关系或事件。

                文字规则：最多添加 2-4 处中文短标注，每处不超过 8 个字。语气要像朋友圈随手记录，轻松但克制，例如“热乎乎”“今天这口”“刚好”“舒服一点”。避免“神器”“爆款”“必吃”“绝了”“超值”“营养担当”等营销、夸张或网红词。

                排版约束：标注放在边缘、角落、餐盘外侧、天空/桌面/墙面等留白处；箭头和圈线可以轻轻指向主体，但不能遮挡食物、人脸、二维码、商品信息、屏幕文字或画面重点。整体留白要舒服，宁少勿多。

                禁止事项：不要添加水印、Logo、二维码、联系方式、夸张标题、促销文案；不要生成明显错误或难以辨认的文字；不要让装饰喧宾夺主。
            """.trimIndent(),
            order = 0,
            isSystemPreset = true
        ),
        MomentImageStyleOption(
            id = "fresh_diary",
            name = "清爽日记风",
            description = "留白、细线和轻量文字排版，让照片像干净的生活日记页。",
            prompt = """
                重要前提：输入中已经包含原图，请直接基于这张原图加工，不要要求用户重新上传图片，也不要脱离原图重新创作。

                目标：把原图整理成“清爽日记风”朋友圈分享图，像一页安静、干净、有记录感的生活日记。画面要自然耐看，不要像商业模板或宣传海报。

                原图保留：保留原图主体、真实场景和关键细节，不改变人物五官、食物形态、物品结构、地点特征和主体数量。照片本身仍然是主角。

                画面处理：轻微提亮，降低杂乱背景的视觉干扰，统一成低饱和、柔和、干净的色调；保留真实光影和手机摄影质感，不要磨皮过度、虚化过度或大幅重绘。

                版式设计：可在照片上方、下方或边缘生成柔和留白区域，加入细线分隔、小圆点、轻量纸张纹理、浅色标签条或很轻的胶片感边框。布局以手机竖图为主，主体居中稳定，边距均衡。不要加入日期栏、年份、月日、时刻或任何类似日期的数字组合。

                文字元素：最多添加 1 行日记式中文短句，中文为主，不超过 12 个字；优先使用“今日记录”“日常记录”“记录一下”“刚好这样”等不含具体时间的信息。除非用户补充信息明确给出日期，否则严禁生成任何年份、月份、日期、时刻、数字日期或类似“2024.12.26”的内容。文字要小而清楚，不能抢主体，不能编造地点、品牌、活动名或具体日期。

                氛围要求：白色、浅灰、浅暖色或低饱和底色；整体要有呼吸感、生活感、真实感。适合用户直接发朋友圈，而不是像 App 广告 Banner。

                禁止事项：不要大标题压图，不要强烈渐变，不要大量贴纸，不要营销语，不要水印/Logo/二维码，不要遮挡人脸、食物主体或关键景物；不要生成任何具体年月日、时间、编号式日期或无来源数字。
            """.trimIndent(),
            order = 1,
            isSystemPreset = true
        ),
        MomentImageStyleOption(
            id = "cute_sticker",
            name = "可爱贴纸风",
            description = "添加少量贴纸和小装饰，提升可爱感但不抢走照片主体。",
            prompt = """
                重要前提：输入中已经包含原图，请直接基于原图添加轻量贴纸装饰，不要要求用户补图，不要生成与原图无关的新场景。

                目标：把原图加工成“可爱贴纸风”朋友圈分享图。整体轻松、有亲近感，但仍然像真实生活照片，而不是儿童画、表情包或过度装饰的拼贴图。

                原图保留：保留原图主体、真实比例、人物/食物/物品/风景的核心外观；不要把人物变卡通，不要改变食物种类，不要替换背景，不要新增不存在的动物、人物或大型道具。

                画面处理：可做轻微提亮、柔和暖色调和低对比优化，让画面更亲和；保持照片清晰，不要过度磨皮、过度饱和或大面积模糊。

                贴纸设计：添加 3-6 个小贴纸元素即可，例如小星星、爱心、笑脸、小花、便签、胶带、圆形标签、手账小图标。贴纸要小、克制、颜色协调，优先使用浅色、奶油色、柔和粉/黄/绿等低饱和颜色。

                文字元素：可添加 1-2 个短中文词组，每个不超过 6 个字，例如“好吃”“开心一下”“治愈”“今天很可以”“小确幸”。字体圆润但清楚，不要幼稚到像儿童绘本，不要生成难以辨认的字。

                排版约束：贴纸优先放在角落、边缘和背景空处；绝不遮挡人脸、主菜、重要文字、品牌标识、关键景物和图片核心信息。画面要清爽，装饰只负责加氛围。

                禁止事项：不要堆满贴纸，不要添加节日主题除非原图明显相关，不要生成水印、Logo、二维码、促销标签、价格标签或夸张网红词。
            """.trimIndent(),
            order = 2,
            isSystemPreset = true
        ),
        MomentImageStyleOption(
            id = "highlight_focus",
            name = "突出重点风",
            description = "用轻微景深、光影和局部强调突出主体，保持真实高级但不过度修图。",
            prompt = """
                重要前提：输入中已经包含原图，请直接基于原图做局部优化，不要要求用户重新上传图片，不要把照片改成无关画面。

                目标：把原图加工成“突出重点风”朋友圈分享图，让画面主体更醒目、更有层次，但仍然自然真实，像手机照片经过克制后期，而不是商业大片或科幻滤镜。

                主体判断：根据原图内容识别最重要主体，例如一道菜、饮品、人物动作、桌面物件、书本电脑、旅行地标或风景中心。所有强调都围绕这个主体展开。

                原图保留：保留主体形状、颜色、数量、位置和真实细节，不改变人物五官、食物种类、物体身份或场景背景。主体不能被裁掉、遮挡或重画。

                画面处理：对主体做轻微清晰度、亮度、对比度和质感提升；背景可以做非常轻的景深、暗角、低饱和、柔化或光影过渡，形成自然视觉聚焦。处理要细腻，不能像滤镜开满。

                强调元素：可加入极轻的半透明光晕、局部高亮、细线框、小箭头或一处短中文提示；这些元素必须非常克制，像生活照片的轻后期，不像教程截图、广告标注或赛博特效。

                色彩要求：整体色调干净、真实、略高级；食物保持可食欲，人物肤色自然，风景保留空气感。不要让画面变脏、过暗或过艳。

                禁止事项：不要新增无关物体，不要强行换背景，不要大面积虚化，不要强烈霓虹/科幻/赛博效果，不要水印、Logo、二维码，不要生成大标题或营销口号。
            """.trimIndent(),
            order = 3,
            isSystemPreset = true
        ),
        MomentImageStyleOption(
            id = "minimal_card",
            name = "简约分享卡",
            description = "把照片整理成现代、克制、有留白的朋友圈分享卡。",
            prompt = """
                重要前提：输入中已经包含原图，请直接基于原图制作分享卡，不要要求用户重新上传图片，不要脱离原图生成通用模板。

                目标：把原图加工成“简约分享卡”朋友圈成图，像一张克制、现代、可直接发布的生活内容卡片。重点是把真实照片整理得更有版式感，而不是做成广告海报。

                原图保留：保留照片主体和可识别场景，不改变人物、食物、物品、地点或事件。可适度裁切适配竖版卡片，但主体和关键内容不能被裁掉。

                构图处理：将原图放入简洁卡片版式中，可增加统一边距、圆角、浅色底、轻微投影、细线分隔或干净标题区。整体适配手机竖屏和朋友圈单图发布，画面稳定、有呼吸感。

                信息层级：生成一个很短的中文标题或标签，不超过 10 个字，例如“今日记录”“生活片段”“这一刻不错”“小小日常”。可搭配一条更小的辅助文字、小圆点或编号感装饰，但总文字量要少；除非用户补充信息明确给出日期，否则不要出现任何具体年月日、时间或类似日期的数字。

                视觉风格：白色、浅灰、浅暖色或低饱和背景；字体清楚现代，排版整齐，留白充足。装饰只用细线、小圆点、浅色块或轻量图形，不要复杂拼贴。

                内容约束：文字必须依据原图、图片理解摘要和用户补充信息；不能编造地点、品牌、人物身份、活动名称、价格、功效、评价、具体日期或具体时间。卡片要像真实用户愿意发的生活记录。

                禁止事项：不要营销海报感，不要大标题压图，不要促销标签，不要复杂拼贴，不要过度滤镜，不要水印、Logo、二维码、联系方式或广告式按钮。
            """.trimIndent(),
            order = 4,
            isSystemPreset = true
        )
    )
}
fun List<SocialCopyStyleOption>.sortedForDisplay(): List<SocialCopyStyleOption> {
    return sortedWith(compareBy<SocialCopyStyleOption> { it.order }.thenBy { it.createdAt })
}

fun List<MomentImageStyleOption>.sortedImageStylesForDisplay(): List<MomentImageStyleOption> {
    return sortedWith(compareBy<MomentImageStyleOption> { it.order }.thenBy { it.createdAt })
}

fun buildSocialCopyStyleConfig(
    selectedId: String,
    options: List<SocialCopyStyleOption>
): SocialCopyStyleConfig {
    val selectedOption = options.firstOrNull { it.id == selectedId }
        ?: options.sortedForDisplay().firstOrNull()
        ?: defaultSocialCopyStyleOptions().first()

    return SocialCopyStyleConfig(
        styleName = selectedOption.name,
        styleDescription = if (selectedOption.isSystemPreset) {
            systemSocialCopyStylePrompt(selectedOption)
        } else {
            "请使用“${selectedOption.name}”这种朋友圈创作风格。风格说明：${selectedOption.description}"
        }
    )
}

private fun systemSocialCopyStylePrompt(option: SocialCopyStyleOption): String {
    return when (option.systemKey ?: option.id) {
        DEFAULT_SOCIAL_COPY_STYLE_ID -> """
            风格定位：自然版。像用户本人随手发的一条朋友圈，不刻意、不端着、不像广告文案。
            表达重点：先抓住图片里最真实、最值得记录的一点，可以是当下的感受、画面里的小细节、食物/场景带来的轻微情绪，不要把整张图机械复述一遍。
            语气要求：口语化、轻松、克制，可以有一点点感受，但不要煽情，不要过度夸张，不要出现“治愈了我一整天”“人间值得”这类模板化表达。
            长度要求：每条 8-26 个汉字左右，允许一句话或两句短句，不要长段落。
            内容边界：只写图片和用户补充信息能支持的内容，不编造地点、人物关系、品牌、价格、事件或具体时间。
            输出差异：4 条候选要从不同角度切入，例如画面细节、当下心情、生活记录、轻微感叹，但都保持自然版语气。
        """.trimIndent()

        "relaxed" -> """
            风格定位：轻松版。像朋友之间分享日常，有一点松弛感和小开心，但不过度活泼。
            表达重点：突出“今天还不错”“顺手记录一下”“生活里一个小瞬间”的感觉，可以轻轻带一点语气词，但不要装可爱。
            语气要求：亲近、自然、带一点点俏皮；可以使用少量 emoji，但不是每条都必须有，且最多 1 个。
            长度要求：每条 8-30 个汉字左右，句子短一些，读起来像真实朋友圈而不是小红书标题。
            禁止表达：避免“绝绝子”“冲冲冲”“宝藏”“封神”“氛围感拉满”等网红营销词；不要像打卡文案、探店广告或产品推荐。
            输出差异：4 条候选分别偏轻松记录、小开心、随口感叹、生活碎片，但都不能脱离图片事实。
        """.trimIndent()

        "literary" -> """
            风格定位：文艺版。有画面感、留白感和轻微诗意，但要像真实用户会发的朋友圈，不要像作文、散文或过度抒情。
            表达重点：从光线、颜色、气味、温度、季节感、空间感或心绪中选择一个切口，不要堆满形容词。
            语气要求：含蓄、干净、安静，允许一点比喻，但比喻必须贴合画面，不要玄乎，不要空泛。
            长度要求：每条 10-34 个汉字左右，可以一句短句，也可以两句留白感短句。
            禁止表达：避免“岁月静好”“人间烟火气”“山河远阔”“温柔以待”等高频套话；不要为了文艺而编造地点、天气、人物故事。
            输出差异：4 条候选分别偏画面、情绪、时间感、生活感，整体克制耐看。
        """.trimIndent()

        "formal" -> """
            风格定位：正式版。表达完整、稳妥、有分寸，适合工作学习、活动记录、成果展示、重要场合或不想太私人化的朋友圈。
            表达重点：清楚说明“记录了什么”“这件事的意义/状态/收获”，但不要写成公文、报告或宣传稿。
            语气要求：简洁、礼貌、可信，少用感叹号和 emoji；可以体现感谢、记录、阶段性完成、继续努力等稳妥表达。
            长度要求：每条 14-42 个汉字左右，结构清晰，可一到两句。
            禁止表达：不要夸大成果，不要编造活动名称、机构、奖项、地点、人物身份；不要出现过度官腔、营销腔或鸡汤口号。
            输出差异：4 条候选分别偏记录、总结、感谢、展望，但都要保持自然可信。
        """.trimIndent()

        "humorous" -> """
            风格定位：幽默版。轻微俏皮、有一点梗，但像朋友间自然开的小玩笑，不要为了搞笑而胡编。
            表达重点：从图片里真实存在的反差、状态、食物、动作、环境或用户补充信息里找笑点；笑点要轻，不要冒犯任何人。
            语气要求：口语化、轻松、机灵，可以有一点自嘲或夸张，但夸张只能围绕真实画面展开。
            长度要求：每条 8-30 个汉字左右，短、好读、有记忆点。
            禁止表达：不要低俗、阴阳怪气、人身攻击、刻板印象；不要过度网络热梗，不要把文案写成段子合集或广告标题。
            输出差异：4 条候选分别偏自嘲、反差、小吐槽、轻松感叹，但都要适合直接发朋友圈。
        """.trimIndent()

        "daizhou_li" -> """
            风格定位：大周礼。古文感更强，行文雅正、克制、端庄、守礼，有文言句式和古雅词气，但仍要能作为朋友圈文案阅读。
            起笔要求：不要固定开头，不要使用模板句；可根据画面自然起笔，使用“观此”“是日”“小记”“闲记”“得见”“一隅”“此间”等古雅但不生硬的表达。
            表达重点：从图片内容中提炼一个古文可承载的小主题，例如饮食有节、行止有度、风物可观、学习有恒、相逢有礼、日常可记。语言要像短小古文朋友圈，而不是现代白话套古词。
            语气要求：典雅、从容、含蓄，尽量使用简洁文言句式；可以使用“观”“记”“得”“宜”“可”“亦”“遂”“然”“此间”“一味”“小酌”等词，但不要生僻难懂，不要强行掉书袋。
            长度要求：每条 18-48 个汉字左右，最好一到两句，读起来端正但不绕口。
            内容边界：必须贴合图片事实，不编造人物身份、地点典故、朝代背景或不存在的礼仪场景。
            禁止表达：不要固定起手，不要写成普通文艺风、古风鸡汤或现代白话；不要出现现代营销词、网络热梗、夸张感叹号或 emoji。
        """.trimIndent()

        else -> option.description
    }
}
