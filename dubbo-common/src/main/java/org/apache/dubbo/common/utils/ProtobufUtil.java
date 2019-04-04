package org.apache.dubbo.common.utils;

import com.google.gson.Gson;
import com.google.protobuf.GeneratedMessageV3;
import com.google.protobuf.GeneratedMessageV3.Builder;
import com.google.protobuf.MessageOrBuilder;
import com.google.protobuf.util.JsonFormat;
import com.google.protobuf.util.JsonFormat.Parser;
import com.google.protobuf.util.JsonFormat.Printer;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * @author guo.lin 2019/4/4
 */
public class ProtobufUtil {

  public static boolean isSupported(Class<?> clazz) {
    if (clazz == null) {
      return false;
    }

    if (GeneratedMessageV3.class.isAssignableFrom(clazz)) {
      return true;
    }
    return false;
  }

  public static <T> T deserialize(String json, Class<T> requestClass) {
    Builder builder;
    if (!isSupported(requestClass)) {
      return new Gson().fromJson(json, requestClass);
    }
    try {
      builder = getMessageBuilder(requestClass);
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
    try {
      Printer printer = JsonFormat.printer();
      result = printer.print((MessageOrBuilder) value);
    } catch (Exception e) {
      result = e.getMessage();
    }
    return result;
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
}
