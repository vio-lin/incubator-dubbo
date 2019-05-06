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
package org.apache.dubbo.metadata.definition.builder;

import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.dubbo.metadata.definition.TypeDefinitionBuilder;
import org.apache.dubbo.metadata.definition.model.TypeDefinition;

import com.google.protobuf.ByteString;
import com.google.protobuf.Descriptors;
import com.google.protobuf.GeneratedMessageV3;
import com.google.protobuf.ProtocolStringList;
import com.google.protobuf.UnknownFieldSet;

public class ProtobufTypeBuilder implements TypeBuilder {
    private static final Pattern MAP_PATTERN = Pattern.compile("^java[.]util[.]Map<(\\w+[[.$]\\w*]+), (\\w+[[.$]\\w*]+)>$");
    private static final Pattern LIST_PATTERN = Pattern.compile("^java[.]util[.]List<(\\w+([.$]\\w*)+)>$");
    private static final List<String> list = null;
    //TODO 这里需要获取一个 GenericParameterType用户替换TypeString. 只发现可以从定义field中拿到
    private Type STRING_LIST_TYPE;

    public ProtobufTypeBuilder() {
        try {
            STRING_LIST_TYPE = ProtobufTypeBuilder.class.getDeclaredField("list").getGenericType();
        } catch (NoSuchFieldException e) {
            e.printStackTrace();
        }
    }
    @Override
    public boolean accept(Type type, Class<?> clazz) {
        if (clazz == null) {
            return false;
        }

        if(GeneratedMessageV3.class.isAssignableFrom(clazz)) {
            return true;
        }

        return false;
    }

    @Override
    public TypeDefinition build(Type type, Class<?> clazz, Map<Class<?>, TypeDefinition> typeCache) {
     TypeDefinition typeDefinition = null;
        GeneratedMessageV3.Builder builder;
        try {
            builder = getMessageBuilder(clazz);
            typeDefinition =  parseFieldProperties(clazz,builder,typeCache);
        } catch (Exception e) {
            e.printStackTrace();
        }

        return typeDefinition;
    }

    private GeneratedMessageV3.Builder getMessageBuilder(Class<?> requestType) throws Exception {
        GeneratedMessageV3.Builder builder;
        Method method = requestType.getMethod("newBuilder");
        builder = (GeneratedMessageV3.Builder) method.invoke(null, null);
        return builder;
    }

    private TypeDefinition parseFieldProperties(Class<?> clazz, GeneratedMessageV3.Builder builder, Map<Class<?>, TypeDefinition> typeCache) {
        if (builder == null) {
            return null;
        }

        TypeDefinition typeDefinition = new TypeDefinition(clazz.getName());
        Map<String,TypeDefinition> properties = new HashMap();
        Method[] allMethods = builder.getClass().getDeclaredMethods();

        for (Method methodTemp : allMethods) {
            String methodName = methodTemp.getName();

            if (isSimplePropertyMethod(methodTemp)) {
                properties.put(getSimpleFiledName(methodTemp), TypeDefinitionBuilder.build(methodTemp.getGenericParameterTypes()[0], methodTemp.getParameterTypes()[0], typeCache));
            } else if (isMapPropertyMethod(methodTemp)) {
                Type type = methodTemp.getGenericParameterTypes()[0];
                String fieldName = getMapFieldName(methodName);
                if (validateMapType(type.toString())) {
                    properties.put(fieldName, TypeDefinitionBuilder.build(type, methodTemp.getParameterTypes()[0], typeCache));
                } else {
                    //TODO add some solution
                    throw new IllegalArgumentException("Map protobuf property " + fieldName + "of Type "+type.toString()+" can't be parsed");
                }
            } else if (isRepeatedPropertyMethod(methodTemp)) {
                Type type = methodTemp.getGenericReturnType();
                String fieldName = getListFieldName(methodName);
                TypeDefinition td;
                if(validateListType(type.toString())){
                    if (ProtocolStringList.class.isAssignableFrom(methodTemp.getReturnType())) {
                        // repeated string 的属性 会被转换成protocolString对象,ServiceDefinition需要被重新定义为List<String>
                        td = TypeDefinitionBuilder.build(STRING_LIST_TYPE, List.class, typeCache);
                    } else {
                        td = TypeDefinitionBuilder.build(type, methodTemp.getReturnType(), typeCache);
                    }
                    properties.put(fieldName, td);
                }else{
                    //TODO add some solution
                    throw new IllegalArgumentException("Map protobuf property " + fieldName + "of Type "+type.toString()+" can't be parsed");
                }
            }
        }
        typeDefinition.setProperties(properties);
        typeCache.put(clazz,typeDefinition);
        return typeDefinition;
    }

    /**
     * 1. 不支持 List中Value为Bytes的list <br/>
     *
     * @param typeName
     * @return
     */
    private boolean validateListType(String typeName) {
        Matcher matcher = LIST_PATTERN.matcher(typeName);
        if(!matcher.matches()){
            return false;
        }

        return !ByteString.class.getName().equals(matcher.group(1));
    }

    /**
     * 1. 不支持 PB中Value 为Bytes的map <br/>
     * 2. 不支持 Key为非 String类型的map<br/>
     *
     * @param typeName
     * @return
     */
    private boolean validateMapType(String typeName) {
        Matcher matcher = MAP_PATTERN.matcher(typeName);
        if (!matcher.matches()) {
            return false;
        }

        return String.class.getName().equals(matcher.group(1))&&!ByteString.class.getName().equals(matcher.group(2));
    }

    /**
     * 一般属性的set方法 ex:setXXX();<br/>
     * 获取第三位之后的字符串
     *
     * @param method
     * @return
     */
    private String getSimpleFiledName(Method method) {
        return changeFirstCharacterToLow(method.getName().substring(3));
    }

    /**
     * map属性的set方法 ex: putAllXXX();<br/>
     *
     * @param methodName
     * @return
     */
    private String getMapFieldName(String methodName) {
        return changeFirstCharacterToLow(methodName.substring(6));
    }

    /**
     * list属性的set方法 ex： getXXXList()
     *
     * @param methodName
     * @return
     */
    private String getListFieldName(String methodName) {
        return changeFirstCharacterToLow(methodName.substring(3, methodName.length() - 4));
    }


    private String changeFirstCharacterToLow(String substring) {
        StringBuilder sb = new StringBuilder();
        sb.append(Character.toLowerCase(substring.charAt(0)));
        sb.append(substring.substring(1));
        return sb.toString();
    }

    /**
     * 简单属性判断<br/>
     * 1. 对应 proto3 的默认属性 ex: string name = 1 <br/>
     * 2. 对应 proto2 的optional 和 required ex: optional string name =1 <br/>
     * 生成java代码 setNameValue(String name);
     *
     * @param method
     * @return
     */
    private boolean isSimplePropertyMethod(Method method) {
        String methodName = method.getName();
        Class<?>[] types = method.getParameterTypes();
        if (!methodName.startsWith("set") || types.length != 1) {
            return false;
        }

        // 过滤自动生成的几个的方法
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

        //字符串变量存在 setXXXBytes(com.google.protobuf.ByteString value)的方法，解析传入String的方法
        if (methodName.endsWith("Bytes") && types[0].equals(ByteString.class)) {
            return false;
        }

        // PB Pojo的set方法可以传入Pojo对象或者Builder,解析Message
        if (GeneratedMessageV3.Builder.class.isAssignableFrom(types[0])) {
            return false;
        }

        // 当property类型为enum的时候，会多生成一个setXXXValue(int value)的方法
        if (methodName.endsWith("Value") && types[0] == int.class) {
            return false;
        }

        return true;
    }


    /**
     * List属性解析</br>
     * proto文件中属性标志为 repeated. ex: repeated string names;
     * 生成java代码：List<String> getNamesList()
     *
     * @param method
     * @return
     */
    boolean isRepeatedPropertyMethod(Method method) {
        String methodName = method.getName();
        Class<?> type = method.getReturnType();

        // 非List方法的逻辑
        if (!methodName.startsWith("get") || !methodName.endsWith("List")) {
            return false;
        }

        // getCardsOrBuilderList() 该方法返回Builder的对象
        if (methodName.endsWith("BuilderList")) {
            return false;
        }

        // 防止名字中存在List结尾的一般属性
        if (List.class.isAssignableFrom(type)) {
            return true;
        }
        return false;
    }

    /**
     * Map属性方法解析</br>
     * 对应proto3 语法: map<string,string> strName = 1;
     * 生成java方法: putAllCards(java.util.Map<String, string> values)
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
