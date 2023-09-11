// Generated by the protocol buffer compiler.  DO NOT EDIT!
// source: reflectionV1Alpha.proto

package io.grpc.reflection.v1alpha;

public interface ErrorResponseOrBuilder extends
    // @@protoc_insertion_point(interface_extends:grpc.reflection.v1alpha.ErrorResponse)
    com.google.protobuf.MessageOrBuilder {

  /**
   * <pre>
   * This field uses the error codes defined in grpc::StatusCode.
   * </pre>
   *
   * <code>int32 error_code = 1;</code>
   * @return The errorCode.
   */
  int getErrorCode();

  /**
   * <code>string error_message = 2;</code>
   * @return The errorMessage.
   */
  String getErrorMessage();
  /**
   * <code>string error_message = 2;</code>
   * @return The bytes for errorMessage.
   */
  com.google.protobuf.ByteString
      getErrorMessageBytes();
}
