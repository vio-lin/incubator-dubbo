package org.apache.dubbo.demo.protobuf.provider;

import org.apache.dubbo.demo.protobuf.api.GooglePb.GooglePBRequestType;
import org.apache.dubbo.demo.protobuf.api.GooglePb.GooglePBResponseType;
import org.apache.dubbo.demo.protobuf.api.ProtobufDemoService;

/**
 * @author guo.lin  2019/4/8
 */
public class ProtobufDemoServiceImpl implements ProtobufDemoService {
  @Override
  public GooglePBResponseType sayHello(GooglePBRequestType request) {
    GooglePBResponseType response = GooglePBResponseType.newBuilder().setResponse("message from server :"+request.getReq()).build();
    return response;
  }
}
