package com.devmobile.AIGenerator.data.model

data class PortraitTemplate(
    val id: String,
    val name: String,
    val description: String,
    val category: String,
    val prompt: String,
    val negativePrompt: String,
    val styleStrength: Float,
    val guidanceScale: Float,
    val modelName: String,
    val isPremiumOnly: Boolean = false
)

object TemplateProvider {
    val categories = listOf(
        "Anime",
        "Business",
        "Luxury",
        "Wedding",
        "Fitness",
        "Fantasy",
        "Cyberpunk",
        "TikTok Trend"
    )

    val templates = listOf(
        PortraitTemplate(
            id = "anime_ghibli",
            name = "Studio Ghibli Magic",
            description = "Soft colors, beautiful magical skies, nostalgic vintage anime watercolor styling.",
            category = "Anime",
            prompt = "anime portrait of a person, studio ghibli style, beautiful detailed anime eyes, soft cinematic lighting, high-quality hand-drawn illustration style, colorful background",
            negativePrompt = "photorealistic, blurry, deformed face, low quality, modern CGI, disfigured",
            styleStrength = 0.75f,
            guidanceScale = 7.5f,
            modelName = "Lykon/dreamshaper-xl"
        ),
        PortraitTemplate(
            id = "anime_cyber",
            name = "Cyberpunk Neo-Anime",
            description = "Vibrant glossy anime style fused with futuristic high-contrast lighting details.",
            category = "Anime",
            prompt = "cyberpunk anime key visual portrait, retro sci-fi anime style, neon lighting highlights, sleek cybernetics, masterfully drawn",
            negativePrompt = "photorealistic, ugly face, distorted hands, blurry, bad sketch",
            styleStrength = 0.80f,
            guidanceScale = 8.0f,
            modelName = "cagliostrolab/animagine-xl-3.1"
        ),
        PortraitTemplate(
            id = "biz_ceo",
            name = "Executive CEO Suite",
            description = "Crisp, commanding dark-suit studio portrait with executive background styling.",
            category = "Business",
            prompt = "professional corporate portrait of a CEO, dark elegant business suit, modern office glass windows background, premium soft studio lighting, ultra-detailed photorealistic portrait, 8k resolution",
            negativePrompt = "cartoon, generic 3D, digital drawing, low quality, casual clothes, messy hair",
            styleStrength = 0.85f,
            guidanceScale = 8.0f,
            modelName = "SG161222/RealVisXL_V4.0"
        ),
        PortraitTemplate(
            id = "biz_studio",
            name = "Clean Headshot Studio",
            description = "Professional, friendly grey studio background headshot for resume or LinkedIn.",
            category = "Business",
            prompt = "professional corporate headshot, soft neutral gray studio wall background, smart business attire, pleasant friendly smile, crisp photography, 8k",
            negativePrompt = "unprofessional, casual, saturated background, low quality, painting, drawing",
            styleStrength = 0.80f,
            guidanceScale = 7.5f,
            modelName = "SG161222/RealVisXL_V4.0"
        ),
        PortraitTemplate(
            id = "luxury_yacht",
            name = "Yacht Club Riviera",
            description = "Exquisite luxury setting on a private cruise yacht overlooking stunning costal vistas.",
            category = "Luxury",
            prompt = "luxury leisure portrait of a elegant person on a yacht deck, coastal ocean horizon background, warm golden hour sun glow, sunglasses, rich color harmony, premium editorial photography",
            negativePrompt = "cheap, low resolution, amateur, messy environment, poor lighting",
            styleStrength = 0.80f,
            guidanceScale = 8.0f,
            modelName = "SG161222/RealVisXL_V4.0",
            isPremiumOnly = true
        ),
        PortraitTemplate(
            id = "luxury_gala",
            name = "Red Carpet Gala Event",
            description = "Glitzy spotlight gala look with grand ballroom architecture in the background.",
            category = "Luxury",
            prompt = "haute couture high class portrait, red carpet event background with luxury gala hall lighting, magnificent bokeh sparkles, elegant attire, grand majestic ambiance",
            negativePrompt = "plain, casual background, ugly, poor quality, bad lighting",
            styleStrength = 0.85f,
            guidanceScale = 8.5f,
            modelName = "SG161222/RealVisXL_V4.0",
            isPremiumOnly = true
        ),
        PortraitTemplate(
            id = "wedding_garden",
            name = "Enchanted Garden Nuptials",
            description = "Elegant floral arch bridal backdrop with majestic sunflare accents.",
            category = "Wedding",
            prompt = "romantic wedding portrait, beautiful lush garden with flower arches, stunning lace wedding attire, sunlight flares, soft ethereal cream color style, dreamlike wedding photography",
            negativePrompt = "dark colors, horror, casual clothes, low resolution, bad facial structure",
            styleStrength = 0.75f,
            guidanceScale = 7.0f,
            modelName = "SG161222/RealVisXL_V4.0"
        ),
        PortraitTemplate(
            id = "fit_neon",
            name = "Synthwave Athletics",
            description = "Edgy athletic portrait inside a premium neon-lit fitness studio.",
            category = "Fitness",
            prompt = "cinematic athletic fitness portrait, training wear, premium gym neon-blue light tubes, sweat glisten, strong silhouette glow, athletic posture",
            negativePrompt = "skinny, weak resolution, fat, overexposed, drawing",
            styleStrength = 0.80f,
            guidanceScale = 8.0f,
            modelName = "SG161222/RealVisXL_V4.0"
        ),
        PortraitTemplate(
            id = "fantasy_elven",
            name = "Elven Whispers Realm",
            description = "Ethereal glow, glowing blue forest flowers, long elegant elven details.",
            category = "Fantasy",
            prompt = "mystic elven portrait in an ancient glowing forest, deep fantasy aesthetics, silver leaves crown, glowing ambient lanterns, ethereal fantasy light, unreal engine 5 render look",
            negativePrompt = "modern clothes, low poly, ugly, simple face, bad quality",
            styleStrength = 0.78f,
            guidanceScale = 8.5f,
            modelName = "stabilityai/stable-diffusion-xl-refiner-1.0"
        ),
        PortraitTemplate(
            id = "cyber_neon",
            name = "Akihabara Cyberpunk Glow",
            description = "Electric neon city streets, holographic sign projections, futuristic edge.",
            category = "Cyberpunk",
            prompt = "cyberpunk portrait with neon city lights, holographic sign projections background, cybernetic face details, high-tech jacket, cinematic, purple and cyan color grading, ultra detailed",
            negativePrompt = "nature, trees, blurry, bad anatomy, plain background",
            styleStrength = 0.85f,
            guidanceScale = 8.5f,
            modelName = "stabilityai/stable-diffusion-xl-refiner-1.0"
        ),
        PortraitTemplate(
            id = "trend_old_money",
            name = "TikTok 'Old Money' Classic",
            description = "Pristine beige sweater style aesthetic matching luxury European heritage.",
            category = "TikTok Trend",
            prompt = "old money aesthetic portrait, rich vintage polo club look, clean ivory cashmere clothing, beautiful classic architecture background, analog film camera look, warm organic colors",
            negativePrompt = "neon lights, street wear, digital look, overly sharpened, heavy makeup",
            styleStrength = 0.80f,
            guidanceScale = 7.8f,
            modelName = "SG161222/RealVisXL_V4.0"
        ),
        PortraitTemplate(
            id = "trend_dreamy_retro",
            name = "90s Vintage Yearbook Glow",
            description = "Soft blur visual nostalgia evoking class, warmth, and high school charm.",
            category = "TikTok Trend",
            prompt = "90s high school vintage yearbook portrait, classic soft-focus backdrop panel, subtle aesthetic grain, retro lighting, warm charming nostalgia",
            negativePrompt = "neon, futuristic, ultra-hd sharp, hyper-realistic modern texture, bad anatomy",
            styleStrength = 0.72f,
            guidanceScale = 7.0f,
            modelName = "stabilityai/stable-diffusion-xl-refiner-1.0",
            isPremiumOnly = true
        )
    )
}
