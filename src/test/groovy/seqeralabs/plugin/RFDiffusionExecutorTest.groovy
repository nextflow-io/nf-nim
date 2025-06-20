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
import spock.lang.Specification

/**
 * Tests for RFDiffusion executor
 */
class RFDiffusionExecutorTest extends Specification {

    def 'should create RFDiffusion executor'() {
        given:
        def session = Mock(Session)
        session.config >> [:]
        
        when:
        def executor = new RFDiffusionExecutor()
        executor.session = session
        executor.register()
        
        then:
        executor.name == 'rfdiffusion'
        executor.nimEndpoint == 'http://localhost:8000/biology/ipd/rfdiffusion/generate'
    }

    def 'should create task handler'() {
        given:
        def session = Mock(Session)
        session.config >> [:]
        def executor = new RFDiffusionExecutor()
        executor.session = session
        executor.register()
        
        def taskRun = Mock(TaskRun)
        
        when:
        def handler = executor.createTaskHandler(taskRun)
        
        then:
        handler instanceof RFDiffusionExecutor.RFDiffusionTaskHandler
    }

    def 'should use custom endpoint from config'() {
        given:
        def customEndpoint = 'http://custom-nim-server:8080/biology/ipd/rfdiffusion/generate'
        def session = Mock(Session)
        session.config >> [nim: [rfdiffusion: [endpoint: customEndpoint]]]
        
        when:
        def executor = new RFDiffusionExecutor()
        executor.session = session
        executor.register()
        
        then:
        executor.nimEndpoint == customEndpoint
    }
} 