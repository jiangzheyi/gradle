/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.internal.service.scopes;

import org.gradle.api.Action;
import org.gradle.api.internal.DependencyInjectingInstantiator;
import org.gradle.api.internal.SettingsInternal;
import org.gradle.api.internal.file.BaseDirFileResolver;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.plugins.*;
import org.gradle.api.internal.plugins.dsl.PluginRepositoryHandler;
import org.gradle.api.internal.plugins.repositories.MavenPluginRepository;
import org.gradle.api.tasks.util.PatternSet;
import org.gradle.initialization.DefaultProjectDescriptorRegistry;
import org.gradle.initialization.ProjectDescriptorRegistry;
import org.gradle.internal.nativeintegration.filesystem.FileSystem;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.service.DefaultServiceRegistry;
import org.gradle.internal.service.ServiceRegistration;
import org.gradle.internal.service.ServiceRegistry;

public class SettingsScopeServices extends DefaultServiceRegistry {
    private final SettingsInternal settings;

    public SettingsScopeServices(ServiceRegistry parent, final SettingsInternal settings) {
        super(parent);
        this.settings = settings;
        /*
         * While we don't have the pluginRepositories {} block, allow
         * adding a plugin repository using a system property
         */
        register(new Action<ServiceRegistration>() {
            @Override
            public void execute(ServiceRegistration serviceRegistration) {
            final String customRepoUrl = System.getProperty("org.gradle.plugin.repoUrl");
            if (customRepoUrl != null) {
                PluginRepositoryHandler pluginRepositoryHandler = get(PluginRepositoryHandler.class);
                FileResolver fileResolver = get(FileResolver.class);
                final String normalizedUrl = fileResolver.resolveUri(customRepoUrl).toString();
                pluginRepositoryHandler.maven(new Action<MavenPluginRepository>() {
                    @Override
                    public void execute(MavenPluginRepository mavenPluginRepository) {
                        mavenPluginRepository.setName("maven");
                        mavenPluginRepository.setUrl(normalizedUrl);
                    }
                });
            }
            }
        });
    }

    protected FileResolver createFileResolver() {
        return new BaseDirFileResolver(get(FileSystem.class), settings.getSettingsDir(), getFactory(PatternSet.class));
    }

    protected PluginRegistry createPluginRegistry(PluginRegistry parentRegistry) {
        return parentRegistry.createChild(settings.getClassLoaderScope());
    }

    protected PluginManagerInternal createPluginManager(Instantiator instantiator, PluginRegistry pluginRegistry) {
        PluginApplicator applicator = new ImperativeOnlyPluginApplicator<SettingsInternal>(settings);
        return instantiator.newInstance(DefaultPluginManager.class, pluginRegistry, new DependencyInjectingInstantiator(this), applicator);
    }

    protected ProjectDescriptorRegistry createProjectDescriptorRegistry() {
        return new DefaultProjectDescriptorRegistry();
    }
}
