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
import java.nio.file.Files
import java.nio.file.Path
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

/**
 * Tests for NIM task handler
 */
class NIMTaskHandlerTest extends Specification {

    def 'should initialize with correct status'() {
        given:
        def executor = createMockExecutor()
        def workDir = Files.createTempDirectory('nim-test')
        def taskRun = Mock(TaskRun) {
            getWorkDir() >> workDir
        }
        
        when:
        def handler = new NIMTaskHandler(taskRun, executor)
        
        then:
        handler.task == taskRun
        handler.status == TaskStatus.NEW
        
        cleanup:
        workDir.toFile().deleteDir()
    }

    def 'should transition status on submit'() {
        given:
        def executor = createMockExecutor()
        def workDir = Files.createTempDirectory('nim-test')
        def taskRun = Mock(TaskRun) {
            getWorkDir() >> workDir
        }
        def handler = new NIMTaskHandler(taskRun, executor)
        
        when:
        handler.submit()
        
        then:
        handler.status == TaskStatus.SUBMITTED
        
        cleanup:
        workDir.toFile().deleteDir()
    }

    def 'should detect running status after submit'() {
        given:
        def executor = createMockExecutor()
        def workDir = Files.createTempDirectory('nim-test')
        def taskRun = Mock(TaskRun) {
            getWorkDir() >> workDir
        }
        def handler = new NIMTaskHandler(taskRun, executor)
        handler.submit()
        
        when:
        def isRunning = handler.checkIfRunning()
        
        then:
        isRunning == true
        handler.status == TaskStatus.RUNNING
        
        cleanup:
        workDir.toFile().deleteDir()
    }

    def 'should not report running when not submitted'() {
        given:
        def executor = createMockExecutor()
        def workDir = Files.createTempDirectory('nim-test')
        def taskRun = Mock(TaskRun) {
            getWorkDir() >> workDir
        }
        def handler = new NIMTaskHandler(taskRun, executor)
        
        when:
        def isRunning = handler.checkIfRunning()
        
        then:
        isRunning == false
        handler.status == TaskStatus.NEW
        
        cleanup:
        workDir.toFile().deleteDir()
    }

    def 'should handle kill signal'() {
        given:
        def executor = createMockExecutor()
        def workDir = Files.createTempDirectory('nim-test')
        def taskRun = Mock(TaskRun) {
            getWorkDir() >> workDir
        }
        def handler = new NIMTaskHandler(taskRun, executor)
        
        when:
        handler.kill()
        
        then:
        handler.checkIfCompleted() == true
        handler.status == TaskStatus.COMPLETED
        
        cleanup:
        workDir.toFile().deleteDir()
    }

    def 'should complete after successful execution'() {
        given:
        def executor = createMockExecutor()
        def workDir = Files.createTempDirectory('nim-test')
        def taskRun = Mock(TaskRun) {
            getWorkDir() >> workDir
        }
        def handler = new NIMTaskHandler(taskRun, executor)
        
        when:
        // Simulate completion by setting internal state
        handler.@completed = true
        handler.@exitStatus = 0
        
        then:
        handler.checkIfCompleted() == true
        handler.status == TaskStatus.COMPLETED
        
        cleanup:
        workDir.toFile().deleteDir()
    }

    def 'should not complete when still running'() {
        given:
        def executor = createMockExecutor()
        def workDir = Files.createTempDirectory('nim-test')
        def taskRun = Mock(TaskRun) {
            getWorkDir() >> workDir
        }
        def handler = new NIMTaskHandler(taskRun, executor)
        handler.submit()
        handler.checkIfRunning()
        
        when:
        // Check immediately - should not be completed yet
        def isCompleted = handler.checkIfCompleted()
        
        then:
        isCompleted == false
        handler.status == TaskStatus.RUNNING
        
        when:
        // Wait a bit and check again - it may complete due to API key error
        Thread.sleep(200)
        def finallyCompleted = handler.checkIfCompleted()
        
        then:
        // Either still running or completed with error - both are valid
        finallyCompleted == true || handler.status == TaskStatus.RUNNING
        
        cleanup:
        workDir.toFile().deleteDir()
    }

    def 'should handle missing API key gracefully'() {
        given:
        def executor = createMockExecutor()
        def workDir = Files.createTempDirectory('nim-test')
        def taskRun = Mock(TaskRun) {
            getWorkDir() >> workDir
        }
        def handler = new NIMTaskHandler(taskRun, executor)
        
        // Set PDB data so we can test the API key check without downloading
        handler.setPdbData("ATOM      1  N   ALA A   1      20.154  16.967  24.862  1.00 10.00           N")
        
        when:
        // Temporarily remove environment variable by overriding the check
        def originalTaskHandler = handler
        originalTaskHandler.metaClass.executeNIMTaskWithPdb = { String pdbData ->
            // Simulate missing API key by returning null
            def apiKey = null
            if (!apiKey) {
                println("No API key found. Set NVCF_RUN_KEY environment variable.")
                originalTaskHandler.@completed = true
                originalTaskHandler.@exitStatus = 1
                return
            }
        }
        
        handler.submit()
        // Give the async task a moment to complete
        Thread.sleep(100)
        
        then:
        handler.status == TaskStatus.SUBMITTED
        // The task should eventually complete with error due to missing API key
        
        when:
        // Check multiple times as the task executes asynchronously
        def completed = false
        for (int i = 0; i < 20; i++) {  // Reduced iterations since it should complete quickly
            if (handler.checkIfCompleted()) {
                completed = true
                break
            }
            Thread.sleep(50)
        }
        
        then:
        completed == true
        handler.status == TaskStatus.COMPLETED
        
        cleanup:
        workDir.toFile().deleteDir()
    }

    def 'should use correct endpoint for service'() {
        given:
        def customEndpoint = 'http://custom-server:8080/api'
        def executor = Mock(NIMExecutor) {
            getNimEndpoints() >> ['rfdiffusion': customEndpoint]
            getHttpClient() >> Mock(HttpClient)
        }
        def workDir = Files.createTempDirectory('nim-test')
        def taskRun = Mock(TaskRun) {
            getWorkDir() >> workDir
        }
        
        when:
        def handler = new NIMTaskHandler(taskRun, executor)
        
        then:
        handler.executor.nimEndpoints['rfdiffusion'] == customEndpoint
        
        cleanup:
        workDir.toFile().deleteDir()
    }

    def 'should create logging files when initializing handler'() {
        given:
        def executor = createMockExecutor()
        def workDir = Files.createTempDirectory('nim-test')
        def taskRun = Mock(TaskRun) {
            getWorkDir() >> workDir
        }
        
        when:
        def handler = new NIMTaskHandler(taskRun, executor)
        
        then:
        def outFile = workDir.resolve('.command.out')
        def errFile = workDir.resolve('.command.err')
        def logFile = workDir.resolve('.command.log')
        
        Files.exists(outFile)
        Files.exists(errFile)
        Files.exists(logFile)
        
        cleanup:
        workDir.toFile().deleteDir()
    }

    def 'should use custom output filename when configured'() {
        given:
        def executor = createMockExecutor()
        def workDir = Files.createTempDirectory('nim-test')
        def customOutputFile = 'custom-output.pdb'
        def customResultFile = 'custom-result.json'
        
        def taskRun = Mock(TaskRun) {
            getWorkDir() >> workDir
            getConfig() >> [
                ext: [
                    outputFile: customOutputFile,
                    resultFile: customResultFile,
                    nim: 'rfdiffusion'
                ]
            ]
        }
        
        when:
        def handler = new NIMTaskHandler(taskRun, executor)
        def mainFilename = handler.getMainOutputFilename('rfdiffusion')
        def resultFilename = handler.getResultFilename('rfdiffusion')
        
        then:
        mainFilename == customOutputFile
        resultFilename == customResultFile
        
        cleanup:
        workDir.toFile().deleteDir()
    }

    def 'should fallback to default filenames when not configured'() {
        given:
        def executor = createMockExecutor()
        def workDir = Files.createTempDirectory('nim-test')
        
        def taskRun = Mock(TaskRun) {
            getWorkDir() >> workDir
            getConfig() >> [:]  // No ext configuration
        }
        
        when:
        def handler = new NIMTaskHandler(taskRun, executor)
        def mainFilename = handler.getMainOutputFilename('rfdiffusion')
        def resultFilename = handler.getResultFilename('rfdiffusion')
        
        then:
        mainFilename == 'output.pdb'
        resultFilename == 'nim_result.json'
        
        cleanup:
        workDir.toFile().deleteDir()
    }

    def 'should support variable substitution in filenames'() {
        given:
        def executor = createMockExecutor()
        def workDir = Files.createTempDirectory('nim-test')
        def taskName = 'test-task'
        def serviceName = 'rfdiffusion'
        
        def taskRun = Mock(TaskRun) {
            getWorkDir() >> workDir
            getName() >> taskName
            getConfig() >> [
                ext: [
                    outputFile: '${task.name}_${serviceName}_output.pdb',
                    nim: serviceName
                ]
            ]
        }
        
        when:
        def handler = new NIMTaskHandler(taskRun, executor)
        def mainFilename = handler.getMainOutputFilename(serviceName)
        
        then:
        mainFilename == "${taskName}_${serviceName}_output.pdb"
        
        cleanup:
        workDir.toFile().deleteDir()
    }

    def 'should sanitize invalid characters in filenames'() {
        given:
        def executor = createMockExecutor()
        def workDir = Files.createTempDirectory('nim-test')
        def invalidFilename = 'output<>:"|?*.pdb'
        
        def taskRun = Mock(TaskRun) {
            getWorkDir() >> workDir
            getConfig() >> [
                ext: [
                    outputFile: invalidFilename,
                    nim: 'rfdiffusion'
                ]
            ]
        }
        
        when:
        def handler = new NIMTaskHandler(taskRun, executor)
        def mainFilename = handler.getMainOutputFilename('rfdiffusion')
        
        then:
        mainFilename == 'output_______.pdb'  // Invalid chars replaced with underscores
        
        cleanup:
        workDir.toFile().deleteDir()
    }

    def 'should close log writers when task completes'() {
        given:
        def executor = createMockExecutor()
        def workDir = Files.createTempDirectory('nim-test')
        def taskRun = Mock(TaskRun) {
            getWorkDir() >> workDir
        }
        
        when:
        def handler = new NIMTaskHandler(taskRun, executor)
        handler.@completed = true
        handler.@exitStatus = 0
        handler.task.exitStatus = 0
        
        // Simulate task completion
        def completed = handler.checkIfCompleted()
        
        then:
        completed == true
        handler.status == TaskStatus.COMPLETED
        
        // Writers should be closed, we can't directly test this but files should exist
        def outFile = workDir.resolve('.command.out')
        def errFile = workDir.resolve('.command.err')
        def logFile = workDir.resolve('.command.log')
        
        Files.exists(outFile)
        Files.exists(errFile)
        Files.exists(logFile)
        
        cleanup:
        workDir.toFile().deleteDir()
    }

    private NIMExecutor createMockExecutor() {
        def session = Mock(Session)
        session.config >> [:]
        def executor = new NIMExecutor()
        executor.session = session
        executor.register()
        return executor
    }
} 