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

package io.grpc.reflection.v1alpha;

import org.apache.dubbo.common.stream.StreamObserver;
import com.google.protobuf.Message;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.concurrent.CompletableFuture;

public interface ServerReflection extends org.apache.dubbo.rpc.model.DubboStub {

    String JAVA_SERVICE_NAME = "io.grpc.reflection.v1alpha.ServerReflection";
    String SERVICE_NAME = "grpc.reflection.v1alpha.ServerReflection";



    /**
     * <pre>
     *  The reflection service is structured as a bidirectional stream, ensuring
     *  all related requests go to a single server.
     * </pre>
     */
    StreamObserver<io.grpc.reflection.v1alpha.ServerReflectionRequest> serverReflectionInfo(StreamObserver<io.grpc.reflection.v1alpha.ServerReflectionResponse> responseObserver);



}
