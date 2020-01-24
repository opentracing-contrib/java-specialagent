/* Copyright 2020 The OpenTracing Authors
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

package io.opentracing.contrib.specialagent.test.twilio;

import java.lang.reflect.Method;
import java.net.URI;

import com.twilio.Twilio;
import com.twilio.exception.ApiException;
import com.twilio.rest.api.v2010.account.Call;
import com.twilio.rest.api.v2010.account.CallCreator;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.Endpoint;
import com.twilio.type.PhoneNumber;

import io.opentracing.contrib.specialagent.TestUtil;

public class TwilioITest {
  private static final String ACCOUNT_SID = "ACXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX";
  private static final String AUTH_TOKEN = "your_auth_token";

  public static void main(final String[] args) throws Exception {
    Twilio.init(ACCOUNT_SID, AUTH_TOKEN);

    try {
      Message.creator(new PhoneNumber("+1 555 1234567"), new PhoneNumber("+1 555 7654321"), "Test").create();
    }
    catch (final ApiException ignore) {
    }

    Method method;
    try {
      method = Call.class.getMethod("creator", Endpoint.class, PhoneNumber.class, URI.class);
    }
    catch (final NoSuchMethodException e) {
      method = Call.class.getMethod("creator", Endpoint.class, Endpoint.class, URI.class);
    }

    final CallCreator callCreator = (CallCreator)method.invoke(null, new PhoneNumber("+1 555 1234567"), new PhoneNumber("+1 555 7654321"), new URI("http://demo.twilio.com/docs/voice.xml"));
    try {
      callCreator.create();
    }
    catch (final ApiException ignore) {
    }

    try {
      Call.fetcher("CA42ed11f93dc08b952027ffbc406d0868").fetch();
    }
    catch (final ApiException ignore) {
    }

    try {
      Call.reader().read();
    }
    catch (final ApiException ignore) {
    }

    // Twilio uses Apache HttpClient
    TestUtil.checkSpan("java-httpclient", 4);
  }
}