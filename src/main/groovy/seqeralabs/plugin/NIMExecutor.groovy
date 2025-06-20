/*
 * Copyright 2025, Seqera Labs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package seqeralabs.plugin

import groovy.json.JsonBuilder
import groovy.json.JsonSlurper
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import nextflow.executor.Executor
import nextflow.processor.TaskHandler
import nextflow.processor.TaskMonitor
import nextflow.processor.TaskPollingMonitor
import nextflow.processor.TaskRun
import nextflow.processor.TaskStatus
import nextflow.util.ServiceName
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import nextflow.util.Duration
import java.time.Duration as JavaDuration

/**
 * NVIDIA NIM (NVIDIA Inference Microservices) executor for Nextflow
 *
 * Supports execution of RFDiffusion tasks via NVIDIA's API service
 */
@CompileStatic
@ServiceName('nim')
@Slf4j
class NIMExecutor extends Executor {

    Map<String, String> nimEndpoints
    HttpClient httpClient

    @Override
    void register() {
        super.register()
        
        // Configure endpoint for RFDiffusion NIM service using NVIDIA API
        this.nimEndpoints = [
            'rfdiffusion': 'https://api.nvidia.com/v1/biology/ipd/rfdiffusion/generate'
        ]
        
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(JavaDuration.ofSeconds(30))
                .build()
                
        log.info "NIM executor registered with RFDiffusion endpoint"
    }

    @Override
    TaskHandler createTaskHandler(TaskRun task) {
        return new NIMTaskHandler(task, this)
    }

    @Override
    TaskMonitor createTaskMonitor() {
        return TaskPollingMonitor.create(session, 'nim', Duration.of('5sec'))
    }
} 