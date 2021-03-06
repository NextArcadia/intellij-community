// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.ui;

import com.intellij.diagnostic.ActivityCategory;
import com.intellij.diagnostic.StartUpMeasurer;
import com.intellij.ide.ui.search.OptionDescription;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.concurrent.ConcurrentMap;

public class TopHitCache implements Disposable {
  protected final ConcurrentMap<Class<?>, Collection<OptionDescription>> map = ContainerUtil.newConcurrentMap();

  public static TopHitCache getInstance() {
    return ServiceManager.getService(TopHitCache.class);
  }

  @Override
  public void dispose() {
    map.values().forEach(TopHitCache::dispose);
  }

  private static void dispose(Collection<? extends OptionDescription> options) {
    if (options != null) options.forEach(TopHitCache::dispose);
  }

  private static void dispose(OptionDescription option) {
    if (option instanceof Disposable) Disposer.dispose((Disposable)option);
  }

  public void invalidateCachedOptions(Class<? extends OptionsTopHitProvider.ApplicationLevelProvider> providerClass) {
    map.remove(providerClass);
  }

  public Collection<OptionDescription> getCachedOptions(@NotNull OptionsSearchTopHitProvider provider,
                                                        @Nullable Project project,
                                                        @Nullable PluginDescriptor pluginDescriptor) {
    Class<?> clazz = provider.getClass();
    Collection<OptionDescription> result = map.get(clazz);
    if (result != null) {
      return result;
    }

    long startTime = StartUpMeasurer.getCurrentTime();
    if (provider instanceof OptionsSearchTopHitProvider.ProjectLevelProvider) {
      //noinspection ConstantConditions
      result = ((OptionsSearchTopHitProvider.ProjectLevelProvider)provider).getOptions(project);
    }
    else if (provider instanceof OptionsSearchTopHitProvider.ApplicationLevelProvider) {
      result = ((OptionsSearchTopHitProvider.ApplicationLevelProvider)provider).getOptions();
    }
    else {
      result = ((OptionsTopHitProvider)provider).getOptions(project);
    }
    ActivityCategory category = project == null ? ActivityCategory.APP_OPTIONS_TOP_HIT_PROVIDER : ActivityCategory.PROJECT_OPTIONS_TOP_HIT_PROVIDER;
    StartUpMeasurer.addCompletedActivity(startTime, clazz, category, pluginDescriptor == null ? null : pluginDescriptor.getPluginId().getIdString());
    Collection<OptionDescription> prevValue = map.putIfAbsent(clazz, result);
    return prevValue == null ? result : prevValue;
  }
}
