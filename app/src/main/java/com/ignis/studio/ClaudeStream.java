package com.ignis.studio;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.JsonValue;
import com.anthropic.core.http.StreamResponse;
import com.anthropic.models.beta.messages.BetaRawMessageStreamEvent;
import com.anthropic.models.beta.messages.BetaStopReason;
import com.anthropic.models.beta.messages.MessageCreateParams;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Claude (Anthropic) con el SDK oficial de Java: respuesta en streaming.
 * Escrito en Java para no depender de la versión de Kotlin del SDK.
 */
public final class ClaudeStream {
    public interface Sink {
        /** Cada trozo de texto. Devuelve false para detener. */
        boolean onText(String text);
    }

    private ClaudeStream() {}

    /**
     * @param roles    "user" | "assistant" (los mensajes "system" van en {@code system})
     * @return "" si terminó bien; si no, el motivo
     */
    public static String stream(String apiKey, String model, String system, List<String> roles, List<String> contents,
                                long maxTokens, AtomicBoolean stop, Sink sink) {
        AnthropicClient client = AnthropicOkHttpClient.builder().apiKey(apiKey).build();
        MessageCreateParams.Builder b = MessageCreateParams.builder()
                .model(model)
                .maxTokens(maxTokens)
                // Si el modelo rechaza la petición, Anthropic la pasa a otro modelo adecuado
                .addBeta("server-side-fallback-2026-07-01")
                .putAdditionalBodyProperty("fallbacks", JsonValue.from("default"));
        if (system != null && !system.isEmpty()) b.system(system);
        for (int i = 0; i < roles.size(); i++) {
            if ("assistant".equals(roles.get(i))) b.addAssistantMessage(contents.get(i));
            else b.addUserMessage(contents.get(i));
        }
        final boolean[] refused = {false};
        try (StreamResponse<BetaRawMessageStreamEvent> resp = client.beta().messages().createStreaming(b.build())) {
            java.util.Iterator<BetaRawMessageStreamEvent> it = resp.stream().iterator();
            while (it.hasNext()) {
                if (stop.get()) break;
                BetaRawMessageStreamEvent ev = it.next();
                ev.contentBlockDelta().flatMap(d -> d.delta().text()).ifPresent(t -> {
                    if (!sink.onText(t.text())) stop.set(true);
                });
                ev.messageDelta().flatMap(d -> d.delta().stopReason()).ifPresent(r -> {
                    if (BetaStopReason.REFUSAL.equals(r)) refused[0] = true;
                });
            }
        } catch (com.anthropic.errors.UnauthorizedException e) {
            return "La clave de API de Claude no es válida";
        } catch (com.anthropic.errors.RateLimitException e) {
            return "Claude tiene demasiadas peticiones ahora mismo. Espera un momento";
        } catch (com.anthropic.errors.NotFoundException e) {
            return "Ese modelo de Claude no existe o tu cuenta no tiene acceso";
        } catch (com.anthropic.errors.AnthropicServiceException e) {
            return "Claude respondió con un error (" + e.statusCode() + ")";
        } catch (Exception e) {
            return "No se pudo conectar con Claude. Revisa tu conexión";
        } finally {
            client.close();
        }
        return refused[0] ? "Claude no puede ayudar con esa petición" : "";
    }
}
