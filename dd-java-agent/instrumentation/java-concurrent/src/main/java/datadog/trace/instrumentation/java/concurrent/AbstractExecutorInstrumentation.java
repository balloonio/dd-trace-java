package datadog.trace.instrumentation.java.concurrent;

import static datadog.trace.agent.tooling.ByteBuddyElementMatchers.safeHasInterface;
import static net.bytebuddy.matcher.ElementMatchers.isInterface;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;

import datadog.trace.agent.tooling.Instrumenter;
import datadog.trace.api.Config;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Executor;
import lombok.extern.slf4j.Slf4j;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

@Slf4j
public abstract class AbstractExecutorInstrumentation extends Instrumenter.Default {

  public static final String EXEC_NAME = "java_concurrent";

  private final boolean TRACE_ALL_EXECUTORS = Config.get().isTraceExecutorsAll();

  /**
   * Only apply executor instrumentation to whitelisted executors. To apply to all executors, use
   * override setting above.
   */
  private final Collection<String> WHITELISTED_EXECUTORS;

  /**
   * Some frameworks have their executors defined as anon classes inside other classes. Referencing
   * anon classes by name would be fragile, so instead we will use list of class prefix names. Since
   * checking this list is more expensive (O(n)) we should try to keep it short.
   */
  private final Collection<String> WHITELISTED_EXECUTORS_PREFIXES;

  public AbstractExecutorInstrumentation(final String... additionalNames) {
    super(EXEC_NAME, additionalNames);

    if (TRACE_ALL_EXECUTORS) {
      log.info("Tracing all executors enabled.");
      WHITELISTED_EXECUTORS = Collections.emptyList();
      WHITELISTED_EXECUTORS_PREFIXES = Collections.emptyList();
    } else {
      final String[] whitelist = {
        "akka.actor.ActorSystemImpl$$anon$1",
        "akka.dispatch.BalancingDispatcher",
        "akka.dispatch.Dispatcher",
        "akka.dispatch.Dispatcher$LazyExecutorServiceDelegate",
        "akka.dispatch.ExecutionContexts$sameThreadExecutionContext$",
        "akka.dispatch.forkjoin.ForkJoinPool",
        "akka.dispatch.ForkJoinExecutorConfigurator$AkkaForkJoinPool",
        "akka.dispatch.MessageDispatcher",
        "akka.dispatch.PinnedDispatcher",
        "com.google.common.util.concurrent.AbstractListeningExecutorService",
        "com.google.common.util.concurrent.MoreExecutors$ListeningDecorator",
        "com.google.common.util.concurrent.MoreExecutors$ScheduledListeningDecorator",
        "io.netty.channel.epoll.EpollEventLoop",
        "io.netty.channel.epoll.EpollEventLoopGroup",
        "io.netty.channel.MultithreadEventLoopGroup",
        "io.netty.channel.nio.NioEventLoop",
        "io.netty.channel.nio.NioEventLoopGroup",
        "io.netty.channel.SingleThreadEventLoop",
        "io.netty.util.concurrent.AbstractEventExecutor",
        "io.netty.util.concurrent.AbstractEventExecutorGroup",
        "io.netty.util.concurrent.AbstractScheduledEventExecutor",
        "io.netty.util.concurrent.DefaultEventExecutor",
        "io.netty.util.concurrent.DefaultEventExecutorGroup",
        "io.netty.util.concurrent.GlobalEventExecutor",
        "io.netty.util.concurrent.MultithreadEventExecutorGroup",
        "io.netty.util.concurrent.SingleThreadEventExecutor",
        "java.util.concurrent.AbstractExecutorService",
        "java.util.concurrent.CompletableFuture$ThreadPerTaskExecutor",
        "java.util.concurrent.Executors$DelegatedExecutorService",
        "java.util.concurrent.Executors$FinalizableDelegatedExecutorService",
        "java.util.concurrent.ForkJoinPool",
        "java.util.concurrent.ScheduledThreadPoolExecutor",
        "java.util.concurrent.ThreadPoolExecutor",
        "kotlinx.coroutines.scheduling.CoroutineScheduler",
        "org.eclipse.jetty.util.thread.QueuedThreadPool",
        "org.eclipse.jetty.util.thread.ReservedThreadExecutor",
        "org.glassfish.grizzly.threadpool.GrizzlyExecutorService",
        "play.api.libs.streams.Execution$trampoline$",
        "scala.concurrent.forkjoin.ForkJoinPool",
        "scala.concurrent.Future$InternalCallbackExecutor$",
        "scala.concurrent.impl.ExecutionContextImpl",
      };

      final Set<String> executors = new HashSet<>(Config.get().getTraceExecutors());
      executors.addAll(Arrays.asList(whitelist));

      WHITELISTED_EXECUTORS = Collections.unmodifiableSet(executors);

      final String[] whitelistPrefixes = {"slick.util.AsyncExecutor$"};
      WHITELISTED_EXECUTORS_PREFIXES =
          Collections.unmodifiableCollection(Arrays.asList(whitelistPrefixes));
    }
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    ElementMatcher.Junction<TypeDescription> matcher = not(isInterface());
    if (!TRACE_ALL_EXECUTORS) {
      matcher =
          matcher.and(
              new ElementMatcher<TypeDescription>() {
                @Override
                public boolean matches(final TypeDescription target) {
                  boolean whitelisted = WHITELISTED_EXECUTORS.contains(target.getName());

                  // Check for possible prefixes match only if not whitelisted already
                  if (!whitelisted) {
                    for (final String name : WHITELISTED_EXECUTORS_PREFIXES) {
                      if (target.getName().startsWith(name)) {
                        whitelisted = true;
                        break;
                      }
                    }
                  }

                  if (!whitelisted) {
                    log.debug("Skipping executor instrumentation for {}", target.getName());
                  }
                  return whitelisted;
                }
              });
    }
    return matcher.and(
        safeHasInterface(named(Executor.class.getName()))); // Apply expensive matcher last.
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      AbstractExecutorInstrumentation.class.getPackage().getName() + ".ExecutorInstrumentationUtils"
    };
  }
}
