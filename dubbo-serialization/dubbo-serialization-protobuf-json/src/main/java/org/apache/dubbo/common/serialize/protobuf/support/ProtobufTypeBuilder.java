/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.dubbo.common.serialize.protobuf.support;

import org.apache.dubbo.common.logger.Logger;
import org.apache.dubbo.common.logger.LoggerFactory;
import org.apache.dubbo.metadata.definition.TypeDefinitionBuilder;
import org.apache.dubbo.metadata.definition.builder.TypeBuilder;
import org.apache.dubbo.metadata.definition.model.TypeDefinition;

import com.google.protobuf.ByteString;
import com.google.protobuf.Descriptors;
import com.google.protobuf.GeneratedMessageV3;
import com.google.protobuf.ProtocolStringList;
import com.google.protobuf.UnknownFieldSet;

import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ProtobufTypeBuilder implements TypeBuilder {
    protected final Logger logger = LoggerFactory.getLogger(getClass());
    private static final Pattern MAP_PATTERN = Pattern.compile("^java[.]util[.]Map<(\\w+[[.$]\\w*]+), (\\w+[[.$]\\w*]+)>$");
    private static final Pattern LIST_PATTERN = Pattern.compile("^java[.]util[.]List<(\\w+([.$]\\w*)+)>$");
    private static final List<String> list = null;
    /**
     * provider a List<String> type for TypeDefinitionBuilder.build(type,class,cache)
     * "repeated string" transform to ProtocolStringList, should be build as List<String> type.
     */
    private Type STRING_LIST_TYPE;

    public ProtobufTypeBuilder() {
        try {
            STRING_LIST_TYPE = ProtobufTypeBuilder.class.getDeclaredField("list").getGenericType();
        } catch (NoSuchFieldException e) {
            // do nothing
        }
    }

    @Override
    public boolean accept(Type type, Class<?> clazz) {
        if (clazz == null) {
            return false;
        }

        if (GeneratedMessageV3.class.isAssignableFrom(clazz)) {
            return true;
        }

        return false;
    }

    @Override
    public TypeDefinition build(Type type, Class<?> clazz, Map<Class<?>, TypeDefinition> typeCache) {
        TypeDefinition typeDefinition = new TypeDefinition(clazz.getName());
        try {
            GeneratedMessageV3.Builder builder = getMessageBuilder(clazz);
            typeDefinition = buildProtobufTypeDefinition(clazz, builder, typeCache);
        } catch (Exception e) {
            logger.info("TypeDefinition build failed.", e);
        }

        return typeDefinition;
    }

    private GeneratedMessageV3.Builder getMessageBuilder(Class<?> requestType) throws Exception {
        GeneratedMessageV3.Builder builder;
        Method method = requestType.getMethod("newBuilder");
        builder = (GeneratedMessageV3.Builder) method.invoke(null, null);
        return builder;
    }

    private TypeDefinition buildProtobufTypeDefinition(Class<?> clazz, GeneratedMessageV3.Builder builder, Map<Class<?>, TypeDefinition> typeCache) {
        TypeDefinition typeDefinition = new TypeDefinition(clazz.getName());
        if (builder == null) {
            return typeDefinition;
        }

        Map<String, TypeDefinition> properties = new HashMap();
        Method[] methods = builder.getClass().getDeclaredMethods();
        for (Method method : methods) {
            String methodName = method.getName();

            if (isSimplePropertySettingMethod(method)) {
                // nest or primitive property
                properties.put(getSimpleFiledName(methodName), TypeDefinitionBuilder.build(method.getGenericParameterTypes()[0], method.getParameterTypes()[0], typeCache));
                continue;
            } else if (isMapPropertyMethod(method)) {
                // map property
                Type type = method.getGenericParameterTypes()[0];
                String fieldName = getMapFieldName(methodName);
                if (validateMapType(type.toString())) {
                    properties.put(fieldName, TypeDefinitionBuilder.build(type, method.getParameterTypes()[0], typeCache));
                    continue;
                } else {
                    throw new IllegalArgumentException("Map protobuf property " + fieldName + "of Type " + type
                            .toString() + " can't be parsed.Map with unString key or ByteString value is not supported.");
                }
            } else if (isListPropertySettingMethod(method)) {
                // list property
                Type type = method.getGenericReturnType();
                String fieldName = getListFieldName(methodName);
                TypeDefinition td;
                if (validateListType(type.toString())) {
                    if (ProtocolStringList.class.isAssignableFrom(method.getReturnType())) {
                        // property defined as "repeated string" transform to ProtocolStringList,
                        // should be build as List<String>.
                        td = TypeDefinitionBuilder.build(STRING_LIST_TYPE, List.class, typeCache);
                    } else {
                        td = TypeDefinitionBuilder.build(type, method.getReturnType(), typeCache);
                    }
                    properties.put(fieldName, td);
                    continue;
                } else {
                    throw new IllegalArgumentException("List protobuf property " + fieldName + "of Type " + type.toString
                            () + " can't be parsed.List of ByteString is not supported");
                }
            }
        }
        typeDefinition.setProperties(properties);
        typeCache.put(clazz, typeDefinition);
        return typeDefinition;
    }

    /**
     * 1. Unsupported List with value type is Bytes <br/>
     * Bytes is a primitive type in Proto, transform to ByteString.class in java<br/>
     *
     * @param typeName
     * @return
     */
    private boolean validateListType(String typeName) {
        Matcher matcher = LIST_PATTERN.matcher(typeName);
        if (!matcher.matches()) {
            return false;
        }

        return !ByteString.class.getName().equals(matcher.group(1));
    }

    /**
     * 1. Unsupported Map with value type is Bytes <br/>
     * 2. Unsupported Map with key type is not String <br/>
     * Bytes is a primitive type in Proto, transform to ByteString.class in java<br/>
     *
     * @param typeName
     * @return
     */
    private boolean validateMapType(String typeName) {
        Matcher matcher = MAP_PATTERN.matcher(typeName);
        if (!matcher.matches()) {
            return false;
        }

        return String.class.getName().equals(matcher.group(1)) && !ByteString.class.getName().equals(matcher.group(2));
    }

    /**
     * get unCollection unMap property name from setting method.<br/>
     * ex:setXXX();<br/>
     *
     * @param methodName
     * @return
     */
    private String getSimpleFiledName(String methodName) {
        return toCamelCase(methodName.substring(3));
    }

    /**
     * get map property name from setting method.<br/>
     * ex: putAllXXX();<br/>
     *
     * @param methodName
     * @return
     */
    private String getMapFieldName(String methodName) {
        return toCamelCase(methodName.substring(6));
    }

    /**
     * get list property name from setting method.<br/>
     * ex： getXXXList()<br/>
     *
     * @param methodName
     * @return
     */
    private String getListFieldName(String methodName) {
        return toCamelCase(methodName.substring(3, methodName.length() - 4));
    }


    private String toCamelCase(String nameString) {
        char[] chars = nameString.toCharArray();
        chars[0] = Character.toLowerCase(chars[0]);
        return new String(chars);
    }

    /**
     * judge nest or primitive property<br/>
     * 1. proto3 grammar ex: string name = 1 <br/>
     * 2. proto3 grammar ex: optional string name =1 <br/>
     * generated setting method setNameValue(String name);
     *
     * @param method
     * @return
     */
    private boolean isSimplePropertySettingMethod(Method method) {
        String methodName = method.getName();
        Class<?>[] types = method.getParameterTypes();

        if (!methodName.startsWith("set") || types.length != 1) {
            return false;
        }

        // filter general setting method
        // 1. - setUnknownFields( com.google.protobuf.UnknownFieldSet unknownFields)
        // 2. - setField(com.google.protobuf.Descriptors.FieldDescriptor field,java.lang.Object value)
        // 3. - setRepeatedField(com.google.protobuf.Descriptors.FieldDescriptor field,int index,java.lang.Object value）
        if (methodName.equals("setField") && types[0].equals(Descriptors.FieldDescriptor.class)) {
            return false;
        }

        if (methodName.equals("setUnknownFields") && types[0].equals(UnknownFieldSet.class)) {
            return false;
        }

        if (methodName.equals("setRepeatedField") && types[0].equals(Descriptors.FieldDescriptor.class)) {
            return false;
        }

        // String property has two setting method.
        // skip setXXXBytes(com.google.protobuf.ByteString value)
        // parse setXXX(String string)
        if (methodName.endsWith("Bytes") && types[0].equals(ByteString.class)) {
            return false;
        }

        // Protobuf property has two setting method.
        // skip setXXX(com.google.protobuf.Builder value)
        // parse setXXX(com.google.protobuf.Message value)
        if (GeneratedMessageV3.Builder.class.isAssignableFrom(types[0])) {
            return false;
        }

        // Enum property has two setting method.
        // skip setXXX(int value)
        // parse setXXX(SomeEnum value)
        if (methodName.endsWith("Value") && types[0] == int.class) {
            return false;
        }

        return true;
    }


    /**
     * judge List property</br>
     * proto3 grammar ex: repeated string names; </br>
     * generated setting method：List<String> getNamesList()
     *
     * @param method
     * @return
     */
    boolean isListPropertySettingMethod(Method method) {
        String methodName = method.getName();
        Class<?> type = method.getReturnType();


        if (!methodName.startsWith("get") || !methodName.endsWith("List")) {
            return false;
        }

        // skip the setting method with Pb entity builder as parameter
        if (methodName.endsWith("BuilderList")) {
            return false;
        }

        // if field name end with List, should skip
        if (!List.class.isAssignableFrom(type)) {
            return false;
        }

        return true;
    }

    /**
     * judge map property</br>
     * proto3 grammar : map<string,string> strName = 1; </br>
     * generated setting method: putAllCards(java.util.Map<String, string> values) </br>
     *
     * @param methodTemp
     * @return
     */
    private boolean isMapPropertyMethod(Method methodTemp) {
        String methodName = methodTemp.getName();
        Class[] parameters = methodTemp.getParameterTypes();
        if (methodName.startsWith("putAll") && parameters.length == 1 && Map.class.isAssignableFrom(parameters[0])) {
            return true;
        }

        return false;
    }
}
