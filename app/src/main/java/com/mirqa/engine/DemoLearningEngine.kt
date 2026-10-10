package com.mirqa.engine

/**
 * First offline content pack. This is a small demonstration engine, not yet
 * the complete adaptive diagnosis/mastery engine described in the product spec.
 */
object DemoLearningEngine {
    val questions = listOf(
        LearningQuestion(
            id = "vector-001",
            conceptId = "vector-basics",
            conceptTitle = "المتجهات والقياس",
            prompt = "أي العبارات التالية تصف كمية متجهة؟",
            options = listOf(
                "كتلة جسم مقدارها ٥ كيلوجرامات",
                "درجة حرارة مقدارها ٢٥ درجة",
                "إزاحة مقدارها ٥ أمتار نحو الشرق",
                "زمن مقداره ١٠ ثوانٍ"
            ),
            correctOption = 2,
            explanation = "الإزاحة لها مقدار واتجاه، لذلك هي كمية متجهة. الكتلة ودرجة الحرارة والزمن كميات قياسية."
        ),
        LearningQuestion(
            id = "motion-001",
            conceptId = "motion-basics",
            conceptTitle = "وصف الحركة",
            prompt = "تحرك جسم ٣ أمتار شرقًا ثم ٣ أمتار غربًا. ما مقدار إزاحته النهائية؟",
            options = listOf("٦ أمتار شرقًا", "٦ أمتار غربًا", "٣ أمتار", "صفر متر"),
            correctOption = 3,
            explanation = "الإزاحة تعتمد على الفرق بين الموضع النهائي والابتدائي. عاد الجسم إلى موضع البداية، لذا الإزاحة تساوي صفرًا."
        ),
        LearningQuestion(
            id = "algebra-001",
            conceptId = "equations",
            conceptTitle = "الجبر الأساسي",
            prompt = "إذا كان x + 4 = 9، فما قيمة x؟",
            options = listOf("٤", "٥", "٩", "١٣"),
            correctOption = 1,
            explanation = "نطرح ٤ من الطرفين، فنحصل على x = 5."
        )
    )
}
