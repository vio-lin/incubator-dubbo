package org.apache.dubbo.common.utils;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;

import com.google.common.collect.Maps;
import com.google.gson.Gson;
import com.google.protobuf.ByteString;
import com.google.protobuf.GeneratedMessageV3;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.MessageOrBuilder;
import com.google.protobuf.ProtocolStringList;
import com.google.protobuf.UnknownFieldSet;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.GeneratedMessageV3.Builder;
import com.google.protobuf.util.JsonFormat;
import com.google.protobuf.util.JsonFormat.Parser;
import com.google.protobuf.util.JsonFormat.Printer;

/**
 * @author guo.lin  2019/4/4
 */
public class ProtobufUtil {
  private List<String> list = null;
  private static Type STRING_LIST_TYPE;

  public ProtobufUtil() {
    try {
      STRING_LIST_TYPE = ProtobufUtil.class.getDeclaredField("list").getGenericType();
    } catch (NoSuchFieldException e) {
      e.printStackTrace();
    }
  }

  public static boolean isSupported(Class<?> clazz) {
    if (clazz == null) {
      return false;
    }

    if (GeneratedMessageV3.class.isAssignableFrom(clazz)) {
      return true;
    }
    return false;
  }

  public static <T> T fromJson(Class<T> requestClass, String json) {
    Builder builder;
    if (!isSupported(requestClass)) {
      return new Gson().fromJson(json, requestClass);
    }
    try {
      builder =  getMessageBuilder(requestClass);
      //TODO 研究下能不能使用同一个
      Parser parser = JsonFormat.parser();
      parser.merge(json, builder);
      return (T) builder.build();
    } catch (Exception e) {
      // DoNothing
      e.printStackTrace();
    }
    return null;
  }

  public static String serialize(Object value) {
    String result;
    try{
      Printer printer = JsonFormat.printer();
      result = printer.print((MessageOrBuilder) value);
    }catch (Exception e){
      result = e.getMessage();
    }
    return  result;
  }

  public static String toJson(Object object) throws InvalidProtocolBufferException {
    Printer printer = JsonFormat.printer();
    return  printer.print((MessageOrBuilder) object);
  }

  public Map<String, ParameterType> getFields(Class<?> requestType) {
    Builder builder = getMessageBuilder(requestType);
    Map<String, ParameterType> typeMap = parseFieldProperty(builder);
    return typeMap;
  }

  private static Builder getMessageBuilder(Class<?> requestType) {
    Builder builder = null;
    try {
      Method method = requestType.getMethod("newBuilder");
      builder = (Builder) method.invoke(null, null);
    } catch (NoSuchMethodException e) {
      e.printStackTrace();
    } catch (IllegalAccessException e) {
      e.printStackTrace();
    } catch (InvocationTargetException e) {
      e.printStackTrace();
    }
    return builder;
  }

  private Map<String, ParameterType> parseFieldProperty(Builder builder) {
    Map<String, ParameterType> typeMap = Maps.newHashMap();
    Method[] allMethods = builder.getClass().getDeclaredMethods();

    for (Method methodTemp : allMethods) {
      String methodName = methodTemp.getName();

      if (isSimplePropertyMethod(methodTemp)) {
        ParameterType parameterType = new ParameterType();
        parameterType.setClazz(methodTemp.getParameterTypes()[0]);
        parameterType.setType(methodTemp.getGenericParameterTypes()[0]);
        typeMap.put(changeFirstCharacterToLow(methodTemp.getName().substring(3)), parameterType);
        continue;
      }

      if (isRepeatedPropertyMethod(methodTemp)) {
        ParameterType parameterType = new ParameterType();
        if (ProtocolStringList.class.isAssignableFrom(methodTemp.getReturnType())) {
          // repeated string 的属性 会被转换成protocolString对象,ServiceDefinition需要被重新定义为List<String>
          parameterType.setClazz(List.class);
          parameterType.setType(STRING_LIST_TYPE);
        } else {
          parameterType.setClazz(methodTemp.getReturnType());
          parameterType.setType(methodTemp.getGenericReturnType());
        }
        typeMap.put(changeFirstCharacterToLow(methodName.substring(3, methodName.length() - 4)), parameterType);
      }

      if (isMapPropertyMethod(methodTemp)) {
        ParameterType parameterType = new ParameterType();
        parameterType.setClazz(methodTemp.getParameterTypes()[0]);
        parameterType.setType(methodTemp.getGenericParameterTypes()[0]);
        typeMap.put(changeFirstCharacterToLow(methodName.substring(6)), parameterType);
      }
    }

    return typeMap;
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
    if (methodName.equals("setField") && types[0].equals(FieldDescriptor.class)) {
      return false;
    }

    if (methodName.equals("setUnknownFields") && types[0].equals(UnknownFieldSet.class)) {
      return false;
    }

    if (methodName.equals("setRepeatedField") && types[0].equals(FieldDescriptor.class)) {
      return false;
    }

    //字符串变量存在 setXXXBytes(com.google.protobuf.ByteString value)的方法，解析传入String的方法
    if (methodName.endsWith("Bytes") && types[0].equals(ByteString.class)) {
      return false;
    }

    // PB Pojo的set方法可以传入Pojo对象或者Builder,解析Message
    if (Builder.class.isAssignableFrom(types[0])) {
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

  public static Object serialization(String data,Class<?> clazz){
    return fromJson(clazz, data);
  }
}
