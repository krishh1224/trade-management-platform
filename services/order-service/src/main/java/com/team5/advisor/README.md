# Advisor module

The Gemini chat assistant, served by the Trade REST API beside the order and account routes. It used to be the
standalone `advisor-api` service on port 8083. Contract: [contracts/advisor-api.yaml](../../../../../../../../contracts/advisor-api.yaml).

`POST /api/v1/advisor/chat` takes the conversation (at most 20 messages) and returns the next reply, the tools the
model used and the model name. The account comes only from the token, so a user can only ask about their own.

## How a turn works
1. The account is checked: unknown is `ACC-404`, not `ACTIVE` is `ACC-403`.
2. The conversation goes to Gemini with the system prompt (`resources/prompts/advisor-system.md`) and six
   read-only tools, bound to that account: `get_portfolio`, `get_quotes`, `get_fx_rate`, `search_instruments`,
   `get_order_history`, `get_advice_signals`.
3. Each tool the model calls costs another request to Gemini, so one question is at least two requests
   (decide the tool, then answer).

Prices come from `market_quote_snapshots`, written by the executor's poller. The advisor never calls Fauxnance,
so it uses none of that daily quota.

## Configuration
| Setting | Default | Note |
|---|---|---|
| `GEMINI_API_KEY` | none | In `.env`. Without it the service still starts and the chat answers `ADV-503` |
| `GEMINI_MODEL` | `gemini-3.8-flash` | Free-tier limits differ a lot by model (check AI Studio) |
| `advisor.gemini.request-timeout` | `40s` | One call to Gemini. Gemini can accept a request and never answer; this stops the wait |
| `spring.ai.retry.max-attempts` | `2` | Spring AI's retry. The SDK's own retries are off (`GeminiClientConfig`) |

Failures: a rate limit (HTTP 429 anywhere in the cause chain) is `ADV-429`; everything else, including a timeout,
is `ADV-503`.

## Decision log
1. **Spring AI 1.1.x, not 2.x.** Order-service is on Spring Boot 3.5 and Spring AI 2.x needs Spring Framework 7
   (Boot 4). In 1.1.x the model settings live under `spring.ai.google.genai.chat.options.*`; the old `chat.model`
   key is silently ignored and the model would fall back to a retired default.
2. **Read-only by transaction, not by pool.** The standalone service had its own read-only connection pool.
   Here `AdvisorRepository` is `@Transactional(readOnly = true)`, so Postgres rejects a write, and
   `AdvisorRepositoryReadOnlyTest` fails the build if a write statement appears.
3. **Own timeout on the Gemini client.** Spring AI builds the client with no timeout, so a stalled call hung the
   chat forever. `GeminiClientConfig` sets one and turns the SDK's default retries off (each retry waited out the
   full timeout).
4. **Exception handler limited to this controller.** A catch-all that answered `ADV-503` would otherwise swallow
   the trading routes' own errors.
5. **Shares threads with trading.** A chat call holds a Tomcat thread while it waits for Gemini. The timeout bounds
   that, but a slow model under load still competes with order placement; that is the price of one process.
