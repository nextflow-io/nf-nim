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
import nextflow.processor.TaskMonitor
import nextflow.processor.TaskPollingMonitor
import spock.lang.Specification
import java.net.http.HttpClient
import spock.lang.PendingFeature

/**
 * Tests for NIM executor
 */
class NIMExecutorTest extends Specification {

    def 'should register with default NVIDIA API endpoints'() {
        given:
        def session = Mock(Session)
        session.config >> [:]
        
        when:
        def executor = new NIMExecutor()
        executor.session = session
        executor.register()
        
        then:
        executor.nimEndpoints['rfdiffusion'] == 'https://health.api.nvidia.com/v1/biology/ipd/rfdiffusion/generate'
        executor.nimEndpoints.size() == 1
        executor.httpClient instanceof HttpClient
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
        handler.task == taskRun
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
        executor.nimEndpoints.size() == 1
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

    def 'should handle empty config gracefully'() {
        given:
        def session = Mock(Session)
        session.config >> [nim: [:]]
        
        when:
        def executor = new NIMExecutor()
        executor.session = session
        executor.register()
        
        then:
        executor.nimEndpoints['rfdiffusion'] == 'https://health.api.nvidia.com/v1/biology/ipd/rfdiffusion/generate'
        executor.nimEndpoints.size() == 1
    }

    def 'should handle invalid config gracefully'() {
        given:
        def session = Mock(Session)
        session.config >> [nim: [rfdiffusion: 'invalid-config']]
        
        when:
        def executor = new NIMExecutor()
        executor.session = session
        executor.register()
        
        then:
        executor.nimEndpoints['rfdiffusion'] == 'https://health.api.nvidia.com/v1/biology/ipd/rfdiffusion/generate'
        executor.nimEndpoints.size() == 1
    }

    def 'should add multiple custom services'() {
        given:
        def session = Mock(Session)
        session.config >> [nim: [
            rfdiffusion: [endpoint: 'http://server1:8080/rfdiffusion'],
            alphafold2: [endpoint: 'http://server2:8080/alphafold2'],
            custom_service: [endpoint: 'http://server3:8080/custom']
        ]]
        
        when:
        def executor = new NIMExecutor()
        executor.session = session
        executor.register()
        
        then:
        executor.nimEndpoints['rfdiffusion'] == 'http://server1:8080/rfdiffusion'
        executor.nimEndpoints['alphafold2'] == 'http://server2:8080/alphafold2'
        executor.nimEndpoints['custom_service'] == 'http://server3:8080/custom'
        executor.nimEndpoints.size() == 3
    }
} 