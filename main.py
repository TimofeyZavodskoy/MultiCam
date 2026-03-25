from fastapi import FastAPI, File, UploadFile, Form
from fastapi.responses import JSONResponse
from dotenv import load_dotenv
from openai import OpenAI
import base64
import os

load_dotenv()

app = FastAPI()

api_key = os.getenv("OPENROUTER_API_KEY")

client = OpenAI(
    base_url="https://openrouter.ai/api/v1",
    api_key=api_key,
)


def encode_image(image: UploadFile) -> str:
    content = image.file.read()
    image.file.seek(0)
    return base64.b64encode(content).decode("utf-8")


async def call_model(data_url: str, prompt: str, model_name: str, max_tokens: int = 1024) -> str:
    resp = client.chat.completions.create(
        model=model_name,
        messages=[
            {
                "role": "user",
                "content": [
                    {
                        "type": "text",
                        "text": prompt
                    },
                    {
                        "type": "image_url",
                        "image_url": {"url": data_url}
                    },
                ]
            }
        ],
        max_tokens=max_tokens,
        temperature=0.1,
    )
    return resp.choices[0].message.content


@app.post("/classify")
async def classify(image: UploadFile = File(...)):
    try:
        data_url = f"data:image/jpeg;base64,{encode_image(image)}"
        raw_response = await  call_model(data_url, "Analyze the image. Output ONLY one word: "
                                                   "'math' (only formulas), 'text' (only plain text), "
                                                   "'mixed' (both text and math), or 'image' (no text/math).",
                                         max_tokens=1024, model_name="google/gemma-3-4b-it:free")
        tag = raw_response.strip().lower()
        return {"tag": tag}
    except Exception as e:
        return JSONResponse(status_code=500, content={"error": str(e)})


@app.post("/description")
async def description(image: UploadFile = File(...)):
    try:
        data_url = f"data:image/jpeg;base64,{encode_image(image)}"
        raw_response = await call_model(data_url, """Follow this structured plan for your response: 
                  1. **General Description:** Identify the central object or the overall scene. 
                  2. **Detailed Analysis:** Describe colors, materials, shapes, and any unique features or textures.
                  3. **Brands and Markings:** Look for logos, brand names, or model identifiers. 
                  - If a brand is clearly visible, name it.
                  - If no brand is visible but the design is recognizable, make a cautious suggestion (e.g., "The design resembles Nike's style").
                  - If it's impossible to identify, state: "Brand not identified."
                  4. **Text:** Transcribe any legible text found in the image exactly as it appears.CRITICAL INSTRUCTIONS:
                  - The output language must be RUSSIAN only.
                  - Be concise, accurate, and objective. 
                  - Do not hallucinate details that are not present in the image.""", max_tokens=2048,
                                        model_name="google/gemma-3-4b-it:free")
        description = raw_response.strip()
        return {"description": description}
    except Exception as e:
        return JSONResponse(status_code=500, content={"error": str(e)})


@app.post("/ocr")
async def text_ocr(image: UploadFile = File(...)):
    try:
        data_url = f"data:image/jpeg;base64,{encode_image(image)}"
        raw_response = await call_model(data_url, "Transcribe the text from this image exactly as it is. "
                                                  "Maintain paragraphs and formatting. Output ONLY the transcribed text",
                                        max_tokens=1024, model_name="google/gemma-3-2b-it:free")
        result = raw_response.strip()
        return {"result": result}
    except Exception as e:
        return JSONResponse(status_code=500, content={"error": str(e)})


@app.post("/ocr-full")
async def full_ocr(image: UploadFile = File(...)):
    try:
        data_url = f"data:image/jpeg;base64,{encode_image(image)}"
        raw_response = await call_model(data_url, "Transcribe everything. Keep text as plain text and "
                                                  "convert all formulas/math to LaTeX wrapped in $...$. "
                                                  "Be very careful with trigonometric functions like tg, ctg, sin, cos. Do not split them into separate variables."
                                                  "Output ONLY Markdown.",
                                        max_tokens=1024, model_name="google/gemma-3-12b-it:free")
        content = raw_response.strip()
        return {"content": content}
    except Exception as e:
        return JSONResponse(status_code=500, content={"error": str(e)})


@app.post("/solve")
async def solve(payload: dict):
    try:
        latex_query = payload.get("latex", "")
        resp = client.chat.completions.create(
            model="stepfun/step-3.5-flash:free",
            messages=[
                {"role": "system", "content": """System Role:
                        You are an expert Mathematics Professor specializing in Algebra, Trigonometry, and Calculus. Your goal is to solve the provided mathematical task with high precision and pedagogical clarity.

                        Input Format:
                        You will receive a mathematical problem, which may contain LaTeX formulas wrapped in $...$ or $$...$$, or plain text descriptions of a task.

                        Task Instructions:
                        1. Analyze the input and identify the core mathematical problem.
                        2. Provide a rigorous, step-by-step solution.
                        3. If applicable, define the Domain of Validity (ODZ/Range) at the beginning.
                        4. Use logical transitions between steps (e.g., "Factoring the expression," "Applying trigonometric identities").
                        5. Format all mathematical notations strictly in LaTeX wrapped in $...$ for inline and $$...$$ for blocks.
                        6. The final output must be in RUSSIAN.
                        7. For Physics problems: 
                        - Start with "Given" (Дано), converting units to SI if necessary.
                        - State the fundamental physical laws or principles being used.
                        - Perform symbolic derivation before plugging in numerical values.
                        - Include units in the final answer.

                        Output Structure (Markdown):
                        - ### Условие (Briefly restate the problem)
                        - ### Ход решения (Step-by-step breakdown)
                        - ### Ответ (Clear final result)

                        Constraints:
                        - Be concise but thorough.
                        - Do not skip intermediate algebraic steps.
                        - If the problem is unsolvable or ambiguous, explain why in RUSSIAN."""},
                {"role": "user", "content": f"Solve this: {latex_query}"}
            ],
            extra_body={"reasoning": {"enabled": True}},
            max_tokens=4096,
            temperature=0.1,
        )

        message = resp.choices[0].message

        reasoning = str(getattr(message, 'reasoning_details', "") or "")
        content = message.content or ""

        return {
            "solution": content,
            "reasoning": reasoning
        }
    except Exception as e:
        return JSONResponse(status_code=500, content={"error": str(e)})


if __name__ == "__main__":
    import uvicorn

    uvicorn.run(app, host="0.0.0.0", port=8000)
