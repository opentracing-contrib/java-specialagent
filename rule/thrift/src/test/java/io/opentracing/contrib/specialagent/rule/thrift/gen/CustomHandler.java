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

package io.opentracing.contrib.specialagent.rule.thrift.gen;

import java.util.concurrent.TimeUnit;


public class CustomHandler implements CustomService.Iface {

  @Override
  public String say(String text, String text2) {
    return "Say " + text + " " + text2;
  }

  @Override
  public String withDelay(int seconds) {
    try {
      TimeUnit.SECONDS.sleep(seconds);
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
    return "delay " + seconds;
  }

  @Override
  public String withoutArgs() {
    return "no args";
  }

  @Override
  public String withError() {
    throw new RuntimeException("fail");
  }

  @Override
  public String withCollision(String input) {
    return input;
  }

  @Override
  public void oneWay() {

  }

  @Override
  public void oneWayWithError() {
    throw new RuntimeException("fail");
  }

  @Override
  public UserWithAddress save(User user, Address address) {
    return new UserWithAddress(user, address);
  }
}
