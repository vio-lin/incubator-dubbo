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

    package io.grpc.health.v1;

import org.apache.dubbo.common.stream.StreamObserver;
import com.google.protobuf.Message;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.concurrent.CompletableFuture;

public interface Health extends org.apache.dubbo.rpc.model.DubboStub {

    String JAVA_SERVICE_NAME = "io.grpc.health.v1.Health";
    String SERVICE_NAME = "grpc.health.v1.Health";

    io.grpc.health.v1.HealthCheckResponse check(io.grpc.health.v1.HealthCheckRequest request);

    default CompletableFuture<io.grpc.health.v1.HealthCheckResponse> checkAsync(io.grpc.health.v1.HealthCheckRequest request){
        return CompletableFuture.completedFuture(check(request));
    }

    /**
    * This server stream type unary method is <b>only</b> used for generated stub to support async unary method.
    * It will not be called if you are NOT using Dubbo3 generated triple stub and <b>DO NOT</b> implement this method.
    */
    default void check(io.grpc.health.v1.HealthCheckRequest request, StreamObserver<io.grpc.health.v1.HealthCheckResponse> responseObserver){
        checkAsync(request).whenComplete((r, t) -> {
            if (t != null) {
                responseObserver.onError(t);
            } else {
                responseObserver.onNext(r);
                responseObserver.onCompleted();
            }
        });
    }


        /**
         * <pre>
         *  Performs a watch for the serving status of the requested service.
         *  The server will immediately send back a message indicating the current
         *  serving status.  It will then subsequently send a new message whenever
         *  the service&#39;s serving status changes.
         * 
         *  If the requested service is unknown when the call is received, the
         *  server will send a message setting the serving status to
         *  SERVICE_UNKNOWN but will &#42;not&#42; terminate the call.  If at some
         *  future point, the serving status of the service becomes known, the
         *  server will send a new message with the service&#39;s serving status.
         * 
         *  If the call terminates with status UNIMPLEMENTED, then clients
         *  should assume this method is not supported and should not retry the
         *  call.  If the call terminates with any other status (including OK),
         *  clients should retry the call with appropriate exponential backoff.
         * </pre>
         */
    void watch(io.grpc.health.v1.HealthCheckRequest request, StreamObserver<io.grpc.health.v1.HealthCheckResponse> responseObserver);




}
