package io.qameta.atlas.core;

import io.qameta.atlas.core.api.*;
import io.qameta.atlas.core.context.RetryerContext;
import io.qameta.atlas.core.context.TargetContext;
import io.qameta.atlas.core.internal.*;
import io.qameta.atlas.core.target.HardcodedTarget;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static io.qameta.atlas.core.util.ReflectionUtils.getMethods;

/**
 * @author Artem Eroshenko.
 */
@SuppressWarnings("checkstyle:ClassDataAbstractionCoupling")
public class Atlas {

    private final Configuration configuration;

    public Atlas() {
        this(new Configuration());
    }

    public Atlas(final Configuration configuration) {
        this.configuration = configuration;
        final Optional<RetryerContext> context = this.configuration.getContext(RetryerContext.class);
        if (!context.isPresent()) {
            configuration.registerContext(new RetryerContext(new EmptyRetryer()));
        }
    }

    public Atlas listener(final Listener listener) {
        this.configuration.registerExtension(listener);
        return this;
    }

    public Atlas extension(final MethodExtension methodExtension) {
        this.configuration.registerExtension(methodExtension);
        return this;
    }

    public Atlas context(final Context context) {
        this.configuration.registerContext(context);
        return this;
    }

    @SuppressWarnings("unchecked")
    public <T> T create(final Target target, final Class<T> type) {
        final Map<Method, MethodInvoker> invokers = new HashMap<>();
        final List<Method> methods = getMethods(type, Object.class);
        this.context(new TargetContext(target));

        methods.forEach(method -> {
            final MethodInvoker invoker = configuration.getExtensions(MethodExtension.class).stream()
                    .filter(extension -> extension.test(method)).map(MethodInvoker.class::cast).findFirst()
                    .orElse(new TargetMethodInvoker());
            invokers.put(method, invoker);
        });

        final ListenerNotifier notifier = new ListenerNotifier();
        configuration.getExtensions(Listener.class).forEach(notifier::addListeners);

        return (T) Proxy.newProxyInstance(
                type.getClassLoader(),
                new Class[]{type},
                new AtlasMethodHandler(configuration, notifier, invokers)
        );
    }

    public <T> T create(final String name, final Object target, final Class<T> type) {
        return create(new HardcodedTarget(name, target), type);
    }

    public <T> T create(final Object target, final Class<T> type) {
        return create(type.getSimpleName(), target, type);
    }

}
