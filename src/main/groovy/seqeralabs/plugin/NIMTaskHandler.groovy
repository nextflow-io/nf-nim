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
import java.nio.file.Files

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
        // Check for API key
        def apiKey = System.getenv('NVCF_RUN_KEY')
        if (!apiKey) {
            println("No API key found. Set NVCF_RUN_KEY environment variable.")
            completed = true
            exitStatus = 1
            return
        }

        def endpoint = executor.nimEndpoints['rfdiffusion']
        if (!endpoint) {
            println("No endpoint configured for rfdiffusion")
            completed = true
            exitStatus = 1
            return
        }
        
        println("Using endpoint: ${endpoint}")

        println("Executing NIM task: rfdiffusion")

        try {
            // Build request body with format matching the working example
            def requestData = [
                input_pdb: """ATOM      1  N   MET A   1      20.154  16.977  27.083  1.00 35.88           N  
ATOM      2  CA  MET A   1      19.030  16.183  26.502  1.00 35.88           C  
ATOM      3  C   MET A   1      18.758  16.623  25.060  1.00 35.88           C  
ATOM      4  O   MET A   1      19.663  17.024  24.335  1.00 35.88           O  
ATOM      5  CB  MET A   1      19.353  14.685  26.502  1.00 35.88           C""",
                contigs: "A20-60/0 50-100",
                hotspot_res: ["A50", "A51", "A52", "A53", "A54"],
                diffusion_steps: 15
            ]
            def requestBody = new JsonBuilder(requestData).toString()
            
            // Use curl with the correct format
            def tempFile = Files.createTempFile("nim_request", ".json")
            tempFile.text = requestBody
            
            def curlCommand = [
                'curl', '-w', '%{http_code}',
                '--connect-timeout', '30',
                '--max-time', '300',
                '--show-error',  // Show error details
                '-H', "Content-Type: application/json",
                '-H', "Authorization: Bearer ${apiKey}",
                '-H', "User-Agent: nf-nim-plugin/1.0",
                '-H', "nvcf-poll-seconds: 300",  // Add the polling header from example
                '-d', "@${tempFile.toString()}",
                endpoint
            ]
            
            println "Executing curl command to NIM API..."
            println "Command: ${curlCommand.join(' ')}"
            def processBuilder = new ProcessBuilder(curlCommand as String[])
            def process = processBuilder.start()
            
            def output = new StringBuilder()
            def error = new StringBuilder()
            
            process.inputStream.eachLine { line ->
                output.append(line).append('\n')
            }
            process.errorStream.eachLine { line ->
                error.append(line).append('\n')
            }
            
            def exitCode = process.waitFor()
            tempFile.delete()  // Clean up temp file
            
            println "Curl exit code: ${exitCode}"
            println "Curl output: ${output.toString()}"
            println "Curl error: ${error.toString()}"
            
            if (exitCode == 0) {
                def responseText = output.toString()
                // Extract HTTP status code (last 3 digits)
                def statusPattern = ~/(\d{3})$/
                def statusMatcher = statusPattern.matcher(responseText)
                def statusCode = statusMatcher.find() ? Integer.parseInt(statusMatcher.group(1)) : 0
                def responseBody = statusMatcher.find() ? responseText.substring(0, responseText.length() - 3) : responseText
                
                if (statusCode == 200 || statusCode == 202) {
                    // Save results to work directory
                    def resultFile = task.workDir.resolve('nim_result.json')
                    resultFile.text = responseBody
                    
                    println("NIM task completed successfully via curl")
                    completed = true
                    exitStatus = 0
                } else {
                    println("NIM API request failed with status: ${statusCode}")
                    println("Response: ${responseBody}")
                    completed = true
                    exitStatus = 1
                }
            } else {
                println("Curl command failed with exit code: ${exitCode}")
                println("Error: ${error.toString()}")
                completed = true
                exitStatus = 1
            }

        } catch (Exception e) {
            println("Error executing NIM request: ${e.message}")
            e.printStackTrace()  // More detailed error info
            completed = true
            exitStatus = 1
        }
    }
}
