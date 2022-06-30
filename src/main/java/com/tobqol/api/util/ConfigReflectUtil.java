/*
 * Copyright (c) 2022, Damen <gh: damencs>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:

 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.

 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.

 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.tobqol.api.util;

import com.google.common.base.Strings;
import com.google.common.collect.Multimap;
import com.google.common.collect.MultimapBuilder;
import com.tobqol.TheatreQOLConfig;
import com.tobqol.api.annotations.ConfigDependency;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import net.runelite.client.config.ConfigDescriptor;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigItemDescriptor;
import net.runelite.client.config.ConfigManager;
import org.apache.commons.lang3.reflect.FieldUtils;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.*;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ConfigReflectUtil
{
	@RequiredArgsConstructor
	private static class Dependency
	{
		private final Map<String, Object> fieldMap;
		private final ConfigItem item;
		private final ConfigDependency dependency;
	}

	private static boolean loaded = false;
	private static final Multimap<String, Dependency> dependencies = MultimapBuilder.hashKeys().arrayListValues().build();

	public static boolean load(TheatreQOLConfig proxy, ConfigManager configManager)
	{
		if (loaded || !dependencies.isEmpty() || proxy == null || configManager == null)
		{
			return false;
		}

		ConfigDescriptor descriptor = configManager.getConfigDescriptor(proxy);
		Class<?> inter = proxy.getClass().getInterfaces()[0];

		for (Method method : inter.getDeclaredMethods())
		{
			ConfigItem mci = method.getAnnotation(ConfigItem.class);
			ConfigDependency mcd = method.getAnnotation(ConfigDependency.class);

			if (mci == null || mcd == null)
			{
				continue;
			}

			for (ConfigItemDescriptor cid : descriptor.getItems())
			{
				ConfigItem ci = cid.getItem();

				if (!ci.keyName().equals(mci.keyName()))
				{
					continue;
				}

				getAnnotationRuntimeFieldMap(ci).ifPresent(fm -> dependencies.put(mcd.keyName(), new Dependency(fm, ci, mcd)));
			}
		}

		return loaded = true;
	}

	public static boolean update(TheatreQOLConfig proxy, ConfigManager configManager, String keyName)
	{
		if (!loaded || dependencies == null || dependencies.isEmpty()
				|| proxy == null || configManager == null || Strings.isNullOrEmpty(keyName))
		{
			return false;
		}

		String ret = configManager.getConfiguration(TheatreQOLConfig.GROUP_NAME, keyName);

		return refresh(dependencies.get(keyName), ret);
	}

	public static boolean updateAll(TheatreQOLConfig proxy, ConfigManager configManager)
	{
		if (!loaded || dependencies == null || dependencies.isEmpty())
		{
			return false;
		}

		dependencies.keySet().forEach(keyName -> update(proxy, configManager, keyName));

		return true;
	}

	private static boolean refresh(Collection<Dependency> dependencies, String ret)
	{
		if (dependencies == null || dependencies.isEmpty() || Strings.isNullOrEmpty(ret))
		{
			return false;
		}

		dependencies.forEach(dep -> {
			List list = Arrays.asList(dep.dependency.hideOnValues());
			dep.fieldMap.replace("hidden", list.contains(ret));
		});

		return true;
	}

	@SuppressWarnings("unchecked")
	private static Optional<Map<String, Object>> getAnnotationRuntimeFieldMap(Annotation annotation)
	{
		if (annotation == null)
		{
			return Optional.empty();
		}

		try
		{
			Object obj = Proxy.getInvocationHandler(annotation);
			Field mvf = FieldUtils.getDeclaredField(obj.getClass(), "memberValues", true);
			return Optional.of((Map<String, Object>) mvf.get(obj));
		}
		catch (IllegalArgumentException | IllegalAccessException e)
		{
			e.printStackTrace();
			return Optional.empty();
		}
	}
}
