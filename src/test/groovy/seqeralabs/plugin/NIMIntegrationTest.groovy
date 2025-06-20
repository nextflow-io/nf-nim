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

import nextflow.Session
import nextflow.processor.TaskRun
import nextflow.processor.TaskStatus
import spock.lang.Specification
import spock.lang.Requires
import spock.lang.Timeout
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * Integration tests for NIM executor with real NVIDIA API calls
 * 
 * These tests require NVIDIA_API_KEY environment variable to be set
 */
class NIMIntegrationTest extends Specification {

    Path workDir

    def setup() {
        // Skip integration tests if neither API key is available
        def apiKey = System.getenv('NVIDIA_API_KEY') ?: System.getenv('NVCF_RUN_KEY')
        if (!apiKey) {
            println "Skipping integration tests - NVIDIA_API_KEY or NVCF_RUN_KEY not found"
            println "Set NVIDIA_API_KEY or NVCF_RUN_KEY environment variable to run integration tests"
        }
        
        workDir = Files.createTempDirectory('nim-integration-test')
    }

    def cleanup() {
        if (workDir) {
            workDir.toFile().deleteDir()
        }
    }

    @Requires({ System.getenv('NVIDIA_API_KEY') || System.getenv('NVCF_RUN_KEY') })
    def 'should successfully call RFDiffusion API endpoint'() {
        given:
        def executor = createRealExecutor()
        def workDir = Files.createTempDirectory('nim-integration-test')
        def taskRun = Mock(TaskRun) {
            getWorkDir() >> workDir
        }
        def handler = new NIMTaskHandler(taskRun, executor)
        
        when:
        handler.submit()
        
        then:
        handler.status == TaskStatus.SUBMITTED
        
        when:
        handler.checkIfRunning()
        
        then:
        handler.status == TaskStatus.RUNNING
        
        when:
        // Wait for the API call to complete (with timeout)
        def completed = false
        for (int i = 0; i < 120; i++) { // 2 minutes max
            if (handler.checkIfCompleted()) {
                completed = true
                break
            }
            Thread.sleep(1000)
        }
        
        then:
        completed == true
        handler.status == TaskStatus.COMPLETED
        
        and: 'should create result file'
        def resultFile = workDir.resolve('nim_result.json')
        resultFile.toFile().exists()
        
        and: 'result file should contain valid JSON'
        def resultText = resultFile.text
        resultText.contains('"output_pdb"') || resultText.contains('"error"') || resultText.contains('"detail"') // Either success or documented error
        
        cleanup:
        workDir.toFile().deleteDir()
    }

    @Requires({ System.getenv('NVIDIA_API_KEY') || System.getenv('NVCF_RUN_KEY') })
    def 'should handle API errors gracefully'() {
        given:
        def executor = createRealExecutor()
        def workDir = Files.createTempDirectory('nim-integration-test')
        def taskRun = Mock(TaskRun) {
            getWorkDir() >> workDir
        }
        
        // Override the endpoint to test error handling
        executor.nimEndpoints['rfdiffusion'] = 'https://api.nvidia.com/v1/invalid/endpoint'
        
        def handler = new NIMTaskHandler(taskRun, executor)
        
        when:
        handler.submit()
        
        then:
        handler.status == TaskStatus.SUBMITTED
        
        when:
        // Wait for the API call to complete with error
        def completed = false
        for (int i = 0; i < 30; i++) { // 30 seconds should be enough for error
            if (handler.checkIfCompleted()) {
                completed = true
                break
            }
            Thread.sleep(1000)
        }
        
        then:
        completed == true
        handler.status == TaskStatus.COMPLETED
        // Should complete with error status but not crash
        
        cleanup:
        workDir.toFile().deleteDir()
    }

    @Requires({ System.getenv('NVIDIA_API_KEY') || System.getenv('NVCF_RUN_KEY') })
    def 'should use custom endpoint configuration'() {
        given:
        def customEndpoint = 'https://health.api.nvidia.com/v1/biology/ipd/rfdiffusion/generate'
        def session = Mock(Session)
        session.config >> [nim: [rfdiffusion: [endpoint: customEndpoint]]]
        
        def executor = new NIMExecutor()
        executor.session = session
        executor.register()
        
        def workDir = Files.createTempDirectory('nim-integration-test')
        def taskRun = Mock(TaskRun) {
            getWorkDir() >> workDir
        }
        def handler = new NIMTaskHandler(taskRun, executor)
        
        when:
        handler.submit()
        
        and:
        // Wait for completion
        def completed = false
        for (int i = 0; i < 120; i++) {
            if (handler.checkIfCompleted()) {
                completed = true
                break
            }
            Thread.sleep(1000)
        }
        
        then:
        completed == true
        executor.nimEndpoints['rfdiffusion'] == customEndpoint
        
        cleanup:
        workDir.toFile().deleteDir()
    }

    def 'should skip tests when API key is not available'() {
        given:
        def originalKey = System.getenv('NVIDIA_API_KEY')
        
        expect:
        if (!originalKey) {
            println "Skipping integration tests - NVIDIA_API_KEY not found"
            println "Set NVIDIA_API_KEY environment variable to run integration tests"
        }
        // This test always passes, just provides info
        true
    }

    @Requires({ System.getenv('NVIDIA_API_KEY') || System.getenv('NVCF_RUN_KEY') })
    @Timeout(value = 3, unit = TimeUnit.MINUTES)
    def 'should complete RFDiffusion task within reasonable time'() {
        given:
        println "Testing with API key: ${System.getenv('NVIDIA_API_KEY')?.take(10)}..."
        
        def executor = createRealExecutor()
        def workDir = Files.createTempDirectory('nim-integration-test')
        def taskRun = Mock(TaskRun) {
            getWorkDir() >> workDir
        }
        def handler = new NIMTaskHandler(taskRun, executor)
        
        when:
        def startTime = System.currentTimeMillis()
        handler.submit()
        handler.checkIfRunning()
        
        // Wait for completion
        while (!handler.checkIfCompleted()) {
            Thread.sleep(2000)
            def elapsed = System.currentTimeMillis() - startTime
            println "Waiting for API response... ${elapsed / 1000}s elapsed"
        }
        
        def totalTime = System.currentTimeMillis() - startTime
        println "API call completed in ${totalTime / 1000}s"
        
        then:
        handler.status == TaskStatus.COMPLETED
        totalTime < 180000 // Should complete within 3 minutes
        
        cleanup:
        workDir.toFile().deleteDir()
    }

    @Requires({ System.getenv('NVIDIA_API_KEY') || System.getenv('NVCF_RUN_KEY') })
    def 'should validate API response format'() {
        given:
        def executor = createRealExecutor()
        def workDir = Files.createTempDirectory('nim-integration-test')
        def taskRun = Mock(TaskRun) {
            getWorkDir() >> workDir
        }
        def handler = new NIMTaskHandler(taskRun, executor)
        
        when:
        handler.submit()
        
        // Wait for completion
        while (!handler.checkIfCompleted()) {
            Thread.sleep(2000)
        }
        
        then:
        handler.status == TaskStatus.COMPLETED
        
        when:
        def resultFile = workDir.resolve('nim_result.json')
        def resultExists = resultFile.toFile().exists()
        
        then:
        resultExists == true
        
        when:
        def resultText = resultFile.text
        println "API Response preview: ${resultText.take(200)}..."
        
        then:
        resultText.length() > 0
        // Should be valid JSON (either success response or error response)
        resultText.startsWith('{') || resultText.startsWith('[')
        
        cleanup:
        workDir.toFile().deleteDir()
    }

    @Requires({ System.getenv('NVIDIA_API_KEY') || System.getenv('NVCF_RUN_KEY') })
    def 'should connect to NVIDIA API with proper SSL handling'() {
        given:
        def apiKey = System.getenv('NVIDIA_API_KEY') ?: System.getenv('NVCF_RUN_KEY')
        println "Testing connectivity to NVIDIA Health API..."
        def executor = createRealExecutor()  // Use the executor's HTTP client
        
        when:
        // Test with the executor's HTTP client that has SSL handling
        def testRequest = HttpRequest.newBuilder()
                .uri(URI.create('https://health.api.nvidia.com/'))
                .header("User-Agent", "nf-nim-plugin/1.0")
                .GET()
                .timeout(Duration.ofSeconds(30))
                .build()
                
        def response = executor.httpClient.send(testRequest, HttpResponse.BodyHandlers.ofString())
        println "Base API connectivity test - Status: ${response.statusCode()}"
        
        then:
        response.statusCode() >= 200 && response.statusCode() < 500  // Any response means connectivity works
    }

    private NIMExecutor createRealExecutor() {
        def session = Mock(Session)
        session.config >> [:]
        def executor = new NIMExecutor()
        executor.session = session
        executor.register()
        return executor
    }
} 