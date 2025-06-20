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
import java.nio.file.Path
import java.nio.file.Files

/**
 * Tests for NIM executor
 */
class NIMExecutorTest extends Specification {

    def 'should create NIM executor with NVIDIA API endpoints'() {
        given:
        def session = Mock(Session)
        session.config >> [:]
        
        when:
        def executor = new NIMExecutor()
        executor.session = session
        executor.register()
        
        then:
        executor.nimEndpoints['rfdiffusion'] == 'https://api.nvidia.com/v1/biology/ipd/rfdiffusion/generate'
        executor.nimEndpoints.size() == 1
    }

    def 'should create task handler'() {
        given:
        def session = Mock(Session)
        session.config >> [:]
        def executor = new NIMExecutor()
        executor.session = session
        executor.register()
        
        def taskRun = Mock(TaskRun)
        
        when:
        def handler = executor.createTaskHandler(taskRun)
        
        then:
        handler instanceof NIMTaskHandler
    }

    def 'should use custom endpoint from config'() {
        given:
        def customEndpoint = 'http://custom-nim-server:8080/biology/ipd/rfdiffusion/generate'
        def session = Mock(Session)
        session.config >> [nim: [rfdiffusion: [endpoint: customEndpoint]]]
        
        when:
        def executor = new NIMExecutor()
        executor.session = session
        executor.register()
        
        then:
        executor.nimEndpoints['rfdiffusion'] == customEndpoint
    }

    def 'should support custom endpoint configuration'() {
        given:
        def customRFDiffusion = 'http://server1:8080/biology/ipd/rfdiffusion/generate'
        def session = Mock(Session)
        session.config >> [nim: [
            rfdiffusion: [endpoint: customRFDiffusion]
        ]]
        
        when:
        def executor = new NIMExecutor()
        executor.session = session
        executor.register()
        
        then:
        executor.nimEndpoints['rfdiffusion'] == customRFDiffusion
        executor.nimEndpoints.size() == 1
    }

    def 'should initialize task handler with correct status'() {
        given:
        def session = Mock(Session)
        session.config >> [:]
        def executor = new NIMExecutor()
        executor.session = session
        executor.register()
        
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
        def session = Mock(Session)
        session.config >> [:]
        def executor = new NIMExecutor()
        executor.session = session
        executor.register()
        
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

    def 'should detect running status'() {
        given:
        def session = Mock(Session)
        session.config >> [:]
        def executor = new NIMExecutor()
        executor.session = session
        executor.register()
        
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

    def 'should handle kill signal'() {
        given:
        def session = Mock(Session)
        session.config >> [:]
        def executor = new NIMExecutor()
        executor.session = session
        executor.register()
        
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
} 