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
import nextflow.processor.TaskHandler
import nextflow.processor.TaskRun
import nextflow.processor.TaskStatus
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * Task handler for NIM tasks
 */
@CompileStatic
class NIMTaskHandler extends TaskHandler {

    private final NIMExecutor executor
    private volatile boolean completed = false
    private volatile int exitStatus = 0

    NIMTaskHandler(TaskRun task, NIMExecutor executor) {
        super(task)
        this.executor = executor
    }

    @Override
    void submit() {
        status = TaskStatus.NEW
        
        // Execute the NIM task asynchronously
        Thread.start {
            try {
                executeNIMTask()
            } catch (Exception e) {
                println("Error executing NIM task: ${e.message}")
                completed = true
                exitStatus = 1
            }
        }
        
        status = TaskStatus.SUBMITTED
    }

    @Override
    boolean checkIfRunning() {
        if (status == TaskStatus.SUBMITTED) {
            status = TaskStatus.RUNNING
            return true
        }
        return status == TaskStatus.RUNNING
    }

    @Override
    boolean checkIfCompleted() {
        if (completed) {
            status = TaskStatus.COMPLETED
            return true
        }
        return false
    }

    @Override
    void kill() {
        completed = true
        exitStatus = 130 // SIGINT
    }

    private void executeNIMTask() {
        // Get the NIM service type
        String nimService = 'rfdiffusion' // default

        // Get API key from environment
        def apiKey = System.getenv('NVIDIA_API_KEY')
        
        if (!apiKey) {
            println("NVIDIA_API_KEY not found in environment")
            completed = true
            exitStatus = 1
            return
        }

        def endpoint = executor.nimEndpoints[nimService]
        if (!endpoint) {
            println("Unknown NIM service: ${nimService}")
            completed = true
            exitStatus = 1
            return
        }

        println("Executing NIM task: ${nimService}")

        try {
            // Build request body for RFDiffusion
            def requestData = [
                algorithm_version: "1.1.0",
                inference_type: "ddim",
                num_steps: 50,
                inference_input: [
                    length: 100,
                    hotspots: "1-10,50-60"
                ]
            ]
            def requestBody = new JsonBuilder(requestData).toString()

            // Create HTTP request
            def request = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer ${apiKey}")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .timeout(Duration.ofMinutes(5))
                    .build()

            // Send request
            def response = executor.httpClient.send(request, HttpResponse.BodyHandlers.ofString())

            if (response.statusCode() == 200) {
                // Parse response
                def responseData = new JsonSlurper().parseText(response.body() as String)
                
                // Save results to work directory
                def resultFile = task.workDir.resolve('nim_result.json')
                resultFile.text = new JsonBuilder(responseData).toPrettyString()
                
                println("NIM task completed successfully")
                completed = true
                exitStatus = 0
            } else {
                println("NIM API request failed with status: ${response.statusCode()}")
                println("Response: ${response.body()}")
                completed = true
                exitStatus = 1
            }

        } catch (Exception e) {
            println("Error executing NIM request: ${e.message}")
            completed = true
            exitStatus = 1
        }
    }
} 