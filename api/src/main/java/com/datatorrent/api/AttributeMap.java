/*
 * Copyright (c) 2013 DataTorrent, Inc. ALL Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.datatorrent.api;

import java.io.Serializable;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.Map.Entry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Parameterized and scoped context attribute map that supports serialization.
 * Derived from io.netty.util.AttributeMap
 *
 * @since 0.3.2
 */
public interface AttributeMap
{
  /**
   * Return the attribute value for the given key. If the map does not have an
   * entry for the key, a default attribute value will be created and returned.
   * Modifies state of the map of key is not present.
   *
   * @param <T>
   * @param key
   * @return <T> AttributeValue<T>
   */
  <T> T get(Attribute<T> key);

  <T> T get(String key);

  <T> T put(Attribute<T> key, T value);

  Set<Map.Entry<Attribute<?>, Object>> entrySet();

  /**
   * Return the value map
   *
   * @return the value map
   */
  AttributeMap clone();

  /**
   * TypedNull objects can be used in place of the null default values.
   * Since the Attribute does not have access to the key type at runtime
   * due to erasure, it has no way of knowing the value type if the value
   * needs to be generated by reading configuration file or so. Storing
   * the null values as TypedNull objects make the type information accessible.
   *
   * @param <T> Type of the null value.
   */
  public static class TypedNull<T>
  {
    public final Class<T> clazz;

    public TypedNull(Class<T> clazz)
    {
      this.clazz = clazz;
    }

    public T value()
    {
      return null;
    }

  }

  /**
   * Scoped attribute key. Subclasses define scope.
   *
   * @param <T>
   */
  public static class Attribute<T> implements Serializable
  {
    private final T defaultValue;
    private final String name;
    public final Class<T> clazz;

    public Attribute(Class<T> clazz)
    {
      this.name = "";
      this.defaultValue = null;
      this.clazz = clazz;
    }

    public Attribute(T defaultValue)
    {
      if (defaultValue == null) {
        throw new IllegalArgumentException("This constructor cannot be used to set null default, please use null Attribute(Class<T>) constructor instead.");
      }

      this.name = "";
      this.defaultValue = defaultValue;

      @SuppressWarnings("unchecked")
      final Class<T> klass = (Class<T>)defaultValue.getClass();
      this.clazz = klass;
    }

    public String name()
    {
      return name;
    }

    public T getDefault()
    {
      return defaultValue;
    }

    public Class<T> getAttributeClass()
    {
      return clazz;
    }

    @Override
    public int hashCode()
    {
      return name.hashCode();
    }

    @Override
    public boolean equals(Object obj)
    {
      if (obj == null) {
        return false;
      }
      if (getClass() != obj.getClass()) {
        return false;
      }
      @SuppressWarnings("unchecked")
      final Attribute<T> other = (Attribute<T>)obj;
      if ((this.name == null) ? (other.name != null) : !this.name.equals(other.name)) {
        return false;
      }
      return true;
    }

    @Override
    public String toString()
    {
      return "Attribute{" + "defaultValue=" + defaultValue + ", name=" + name + ", clazz=" + clazz + '}';
    }

    private static final long serialVersionUID = 201310111904L;
  }

  /**
   * AttributeValue map records values against String keys and can therefore be serialized
   * ({@link Attribute} cannot be serialized)
   *
   */
  public class DefaultAttributeMap implements AttributeMap, Serializable
  {
    private static final long serialVersionUID = 201306051022L;
    private final HashMap<Attribute<?>, Object> map;
    private final HashMap<String, Attribute<?>> attributeMap;

    public DefaultAttributeMap()
    {
      this(new HashMap<Attribute<?>, Object>());
    }

    private DefaultAttributeMap(HashMap<Attribute<?>, Object> map)
    {
      this.map = map;
      attributeMap = new HashMap<String, Attribute<?>>(map.size());
      for (Attribute<?> attribute : map.keySet()) {
        attributeMap.put(attribute.name, attribute);
      }
    }

    // if there is at least one attribute, serialize scope for key object lookup
    @Override
    @SuppressWarnings("unchecked")
    public <T> T get(Attribute<T> key)
    {
      return (T)map.get(key);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T get(String key)
    {
      Attribute<?> attribute = attributeMap.get(key);
      if (attribute != null) {
        return (T)get(attribute);
      }

      return null;
    }

    @Override
    public String toString()
    {
      return this.map.toString();
    }

    @Override
    @SuppressWarnings("unchecked")
    public DefaultAttributeMap clone()
    {
      return new DefaultAttributeMap((HashMap<Attribute<?>, Object>)map.clone());
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T put(Attribute<T> key, T value)
    {
      attributeMap.put(key.name, key);
      return (T)map.put(key, value);
    }

    @Override
    public Set<Entry<Attribute<?>, Object>> entrySet()
    {
      return map.entrySet();
    }

  }

  public static class AttributeInitializer
  {
    static final HashMap<Class<?>, Set<Attribute<?>>> map = new HashMap<Class<?>, Set<Attribute<?>>>();

    public static Set<Attribute<?>> getAttributes(Class<?> clazz)
    {
      return map.get(clazz);
    }

    public static boolean initialize(Class<?> clazz)
    {
      Set<Attribute<?>> set = new HashSet<Attribute<?>>();

      try {
        for (Field f : clazz.getDeclaredFields()) {
          if (Modifier.isStatic(f.getModifiers()) && Attribute.class.isAssignableFrom(f.getType())) {
            Attribute<?> attribute = (Attribute<?>)f.get(null);
            Field name = Attribute.class.getDeclaredField("name");
            name.setAccessible(true);
            name.set(attribute, f.getName());
            name.setAccessible(false);
            set.add(attribute);
          }
        }
      }
      catch (Exception ex) {
        throw new RuntimeException(ex);
      }

      map.put(clazz, set);
      return true;
    }

    private static final Logger logger = LoggerFactory.getLogger(AttributeInitializer.class);
  }

}
