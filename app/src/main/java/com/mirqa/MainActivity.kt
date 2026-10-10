package com.mirqa

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mirqa.engine.DemoLearningEngine
import com.mirqa.engine.LearningQuestion
import com.mirqa.engine.LearningStep

private val Navy = Color(0xFF10243A)
private val Blue = Color(0xFF1B3A5C)
private val Gold = Color(0xFFC79A3E)
private val Canvas = Color(0xFFF5F3EF)
private val Ink = Color(0xFF1D2A36)
private val Muted = Color(0xFF667381)
private val Green = Color(0xFF27765D)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MirqaTheme {
                MirqaApp()
            }
        }
    }
}

@Composable
private fun MirqaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Gold,
            onPrimary = Navy,
            secondary = Blue,
            background = Canvas,
            surface = Color.White,
            onSurface = Ink
        ),
        content = content
    )
}

private enum class Screen { HOME, DIAGNOSIS, LESSON, MAP, PROGRESS }

@Composable
private fun MirqaApp() {
    var screen by remember { mutableStateOf(Screen.HOME) }
    var questionIndex by remember { mutableIntStateOf(0) }
    var correctAnswers by remember { mutableIntStateOf(0) }
    var lastFeedback by remember { mutableStateOf<String?>(null) }
    var selectedAnswer by remember { mutableIntStateOf(-1) }
    var completedLesson by remember { mutableStateOf(false) }

    val questions = DemoLearningEngine.questions
    val currentQuestion = questions[questionIndex.coerceIn(0, questions.lastIndex)]

    Surface(modifier = Modifier.fillMaxSize(), color = Canvas) {
        Column(modifier = Modifier.fillMaxSize()) {
            Header(onHome = { screen = Screen.HOME })
            Box(modifier = Modifier.weight(1f)) {
                when (screen) {
                    Screen.HOME -> HomeScreen(
                        onStart = { screen = Screen.DIAGNOSIS; questionIndex = 0; correctAnswers = 0; lastFeedback = null; selectedAnswer = -1 },
                        onMap = { screen = Screen.MAP },
                        onProgress = { screen = Screen.PROGRESS }
                    )
                    Screen.DIAGNOSIS -> DiagnosisScreen(
                        question = currentQuestion,
                        questionNumber = questionIndex + 1,
                        totalQuestions = questions.size,
                        selectedAnswer = selectedAnswer,
                        feedback = lastFeedback,
                        onSelect = { selectedAnswer = it },
                        onSubmit = {
                            if (selectedAnswer == currentQuestion.correctOption) correctAnswers++
                            lastFeedback = if (selectedAnswer == currentQuestion.correctOption) {
                                "إجابة صحيحة. سجّل مرقاة دليلًا على فهم هذا المفهوم."
                            } else {
                                "ليست الإجابة الصحيحة. راجع التفسير، ثم تابع؛ الخطأ يساعدنا على تحديد ما يحتاج إلى تعلم."
                            }
                        },
                        onNext = {
                            if (questionIndex < questions.lastIndex) {
                                questionIndex++
                                selectedAnswer = -1
                                lastFeedback = null
                            } else {
                                screen = Screen.LESSON
                            }
                        }
                    )
                    Screen.LESSON -> LessonScreen(
                        completed = completedLesson,
                        onComplete = { completedLesson = true },
                        onMap = { screen = Screen.MAP },
                        onHome = { screen = Screen.HOME }
                    )
                    Screen.MAP -> KnowledgeMapScreen(onBack = { screen = Screen.HOME })
                    Screen.PROGRESS -> ProgressScreen(
                        correct = correctAnswers,
                        total = if (lastFeedback == null) 0 else questionIndex + 1,
                        lessonCompleted = completedLesson,
                        onBack = { screen = Screen.HOME }
                    )
                }
            }
            BottomNavigation(
                current = screen,
                onHome = { screen = Screen.HOME },
                onMap = { screen = Screen.MAP },
                onProgress = { screen = Screen.PROGRESS }
            )
        }
    }
}

@Composable
private fun Header(onHome: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Navy)
            .clickable { onHome() }
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text("مِرقاة", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text("M I R Q A  ·  محرك التعلّم", color = Gold, fontSize = 11.sp)
        }
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(Color(0xFF203A53)),
            contentAlignment = Alignment.Center
        ) {
            Text("م", color = Gold, fontSize = 23.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun HomeScreen(onStart: () -> Unit, onMap: () -> Unit, onProgress: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(Modifier.height(4.dp))
        Text("أنت تحدد الوجهة،", fontSize = 27.sp, fontWeight = FontWeight.Bold, color = Navy)
        Text("ومرقاة تكتشف الطريق.", fontSize = 27.sp, fontWeight = FontWeight.Bold, color = Gold)
        Text(
            "تعلّم يبدأ من معرفتك الفعلية، لا من افتراض أنك تبدأ من الصفر.",
            fontSize = 15.sp,
            color = Muted,
            lineHeight = 25.sp
        )

        Card(
            colors = CardDefaults.cardColors(containerColor = Navy),
            shape = RoundedCornerShape(22.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("وجهتك الأولى", color = Gold, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Text("مدخل إلى النسبية العامة", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text(
                    "ابدأ بتشخيص قصير للمتطلبات الأساسية، ثم تابع إلى خطوة تعلم مناسبة.",
                    color = Color(0xFFD8E0E7),
                    fontSize = 14.sp,
                    lineHeight = 23.sp
                )
                Button(
                    onClick = onStart,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Navy)
                ) {
                    Text("اكتشف نقطة بدايتي", fontWeight = FontWeight.Bold)
                }
            }
        }

        Text("كيف يعمل مرقاة؟", color = Navy, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        FeatureCard("١", "يفهم المتطلبات", "يربط الهدف بالمفاهيم التي تساعد على الوصول إليه.")
        FeatureCard("٢", "يختبر ما تحتاجه فقط", "أسئلة تشخيصية قصيرة تساعد على اختيار مسار أفضل.")
        FeatureCard("٣", "يتابع الأدلة", "الإجابة الصحيحة بداية دليل، وليست دائمًا إثباتًا للإتقان.")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = onMap, modifier = Modifier.weight(1f)) { Text("خريطة المعرفة") }
            OutlinedButton(onClick = onProgress, modifier = Modifier.weight(1f)) { Text("تقدمي") }
        }
        Text(
            "نسخة تجريبية أولية · تعمل دون حساب أو اتصال بالشبكة",
            color = Muted,
            fontSize = 12.sp,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun FeatureCard(number: String, title: String, description: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier.size(34.dp).clip(CircleShape).background(Color(0xFFE8DFC9)),
            contentAlignment = Alignment.Center
        ) {
            Text(number, color = Navy, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, color = Ink, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Text(description, color = Muted, fontSize = 13.sp, lineHeight = 20.sp)
        }
    }
}

@Composable
private fun DiagnosisScreen(
    question: LearningQuestion,
    questionNumber: Int,
    totalQuestions: Int,
    selectedAnswer: Int,
    feedback: String?,
    onSelect: (Int) -> Unit,
    onSubmit: () -> Unit,
    onNext: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("التشخيص التمهيدي", color = Gold, fontWeight = FontWeight.Bold)
        Text("السؤال $questionNumber من $totalQuestions", color = Muted, fontSize = 13.sp)
        Box(Modifier.fillMaxWidth().height(5.dp).clip(CircleShape).background(Color(0xFFE3E5E7))) {
            Box(Modifier.fillMaxWidth(questionNumber.toFloat() / totalQuestions).height(5.dp).clip(CircleShape).background(Gold))
        }
        Text(question.conceptTitle, color = Blue, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Text(question.prompt, color = Navy, fontSize = 22.sp, fontWeight = FontWeight.Bold, lineHeight = 32.sp)
        question.options.forEachIndexed { index, option ->
            val chosen = selectedAnswer == index
            Card(
                modifier = Modifier.fillMaxWidth().clickable(enabled = feedback == null) { onSelect(index) },
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (chosen) Color(0xFFEDE3CB) else Color.White
                ),
                border = androidx.compose.foundation.BorderStroke(
                    width = if (chosen) 1.5.dp else 1.dp,
                    color = if (chosen) Gold else Color(0xFFE0E3E6)
                )
            ) {
                Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(28.dp).clip(CircleShape).background(if (chosen) Gold else Color(0xFFE9EDF0)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(('أ'.code + index).toChar().toString(), color = Navy, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(option, color = Ink, fontSize = 15.sp, lineHeight = 22.sp)
                }
            }
        }
        if (feedback != null) {
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFE7F1EB))) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(feedback, color = Green, fontWeight = FontWeight.SemiBold, lineHeight = 22.sp)
                    Text(question.explanation, color = Ink, fontSize = 13.sp, lineHeight = 21.sp)
                }
            }
            Button(onClick = onNext, modifier = Modifier.fillMaxWidth()) {
                Text(if (questionNumber < totalQuestions) "السؤال التالي" else "تابع إلى خطوة التعلم")
            }
        } else {
            Button(
                onClick = onSubmit,
                enabled = selectedAnswer >= 0,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Navy)
            ) { Text("تحقق من الإجابة", color = Color.White) }
        }
        TextButton(onClick = onNext, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text("تجاوز هذا السؤال")
        }
    }
}

@Composable
private fun LessonScreen(
    completed: Boolean,
    onComplete: () -> Unit,
    onMap: () -> Unit,
    onHome: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("خطوتك التعليمية التالية", color = Gold, fontWeight = FontWeight.Bold)
        Text("ما معنى المتجه؟", color = Navy, fontSize = 27.sp, fontWeight = FontWeight.Bold)
        Text(
            "المتجه كمية لها مقدار واتجاه. أما الكمية القياسية فلها مقدار فقط. هذا الفرق مهم في وصف الحركة والقوى، وهو أحد المفاهيم التي قد تحتاجها في مسارات الفيزياء.",
            color = Ink, fontSize = 16.sp, lineHeight = 28.sp
        )
        Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("مثال", color = Gold, fontWeight = FontWeight.Bold)
                Text("قولنا «تحرك الجسم ٥ أمتار شرقًا» يحدد مقدارًا واتجاهًا؛ أما «كتلته ٥ كيلوجرامات» فلا يحدد اتجاهًا.", color = Ink, lineHeight = 25.sp)
            }
        }
        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F1EB))) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(if (completed) "سُجل إكمال الدرس في الجلسة الحالية" else "كيف نثبت الفهم؟", color = Green, fontWeight = FontWeight.Bold)
                Text(
                    if (completed) "يمكنك متابعة خريطة المعرفة أو الرجوع إلى الصفحة الرئيسية."
                    else "إكمال القراءة وحده لا يثبت الإتقان. في الخطوة التالية سنضيف مهمة تطبيقية تقيس قدرتك على التمييز بين النوعين.",
                    color = Ink, lineHeight = 23.sp
                )
            }
        }
        Button(onClick = onComplete, enabled = !completed, modifier = Modifier.fillMaxWidth()) {
            Text(if (completed) "تم إكمال الدرس" else "سجّل إكمال الدرس")
        }
        OutlinedButton(onClick = onMap, modifier = Modifier.fillMaxWidth()) { Text("استكشف خريطة المعرفة") }
        TextButton(onClick = onHome, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("العودة للرئيسية") }
    }
}

@Composable
private fun KnowledgeMapScreen(onBack: () -> Unit) {
    val concepts = listOf(
        LearningStep("المتجهات", "مفهوم تمهيدي", Gold),
        LearningStep("الحركة", "يستفيد من فهم المتجهات", Blue),
        LearningStep("القوى", "يرتبط بالحركة", Blue),
        LearningStep("الجاذبية", "مفهوم فيزيائي لاحق", Green),
        LearningStep("مدخل إلى النسبية", "الهدف التعليمي", Navy)
    )
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("خريطة المعرفة", color = Navy, fontSize = 25.sp, fontWeight = FontWeight.Bold)
        Text("خريطة تجريبية مبسطة؛ العلاقات هنا توضيحية وليست منهجًا علميًا كاملًا.", color = Muted, lineHeight = 22.sp)
        concepts.forEachIndexed { index, concept ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(13.dp).clip(CircleShape).background(concept.color))
                    if (index < concepts.lastIndex) Box(Modifier.width(2.dp).height(28.dp).background(Color(0xFFD6D9DC)))
                }
                Spacer(Modifier.width(12.dp))
                Card(modifier = Modifier.weight(1f), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Column(Modifier.padding(13.dp)) {
                        Text(concept.title, color = Navy, fontWeight = FontWeight.Bold)
                        Text(concept.subtitle, color = Muted, fontSize = 12.sp)
                    }
                }
            }
        }
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("العودة للرئيسية") }
    }
}

@Composable
private fun ProgressScreen(correct: Int, total: Int, lessonCompleted: Boolean, onBack: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("دليل تقدمك", color = Navy, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Text("تعرض هذه الشاشة بيانات الجلسة الحالية فقط في هذا الإصدار التجريبي.", color = Muted, lineHeight = 23.sp)
        Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("الإجابات الصحيحة المسجلة", color = Muted)
                Text("$correct", color = Navy, fontSize = 34.sp, fontWeight = FontWeight.Bold)
                Divider()
                Text("الدرس مكتمل: ${if (lessonCompleted) "نعم" else "ليس بعد"}", color = Ink)
                Text("الأسئلة التي تم الرد عليها: $total", color = Ink)
            }
        }
        Text("لا تمثل هذه الأرقام درجة إتقان علمية؛ ما زلنا نطور نموذج الأدلة والحفظ الدائم.", color = Muted, fontSize = 13.sp, lineHeight = 22.sp)
        Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("العودة للرئيسية") }
    }
}

@Composable
private fun BottomNavigation(current: Screen, onHome: () -> Unit, onMap: () -> Unit, onProgress: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().background(Color.White).padding(horizontal = 8.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        NavItem("الرئيسية", current == Screen.HOME, onHome)
        NavItem("خريطة المعرفة", current == Screen.MAP, onMap)
        NavItem("التقدم", current == Screen.PROGRESS, onProgress)
    }
}

@Composable
private fun NavItem(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        label,
        modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable { onClick() }.padding(horizontal = 10.dp, vertical = 9.dp),
        color = if (selected) Gold else Muted,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        fontSize = 12.sp
    )
}
