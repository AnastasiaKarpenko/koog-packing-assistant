# Travel Packing Agent app

A Koog-based AI agent that generates a packing list for a trip. It combines:

- an LLM (via Ollama) for reasoning/JSON generation, and

- two Koog tools for hard facts:

  - a weather fetcher, and

  - a trip-context provider (type + duration).

The agent strictly orchestrates tool calls, then compels the LLM to output a single final JSON packing list.

## Key Features

Deterministic tool usage (exactly once)

Weather-integrated packing

Strict JSON output (no chatter)

LLM guardrails (loop cap, JSON detection)

Separation of concerns (weather service, tools, agent strategy)

## Architechrute at a glance
### Modules / classes

WeatherTool — Koog tool wrapping WeatherService to deliver a compact, LLM-friendly forecast string.

TripContextTool — Koog tool providing tripType and days.

WeatherService — Ktor client to OpenWeather (geocoding + 5-day/3-hour forecast), aggregated into daily summaries.

buildPackingAgent(...) — wires the LLM (Ollama), registers tools, defines helper functions, and—most importantly—builds a Koog strategy that:

  - de-dupes tool calls,

  - detects when the LLM has already produced the final JSON,

  - caps back-and-forth turns, and

  - normalizes some key names to your schema.

main() — CLI runner: reads user inputs, loads env vars, builds the agent, seeds an initial message that hints tool usage, prints the final JSON.

## Agent strategy and orchestration
Nodes: nodeStart → nodeLLMRequest → (nodeFinish | nodeExecuteTool) → nodeLLMSendToolResult → ...

Final JSON detection:

  - extractLastJsonObject(s) — scans brace depth to extract the last {...} block reliably.

  - isLikelyFinalJson(s) — quick check that it starts with { and ends with }.

De-duping & exactly-once:

  - Flags didTrip, didWeather flip immediately on intercept, preventing the model from requesting the same tool twice.

Ping-pong guardrail: up to 8 re-asks (turns cap).

Post-tool flow:

  - Always send tool result back to the LLM (nodeLLMSendToolResult).

  - If the model returns final JSON, finish.

  - Else, allow the other tool if it hasn’t been used yet; otherwise loop (under cap).

Key normalization:

  - normalizePackingJsonKeys lowercases a couple of likely mis-cased keys ("Toiletries", "Accessories").

## Data contracts (models)

DailyForecast, WeatherSummary — structured weather data produced by WeatherService.

UserTripInput — input shape (not directly used by the agent run path).

PackingList — the ideal, fully structured packing list shape


## CLI run path

Read inputs (city, start/end dates, trip type).

Load env vars via .env:

  - OPENWEATHER_API_KEY (required)

  - OLLAMA_BASE_URL (default http://localhost:11434)

  - OLLAMA_MODEL (default llama3.1:8b)

Build tools + agent.

Seed the LLM with a clear initial user message including the tool call hints and computed days (inclusive).

Print the final JSON (“— PACKING LIST (JSON) —”).

## Sequence diagram
<img width="3600" height="2100" alt="image" src="https://github.com/user-attachments/assets/47ef48ca-ca64-480f-b9a5-99db05c0a8c1" />


## Prerequisites
1. Install Ollama model on your laptop,for MacOS ```brew install ollama```
2. Pull a model ```ollama pull llama3.1:8b```
3. Run a model directly from CLI: ```ollama run llama3.1:8b```
4. Get the Open Weather API key and save it here https://github.com/AnastasiaKarpenko/koog-packing-assistant/blob/b9cadcef250e8c127ad13810164b491beee6d1e8/.env#L1

## Run 
To run the project just run the Main.kt. Make sure you are also running Ollama.

