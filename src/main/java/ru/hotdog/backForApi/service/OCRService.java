package ru.hotdog.multicam_api.service;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import ru.hotdog.multicam_api.dto.DetectedObj;
import ru.hotdog.multicam_api.dto.OCRResponse;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.core.type.TypeReference;

import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class OCRService {

    // Промпты для моделей. Тут вся инструкция по поведению: как именно извлекать формулы, в каком формате ждать ответ по еде и как оформлять решение
    //'image' if you see nothing interesting just photos "

    private static final String SYSTEM_PROMPT = """
            You are a precise visual analysis engine. Obey these rules without exception:
            1. ACCURACY FIRST: Only report what you can see with certainty. If unsure — omit, never guess.
            2. FORMAT STRICT: Respond ONLY in the exact format the user specifies. No preambles, no apologies, no commentary.
            3. NO HALLUCINATION: Do not invent brand names, colors, text, or attributes you cannot clearly see.
            4. NO MARKDOWN WRAPPING: Never wrap JSON output in ```json blocks unless explicitly told to.
            5. SCOPE: Focus on the primary subject. Ignore backgrounds, surfaces, and environmental context.
            """;

    private static final String CLASSIFIER_PROMPT = """
            Classify this image into exactly ONE category. Output ONLY the single category word — nothing else.
            
            Categories:
            - 'math'    : Mathematical equations, formulas, graphs, geometric figures, physics problems
            - 'mixed'   : Text combined with mathematical formulas or diagrams
            - 'text'    : Printed or handwritten text without math (documents, notes, signs)
            - 'food'    : Food items, meals, dishes, beverages, ingredients on a plate/surface
            - 'objects' : A clearly identifiable physical product or item (electronics, clothing, toys, tools, accessories, household items)
            - 'image'   : People, animals, nature, abstract scenes, architecture, art
            - 'noise'   : A table surface, floor, wall, empty background, blurry or unrecognizable content
            
            RULE: If the image is primarily background or surface with no clear target object — output 'noise'.
            Output ONE word only. No punctuation.
            """;

    private static final String EXTRACT_PROMPT = """
            You are a precise OCR assistant.
            Transcribe the mathematical problem from the image into LaTeX format exactly.
            - Use tg, ctg notation (Soviet style).
            - Do NOT solve. Just transcribe.
            - Output ONLY the LaTeX code. Nothing else.
            """;


    private static final String MATH_SOLVER_PROMPT = """
            You are a strict Mathematics Professor. Solve the following problem step-by-step.
            RULES:
            1. OUTPUT LANGUAGE: Russian.
            2. NOTATION: Use tg/ctg (Soviet style).
            3. Show every step clearly.
            4. Check ODZ (domain).
            5. Final answer in \\boxed{}.
            
            PROBLEM TO SOLVE:
            """;

    private static final String OCR_PROMPT = """
            Transcribe all visible text from the image.
            - Keep plain text as plain text.
            - Convert all formulas and equations to LaTeX notation.
            - Output ONLY valid Markdown. No commentary.
            """;

    private static final String DESCRIPTION_PROMPT = """
            Follow this structured plan: 
            1. General Description. 
            2. Detailed Analysis (colors, shapes). 
            3. Brands/Text. 
            Language: RUSSIAN. Be concise.
            """;


    private static final String FOOD_PROMPT = """
            Analyze the food in this image as a nutritionist.
            Estimate nutritional values for the ENTIRE visible portion.
            Return ONLY this JSON object with integer values. No markdown, no explanation:
            {"mass": 0, "calories": 0, "proteins": 0, "fats": 0, "carbs": 0}
            """;


    private static final String DETECT_PROMPT = """
            Detect and list the main physical objects in this image.
            
            IGNORE completely (do not include in output):
            table, desk, floor, wall, ceiling, background, shadow, cloth, fabric,
            tablecloth, surface, wood, carpet, shelf, counter, plate (if empty), tray.
            
            Return ONLY a raw JSON array. No ```json blocks, no explanation.
            Format: [{"label": "English product name", "bbox": {"x": 0.1, "y": 0.1, "width": 0.2, "height": 0.2}}]
            
            Rules:
            - Coordinates normalized 0.0–1.0 (x,y = top-left corner of bounding box)
            - Maximum 5 objects
            - Use concise English product names (e.g. "wireless headphones", "ceramic mug", "running shoes")
            - If no meaningful objects found: return []
            """;


    private static final String MATH_PROMPT =
            """
            You are a strict Academic Tutor specializing in Mathematics (Algebra, Calculus, Trig) and Physics.
              Your goal is 100% accuracy. You must assume the user is a student who needs to see EVERY intermediate step.
        
              ═══════════════════════════════════════════
              GLOBAL CONSTRAINTS
              ═══════════════════════════════════════════
              1. LANGUAGE: Output must be entirely in RUSSIAN.
              2. NOTATION: Use Soviet notation: 'tg' for tangent, 'ctg' for cotangent. NEVER use 'tan' or 'cot'.
              3. ATOMIC STEPS: Perform only ONE logical or algebraic operation per step. Do not combine simplification and substitution in one line.
              4. VERBOSITY: Do not summarize. Show full intermediate expressions.\s
                 - BAD: "Simplify to get x=5"
                 - GOOD: Show the equation, then show the simplified equation, then the result.
        
              ═══════════════════════════════════════════
              REASONING PROTOCOL (INTERNAL)
              ═══════════════════════════════════════════
              Before generating the final LaTeX math block for any step, you must mentally verify:
              - Are signs (+/-) correct?
              - Did I miss a coefficient (like 1/3 or sqrt(3))?
              - Is the domain (ОДЗ) respected?
        
              ═══════════════════════════════════════════
              OUTPUT STRUCTURE
              ═══════════════════════════════════════════
              Follow this exact Markdown structure:
        
              ### Анализ задачи
              Briefly describe what is given and what is needed. If there is an image, describe the visible graph/formula text.
        
              ### ОДЗ (Domain)
              Determine the valid domain for x. If none, write "ОДЗ: x ∈ R".
        
              ### План решения
              List the strategy (e.g., "1. Group terms. 2. Use Pythagorean identity. 3. Solve quadratic.").
        
              ### Решение
              Execute the plan step-by-step.\s
              Format for each step:
              **Шаг N:** [Name of operation]
              [Explanation in Russian]
              $$ [LaTeX Math Block] $$
        
              ### Проверка
              Substitute the result back into the original expression to verify correctness.
        
              ### Ответ
              Final answer clearly stated.
              $$ \\boxed{ [Answer] } $$
        
              ═══════════════════════════════════════════
              CRITICAL REMINDERS
              ═══════════════════════════════════════════
              - For Trig: $\\sin^2 x + \\cos^2 x = 1$.
              - For Physics: Show formula -> Show substitution with units -> Show result.
              - Never skip the "Plan" section. It grounds your logic.      
                """;

    private final String vllmApiUrl = "http://172.22.168.73:8000/v1/chat/completions";

    @Value("${deepseek.api.key:}")
    private String deepSeekApiKey;

    @Value("${deepseek.api.model:deepseek-chat}")
    private String deepSeekModel;

    @Value("${llm.local.model:Qwen/Qwen2.5-VL-7B-Instruct-AWQ}")
    private String localModel;

    @Value("${llm.local.temperature:0.01}")
    private double localTemperature;
    private final String deepSeekApiUrl = "https://api.deepseek.com/v1/chat/completions";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final WebClient localWebClient;
    private final WebClient deepSeekWebClient;
    private final ExecutorService executor = Executors.newFixedThreadPool(10);
    private final ObjectFilterService objectFilterService;
    private final ProductSearchService productSearchService;


    public OCRService(WebClient.Builder webClientBuilder) {


        HttpClient localHttpClient = HttpClient.create()
                // Выставил большие таймауты, потому что нейронки на тяжелых изображениях долго думают
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 100000)
                .responseTimeout(Duration.ofSeconds(1800))
                .doOnConnected(conn -> conn
                        .addHandlerLast(new ReadTimeoutHandler(1800, TimeUnit.SECONDS))
                        .addHandlerLast(new WriteTimeoutHandler(1800, TimeUnit.SECONDS)));

        this.localWebClient = webClientBuilder.baseUrl("http://172.22.168.73:8000").build();

        HttpClient deepSeekHttpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10000)
                .responseTimeout(Duration.ofSeconds(120));

        this.deepSeekWebClient = webClientBuilder
                .baseUrl("https://api.deepseek.com")
                .build();

        this.objectFilterService = objectFilterService;
        this.productSearchService = productSearchService;
    }

    public CompletableFuture<OCRResponse> processRequest(byte[] imageBytes) {
        log.info("Начинаем обработку: отправка в классификатор...");

        // Сначала определяем тип контента. Это экономит токены и позволяет юзать разные модели для разных задач.
        return sendTolVllm(imageBytes, CLASSIFIER_PROMPT, 128)
                .thenComposeAsync(category -> {
                    String cleanCategory = category.toLowerCase().trim().replaceAll("[^a-z]", "");

                    if (cleanCategory.contains("math") || cleanCategory.contains("mixed")) {
                        cleanCategory = "math";
                    } else if (cleanCategory.contains("food")) {
                        cleanCategory = "food";
                    }

                    log.info("Классификация: {}", cleanCategory);

                    return switch (cleanCategory) {

                        case "math", "mixed" ->
                                sendTolVllm(imageBytes, EXTRACT_PROMPT, 1024)
                                        .thenComposeAsync(extractedLaTeX -> {
                                            log.info("Извлекли задачу: {}", extractedLaTeX);
                                            return callMathSolver(extractedLaTeX);
                                        }, executor)
                                        .thenApply(solution -> {
                                            OCRResponse r = new OCRResponse();
                                            r.setTag("math");
                                            r.setSolution(solution);
                                            r.setResult(solution);
                                            return r;
                                        })
                                        .exceptionally(ex -> {
                                            //отправка на локалку если что-то с api дипсика
                                            if (ex.getMessage().contains("Payment Required") || ex.getMessage().contains("облачная модель")) {
                                                log.warn("⚠️ Облако недоступно, пробую решить локально...");
                                                return sendTolVllm(imageBytes, MATH_PROMPT + "\n\nSolve locally if possible.", 4096)
                                                        .thenApply(res -> {
                                                            OCRResponse r = new OCRResponse();
                                                            r.setTag("math");
                                                            r.setResult(res + "\n\n⚠️ *Решено локальной моделью*");
                                                            return r;
                                                        }).join();
                                            }
                                            return null;
                                        });

                        case "text" -> sendTolVllm(imageBytes, OCR_PROMPT, 1024)
                                .thenApply(res -> {
                                    OCRResponse r = new OCRResponse();
                                    r.setTag("text");
                                    r.setResult(res);
                                    return r;
                                });

                        case "food" -> sendTolVllm(imageBytes, FOOD_PROMPT, 512)
                                .thenApply(jsonStr -> {
                                    try {
                                        String cleanJson = jsonStr.replaceAll("```json\\s*", "").replaceAll("```", "").trim();
                                        OCRResponse r = objectMapper.readValue(cleanJson, OCRResponse.class);
                                        r.setTag("food");
                                        return r;
                                    } catch (Exception e) {
                                        log.error("Ошибка парсинга еды", e);
                                        OCRResponse err = new OCRResponse();
                                        err.setTag("food");
                                        err.setResult("Ошибка разбора данных о еде");
                                        return err;
                                    }
                                });

                        case "objects" -> sendTolVllm(imageBytes, DETECT_PROMPT, 1024)
                                .thenApply(jsonStr -> {
                                    try {
                                        String cleanJson = jsonStr.replaceAll("```json\\s*", "").replaceAll("```", "").trim();
                                        List<DetectedObj> objs = objectMapper.readValue(cleanJson, new TypeReference<List<DetectedObj>>() {});
                                        OCRResponse r = new OCRResponse();
                                        r.setTag("objects");
                                        r.setDetectedObjs(objs);
                                        return r;
                                    } catch (Exception e) {
                                        log.error("Ошибка парсинга объектов", e);
                                        OCRResponse err = new OCRResponse();
                                        err.setTag("objects");
                                        err.setResult("Ошибка разбора данных о объектах");
                                        return err;
                                    }
                                });

                        case "image" -> sendTolVllm(imageBytes, DESCRIPTION_PROMPT, 1024)
                                .thenApply(res -> {
                                    OCRResponse r = new OCRResponse();
                                    r.setTag("image");
                                    r.setDescription(res);
                                    r.setResult(res);
                                    return r;
                                });

                        default -> CompletableFuture.completedFuture(new OCRResponse());
                    };
                }, executor)
                .exceptionally(ex -> {
                    log.error("Ошибка в пайплайне", ex);
                    OCRResponse errorRes = new OCRResponse();
                    errorRes.setResult("Ошибка обработки: " + ex.getMessage());
                    return errorRes;
                });
    }

    private CompletableFuture<String> sendTolVllm(byte[] imageBytes, String prompt, int maxTokens) {
        // Кодируем картинку в Base64, чтобы скинуть её в JSON для vLLM.
        String base64Image = "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(imageBytes);

        Map<String, Object> requestBody = Map.of(
                "model", "Qwen/Qwen2.5-VL-7B-Instruct-AWQ",
                "messages", List.of(
                        Map.of("role", "user", "content", List.of(
                                Map.of("type", "text", "text", prompt),
                                Map.of("type", "image_url", "image_url", Map.of("url", base64Image))
                        ))
                ),
                "temperature", 0.01,
                "max_tokens", maxTokens
        );

        return localWebClient.post()
                .uri("/v1/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(Map.class)
                .map(this::extractContentFromResponse)
                .toFuture();
    }

    private CompletableFuture<String> callMathSolver(String problemText) {
        Map<String, Object> requestBody = Map.of(
                "model", "deepseek-chat",
                "messages", List.of(
                        Map.of("role", "user", "content", MATH_SOLVER_PROMPT + "\n\n" + problemText)
                ),
                "temperature", 0.1,
                "max_tokens", 4096
        );

        return deepSeekWebClient.post()
                .uri("/v1/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer sk-b1687517c01f47bdac606704de5fd311")
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(Map.class)
                .map(this::extractContentFromResponse)
                .toFuture();
    }

    private String extractContentFromResponse(Map response) {
        try {
            List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
            Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
            return ((String) message.get("content")).trim();
        } catch (Exception e) {
            log.error("Ошибка парсинга ответа", e);
            return "Ошибка при чтении ответа модели.";
        }
    }
}
