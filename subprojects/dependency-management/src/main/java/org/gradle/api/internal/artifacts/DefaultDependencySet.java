/*
 * Copyright 2011 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gradle.api.internal.artifacts;

import com.google.common.collect.ImmutableSet;
import org.gradle.api.Describable;
import org.gradle.api.DomainObjectSet;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.DependencySet;
import org.gradle.api.artifacts.ModuleDependency;
import org.gradle.api.internal.DelegatingDomainObjectSet;
import org.gradle.api.internal.artifacts.configurations.MutationValidator;
import org.gradle.api.tasks.TaskDependency;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public class DefaultDependencySet extends DelegatingDomainObjectSet<Dependency> implements DependencySet {
    private static final Set<String> MUTATION_CHECK_METHODS = ImmutableSet.of("setTransitive", "exclude", "setTargetConfiguration");

    private final Describable displayName;
    private final Configuration clientConfiguration;

    public DefaultDependencySet(Describable displayName, Configuration clientConfiguration, DomainObjectSet<Dependency> backingSet) {
        super(backingSet);
        this.displayName = displayName;
        this.clientConfiguration = clientConfiguration;
    }

    @Override
    public String toString() {
        return displayName.getDisplayName();
    }

    public TaskDependency getBuildDependencies() {
        return clientConfiguration.getBuildDependencies();
    }

    @Override
    public boolean add(final Dependency o) {
        if (o instanceof ModuleDependency && !(o instanceof MutationValidatingDependency)) {
            Set<Class<?>> interfaces = collectInterfaces(o.getClass(), new HashSet<Class<?>>());
            interfaces.add(MutationValidatingDependency.class);
            final ModuleDependency mutationValidatingModule = (ModuleDependency) Proxy.newProxyInstance(
                o.getClass().getClassLoader(),
                interfaces.toArray(new Class<?>[interfaces.size()]),
                new InvocationHandler() {
                    @Override
                    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                        String methodName = method.getName();
                        Class<?> declaringClass = method.getDeclaringClass();
                        if (declaringClass == MutationValidatingDependency.class) {
                            // getDelegate is the only one
                            return o;
                        }
                        if (declaringClass == ModuleDependency.class && MUTATION_CHECK_METHODS.contains(methodName)) {
                            ((MutationValidator) clientConfiguration).validateMutation(MutationValidator.MutationType.DEPENDENCIES);
                        } else if ("equals".equals(methodName) && args.length==1) {
                            Object arg = args[0];
                            if (arg instanceof MutationValidatingDependency) {
                                return method.invoke(o, ((MutationValidatingDependency)arg).getDelegate());
                            }
                        }
                        return method.invoke(o, args);
                    }
                });
            return super.add(mutationValidatingModule);
        }
        return super.add(o);
    }

    private static Set<Class<?>> collectInterfaces(Class<?> clazz, Set<Class<?>> interfaces) {
        if (clazz == null) {
            return interfaces;
        }
        for (Class<?> intf : clazz.getInterfaces()) {
            if (interfaces.add(intf)) {
                collectInterfaces(intf, interfaces);
            }
        }
        collectInterfaces(clazz.getSuperclass(), interfaces);
        return interfaces;
    }

    private interface MutationValidatingDependency {
        ModuleDependency getDelegate();
    }

    @Override
    public boolean addAll(Collection<? extends Dependency> dependencies) {
        boolean added = false;
        for (Dependency dependency : dependencies) {
            added |= add(dependency);
        }
        return added;
    }
}
