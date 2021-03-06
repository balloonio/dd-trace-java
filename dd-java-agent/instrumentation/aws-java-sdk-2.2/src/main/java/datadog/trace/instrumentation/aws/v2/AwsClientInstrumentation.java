package datadog.trace.instrumentation.aws.v2;

import static datadog.trace.agent.tooling.ByteBuddyElementMatchers.safeHasInterface;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.isInterface;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;

import com.google.auto.service.AutoService;
import datadog.trace.agent.tooling.Instrumenter;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import software.amazon.awssdk.core.client.builder.SdkClientBuilder;

/** AWS SDK v2 instrumentation */
@AutoService(Instrumenter.class)
public final class AwsClientInstrumentation extends AbstractAwsClientInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return nameStartsWith("software.amazon.awssdk.")
        .and(not(isInterface()))
        .and(
            safeHasInterface(named("software.amazon.awssdk.core.client.builder.SdkClientBuilder")));
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    return singletonMap(
        isMethod().and(isPublic()).and(named("build")),
        AwsClientInstrumentation.class.getName() + "$AwsBuilderAdvice");
  }

  public static class AwsBuilderAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void methodEnter(@Advice.This final SdkClientBuilder thiz) {
      TracingExecutionInterceptor.overrideConfiguration(thiz);
    }
  }
}
