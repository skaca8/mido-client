package io.github.hyunjun.mido.config;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;
import java.util.function.Supplier;

/**
 * Internal. Stands in for an interceptor that must be pulled from the container, resolving it on the
 * first {@code intercept} call rather than when the {@code RestClient} is built.
 *
 * <p>The deferral is the whole point, not an optimization. {@code getOrCreateClient} is routinely
 * called from a consumer's constructor:
 *
 * <pre>{@code
 * public AllmytourDirectApi(MidoClientFactory factory) {
 *     this.client = factory.getOrCreateClient("allmytour");   // still inside bean creation
 * }
 * }</pre>
 *
 * <p>Fetching an interceptor bean at that moment would force it — and whatever it depends on — into
 * existence mid-construction, which turns a perfectly ordinary wiring into a circular reference. By
 * the time a request actually runs, the context has long finished refreshing and the lookup is safe.
 *
 * <p>The resolved delegate is cached under double-checked locking on a {@code volatile} field: a
 * {@code RestClient} is shared across threads, so several may reach the first request at once.
 */
@RequiredArgsConstructor
final class LazyInterceptorDelegate implements ClientHttpRequestInterceptor {

    /** Bean name or class name as written in YAML; used only for diagnostics. */
    private final String reference;

    private final Supplier<ClientHttpRequestInterceptor> resolver;

    private volatile ClientHttpRequestInterceptor delegate;

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte @NonNull [] body,
                                        ClientHttpRequestExecution execution) throws IOException {
        return resolve().intercept(request, body, execution);
    }

    private ClientHttpRequestInterceptor resolve() {
        ClientHttpRequestInterceptor resolved = delegate;
        if (resolved != null) return resolved;

        synchronized (this) {
            if (delegate == null) {
                delegate = requireResolved();
            }
            return delegate;
        }
    }

    private ClientHttpRequestInterceptor requireResolved() {
        ClientHttpRequestInterceptor resolved = resolver.get();
        if (resolved == null) {
            throw new IllegalStateException("Interceptor '" + reference + "' resolved to null");
        }
        return resolved;
    }

    /** Visible for tests: whether the delegate has been pulled from the container yet. */
    boolean isResolved() {
        return delegate != null;
    }

    @Override
    public String toString() {
        return "LazyInterceptorDelegate[" + reference + "]";
    }
}
