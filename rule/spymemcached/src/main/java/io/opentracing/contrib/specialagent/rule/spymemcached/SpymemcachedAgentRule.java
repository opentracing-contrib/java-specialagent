/* Copyright 2019 The OpenTracing Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.opentracing.contrib.specialagent.rule.spymemcached;

import static net.bytebuddy.matcher.ElementMatchers.*;

import io.opentracing.contrib.specialagent.AgentRule;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.agent.builder.AgentBuilder.Transformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType.Builder;
import net.bytebuddy.implementation.bytecode.assign.Assigner.Typing;
import net.bytebuddy.utility.JavaModule;

public class SpymemcachedAgentRule extends AgentRule {
  @Override
  public AgentBuilder buildAgentChainedGlobal1(final AgentBuilder builder) {
    return builder
      .type(not(isInterface()).and(hasSuperType(named("net.spy.memcached.OperationFactory"))))
      .transform(new Transformer() {
        @Override
        public Builder<?> transform(final Builder<?> builder, final TypeDescription typeDescription, final ClassLoader classLoader, final JavaModule module) {
          return builder
            .visit(advice(typeDescription).to(Store.class).on(named("store")))
            .visit(advice(typeDescription).to(Get.class).on(named("get")))
            .visit(advice(typeDescription).to(Delete.class).on(named("delete")))
            .visit(advice(typeDescription).to(Flush.class).on(named("flush")))
            .visit(advice(typeDescription).to(GetAndTouch.class).on(named("getAndTouch")))
            .visit(advice(typeDescription).to(Gets.class).on(named("gets")))
            .visit(advice(typeDescription).to(Mutate.class).on(named("mutate")))
            .visit(advice(typeDescription).to(Touch.class).on(named("touch")))
            .visit(advice(typeDescription).to(Cat.class).on(named("cat")))
            .visit(advice(typeDescription).to(Cas.class).on(named("cas")));
        }});
  }

  public static class Store {
    @Advice.OnMethodEnter
    public static void enter(final @ClassName String className, final @Advice.Origin String origin, final @Advice.Argument(value = 0) Object storeType, final @Advice.Argument(value = 1) Object key, @Advice.Argument(value = 5, typing = Typing.DYNAMIC, readOnly = false) Object callback) {
      if (isAllowed(className, origin))
        callback = SpymemcachedAgentIntercept.store(storeType, key, callback);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class)
    public static void exit(final @Advice.Thrown Throwable thrown, final @Advice.Argument(value = 5) Object callback) {
      if (thrown != null)
        SpymemcachedAgentIntercept.exception(thrown, callback);
    }
  }

  public static class Get {
    @Advice.OnMethodEnter
    public static void enter(final @ClassName String className, final @Advice.Origin String origin, final @Advice.Argument(value = 0) Object key, @Advice.Argument(value = 1, typing = Typing.DYNAMIC, readOnly = false) Object callback) {
      if (isAllowed(className, origin))
        callback = SpymemcachedAgentIntercept.get(key, callback);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class)
    public static void exit(final @Advice.Thrown Throwable thrown, final @Advice.Argument(value = 1) Object callback) {
      if (thrown != null)
        SpymemcachedAgentIntercept.exception(thrown, callback);
    }
  }

  public static class Delete {
    @Advice.OnMethodEnter
    public static void enter(final @ClassName String className, final @Advice.Origin String origin, @Advice.Argument(value = 0, typing = Typing.DYNAMIC) Object key, @Advice.Argument(value = 1, typing = Typing.DYNAMIC, readOnly = false) Object callback, @Advice.Argument(value = 2, typing = Typing.DYNAMIC, optional = true, readOnly = false) Object callback2) {
      if (isAllowed(className, origin)) {
        if (callback2 != null) {
          callback2 = SpymemcachedAgentIntercept.delete(key, callback2);
        }
        else {
          callback = SpymemcachedAgentIntercept.delete(key, callback);
        }
      }
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class)
    public static void exit(final @Advice.Thrown Throwable thrown, final @Advice.Argument(value = 1) Object callback, final @Advice.Argument(value = 2, optional = true) Object callback2) {
      if (thrown != null) {
        if (callback2 != null)
          SpymemcachedAgentIntercept.exception(thrown, callback2);
        else
          SpymemcachedAgentIntercept.exception(thrown, callback);
      }
    }
  }

  public static class Flush {
    @Advice.OnMethodEnter
    public static void enter(final @ClassName String className, final @Advice.Origin String origin, @Advice.Argument(value = 1, typing = Typing.DYNAMIC, readOnly = false) Object callback) {
      if (isAllowed(className, origin))
        callback = SpymemcachedAgentIntercept.tracingCallback("flush", null, callback);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class)
    public static void exit(final @Advice.Thrown Throwable thrown, final @Advice.Argument(value = 1, typing = Typing.DYNAMIC) Object callback) {
      if (thrown != null)
        SpymemcachedAgentIntercept.exception(thrown, callback);
    }
  }

  public static class GetAndTouch {
    @Advice.OnMethodEnter
    public static void enter(final @ClassName String className, final @Advice.Origin String origin, final @Advice.Argument(value = 0) Object key, @Advice.Argument(value = 2, typing = Typing.DYNAMIC, readOnly = false) Object callback) {
      if (isAllowed(className, origin))
        callback = SpymemcachedAgentIntercept.getAndTouch(key, callback);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class)
    public static void exit(final @Advice.Thrown Throwable thrown, final @Advice.Argument(value = 2) Object callback) {
      if (thrown != null)
        SpymemcachedAgentIntercept.exception(thrown, callback);
    }
  }

  public static class Gets {
    @Advice.OnMethodEnter
    public static void enter(final @ClassName String className, final @Advice.Origin String origin, final @Advice.Argument(value = 0) Object key, @Advice.Argument(value = 1, typing = Typing.DYNAMIC, readOnly = false) Object callback) {
      if (isAllowed(className, origin))
        callback = SpymemcachedAgentIntercept.gets(key, callback);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class)
    public static void exit(final @Advice.Thrown Throwable thrown, final @Advice.Argument(value = 1) Object callback) {
      if (thrown != null)
        SpymemcachedAgentIntercept.exception(thrown, callback);
    }
  }

  public static class Mutate {
    @Advice.OnMethodEnter
    public static void enter(final @ClassName String className, final @Advice.Origin String origin, final @Advice.Argument(value = 1) Object key, @Advice.Argument(value = 5, typing = Typing.DYNAMIC, readOnly = false) Object callback) {
      if (isAllowed(className, origin))
        callback = SpymemcachedAgentIntercept.tracingCallback("mutate", key, callback);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class)
    public static void exit(final @Advice.Thrown Throwable thrown, final @Advice.Argument(value = 5) Object callback) {
      if (thrown != null)
        SpymemcachedAgentIntercept.exception(thrown, callback);
    }
  }

  public static class Touch {
    @Advice.OnMethodEnter
    public static void enter(final @ClassName String className, final @Advice.Origin String origin, final @Advice.Argument(value = 0) Object key, @Advice.Argument(value = 2, typing = Typing.DYNAMIC, readOnly = false) Object callback) {
      if (isAllowed(className, origin))
        callback = SpymemcachedAgentIntercept.tracingCallback("touch", key, callback);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class)
    public static void exit(final @Advice.Thrown Throwable thrown, final @Advice.Argument(value = 2) Object callback) {
      if (thrown != null)
        SpymemcachedAgentIntercept.exception(thrown, callback);
    }
  }

  public static class Cat {
    @Advice.OnMethodEnter
    public static void enter(final @ClassName String className, final @Advice.Origin String origin, final @Advice.Argument(value = 2) Object key, @Advice.Argument(value = 4, typing = Typing.DYNAMIC, readOnly = false) Object callback) {
      if (isAllowed(className, origin))
        callback = SpymemcachedAgentIntercept.tracingCallback("cat", key, callback);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class)
    public static void exit(final @Advice.Thrown Throwable thrown, final @Advice.Argument(value = 4) Object callback) {
      if (thrown != null)
        SpymemcachedAgentIntercept.exception(thrown, callback);
    }
  }

  public static class Cas {
    @Advice.OnMethodEnter
    public static void enter(final @ClassName String className, final @Advice.Origin String origin, final @Advice.Argument(value = 1) Object key, @Advice.Argument(value = 6, typing = Typing.DYNAMIC, readOnly = false) Object callback) {
      if (isAllowed(className, origin))
        callback = SpymemcachedAgentIntercept.cas(key, callback);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class)
    public static void exit(final @Advice.Thrown Throwable thrown, final @Advice.Argument(value = 6) Object callback) {
      if (thrown != null)
        SpymemcachedAgentIntercept.exception(thrown, callback);
    }
  }
}