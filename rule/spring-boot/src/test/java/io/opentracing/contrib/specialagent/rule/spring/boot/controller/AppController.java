/* Copyright 2019 The OpenTracing Authors
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

package io.opentracing.contrib.specialagent.rule.spring.boot.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import io.opentracing.contrib.specialagent.rule.spring.boot.config.RemoteRestConfig;

@RequestMapping("/api")
@RestController
public class AppController {
  private static final Logger logger = LoggerFactory.getLogger(AppController.class);

  @Autowired
  RestTemplate restTemplate;

  @Autowired
  RemoteRestConfig config;

  @GetMapping("/new-span")
  public String helloSleuthNewSpan() {
    logger.info("New Span");
    return "success";
  }
}